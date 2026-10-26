import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../platform/network_api.g.dart';

final appControllerProvider = Provider<AppController>((ref) {
  final controller = AppController();
  ref.onDispose(controller.dispose);
  unawaited(controller.initialize());
  return controller;
});

class ToolEvent {
  ToolEvent(this.state, this.data, this.elapsedMs);
  final String state;
  final Map<String, dynamic> data;
  final int elapsedMs;
}

class ToolRun {
  ToolRun(this.id, this.kind, this.parameters, this.networkName);
  final String id;
  final String kind;
  final Map<String, dynamic> parameters;
  final String networkName;
  Map<String, dynamic> networkSnapshot = {};
  final DateTime started = DateTime.now();
  String state = 'created';
  String? error;
  String? errorCode;
  int sequence = -1;
  int elapsedMs = 0;
  final List<ToolEvent> events = [];
  Map<String, dynamic> result = {};
  bool get finished =>
      const {'completed', 'cancelled', 'failed', 'interrupted'}.contains(state);
  bool get successful => state == 'completed';
}

class SignalPoint {
  SignalPoint(this.timestampMicros, this.rssi);
  final int timestampMicros;
  final int rssi;
}

class AppController extends ChangeNotifier implements NetworkFlutterApi {
  AppController({NetworkHostApi? host}) : host = host ?? NetworkHostApi();
  final NetworkHostApi host;
  PlatformSnapshot? snapshot;
  bool loading = true;
  String? lastError;
  final Map<String, ToolRun> runs = {};
  final Map<String, List<SignalPoint>> signalHistory = {};
  final Set<String> comparedBssids = {};
  final List<Map<String, dynamic>> history = [];
  final StreamController<TaskEventData> _rawEvents =
      StreamController.broadcast();
  Stream<TaskEventData> get rawEvents => _rawEvents.stream;
  Timer? _scanTimer;
  bool scanPaused = true;
  bool _disposed = false;
  File? _historyFile;
  Future<void> _saving = Future.value();
  @visibleForTesting
  Future<void> get pendingPersistence => _saving;
  int _nextId = 0;
  String? publicIp;
  DateTime? publicIpAt;
  String? _publicIpNetwork;

  NetworkData? get selectedNetwork {
    final state = snapshot;
    if (state == null) return null;
    for (final network in state.networks) {
      if (network.id == state.selectedNetworkId) return network;
    }
    return null;
  }

  ToolRun? get activeRun {
    for (final run in runs.values.toList().reversed) {
      if (!run.finished && run.kind != 'root.capture') return run;
    }
    return null;
  }

  ToolRun? get activeCapture => runs.values
      .where((run) => run.kind == 'root.capture' && !run.finished)
      .firstOrNull;
  Map<String, dynamic>? get configTransaction {
    final json = snapshot?.privilege.recoveryJson;
    if (json == null) return null;
    try {
      return jsonDecode(json) as Map<String, dynamic>;
    } on Object {
      return {'state': 'recovery_pending', 'message': '恢复状态暂时无法读取，请重新连接高级访问'};
    }
  }

  Future<void> initialize() async {
    NetworkFlutterApi.setUp(this);
    try {
      final directory = await host.getSupportDirectory();
      _historyFile = File('$directory/history.json');
      await _loadHistory();
    } on Exception {
      // Network functionality remains available if local history is unavailable.
    }
    await refresh();
  }

  Future<void> refresh() async {
    try {
      onSnapshot(await host.getSnapshot());
      lastError = null;
    } on Exception catch (error) {
      lastError = readableError(error);
    } finally {
      loading = false;
      _notify();
    }
  }

  @override
  void onSnapshot(PlatformSnapshot value) {
    if (_disposed) return;
    final oldNetwork = selectedNetwork;
    snapshot = value;
    final next = selectedNetwork;
    if (oldNetwork?.id != next?.id ||
        oldNetwork?.generation != next?.generation) {
      publicIp = null;
      publicIpAt = null;
      _publicIpNetwork = null;
    }
    for (final ap in value.accessPoints) {
      final points = signalHistory.putIfAbsent(ap.bssid, () => []);
      if (ap.timestampMicros > 0 &&
          (points.isEmpty ||
              ap.timestampMicros > points.last.timestampMicros)) {
        points.add(SignalPoint(ap.timestampMicros, ap.rssi));
      }
      if (points.isNotEmpty) {
        final cutoff =
            points.last.timestampMicros -
            const Duration(minutes: 10).inMicroseconds;
        points.removeWhere((point) => point.timestampMicros < cutoff);
      }
    }
    if (comparedBssids.isEmpty) {
      comparedBssids.addAll(value.accessPoints.take(3).map((ap) => ap.bssid));
    }
    loading = false;
    _notify();
  }

  Future<void> selectNetwork(NetworkData network) async {
    await host.selectNetwork(network.id);
    await refresh();
  }

  Future<void> requestPermissions() async {
    await host.requestNetworkPermissions();
    await refresh();
  }

  Future<void> scan({bool periodic = true}) async {
    scanPaused = !periodic;
    _scanTimer?.cancel();
    await host.requestWifiScan();
    if (periodic) {
      _scanTimer = Timer.periodic(const Duration(seconds: 31), (_) async {
        if (activeRun != null || scanPaused) return;
        try {
          await host.requestWifiScan();
        } on Exception catch (error) {
          lastError = readableError(error);
          pauseScan();
        }
      });
    }
    _notify();
  }

  void pauseScan() {
    scanPaused = true;
    _scanTimer?.cancel();
    _notify();
  }

  void compare(String bssid, bool enabled) {
    if (enabled && comparedBssids.length < 8) comparedBssids.add(bssid);
    if (!enabled) comparedBssids.remove(bssid);
    _notify();
  }

  Future<ToolRun> start(
    String kind,
    Map<String, dynamic> parameters, {
    InterfaceData? interface,
    void Function(ToolRun)? onCreated,
  }) async {
    if (activeRun != null && kind != 'root.capture') {
      throw PlatformException(code: 'BUSY', message: '请先停止正在运行的任务');
    }
    final network = selectedNetwork;
    final id = '${DateTime.now().microsecondsSinceEpoch}-${_nextId++}';
    final run = ToolRun(
      id,
      kind,
      Map.of(parameters),
      interface?.name ?? network?.displayName ?? '未选择网络',
    );
    run.networkSnapshot = {
      if (network != null) ...{
        'id': network.id,
        'generation': network.generation,
        'interface': network.interfaceName,
        'transport': network.transport,
        'ipv4': network.addresses,
        'prefixes': network.prefixes,
        'gateway': network.gateway,
        'dns': network.dnsServers,
        'mtu': network.mtu,
      },
      if (interface != null) ...{
        'interfaceId': interface.id,
        'interface': interface.name,
        'interfaceGeneration': interface.generation,
        'ipv4': interface.addresses,
      },
      'capturing': activeCapture != null,
    };
    runs[id] = run;
    onCreated?.call(run);
    _notify();
    _record(run);
    await pendingPersistence;
    try {
      await host.startTask(
        TaskRequestData(
          requestId: id,
          kind: kind,
          parametersJson: jsonEncode(parameters),
          networkId: network?.id,
          networkGeneration: network?.generation,
          interfaceId: interface?.id,
          privilegeGeneration: snapshot?.privilege.generation,
        ),
      );
    } on Exception catch (error) {
      run.state = 'failed';
      run.error = readableError(error);
      run.errorCode = error is PlatformException ? error.code : 'ENGINE_FAILED';
      _record(run);
      _notify();
      rethrow;
    }
    return run;
  }

  Future<void> stop(ToolRun run) => host.stopTask(run.id);

  Future<void> requestAdvancedAccess() async {
    await host.requestAdvancedAccess();
    await refresh();
  }

  @override
  void onTaskEvent(TaskEventData event) {
    if (_disposed) return;
    final run = runs[event.taskId];
    if (run == null || run.finished || event.sequence <= run.sequence) return;
    run.sequence = event.sequence;
    run.elapsedMs = event.elapsedMs;
    Map<String, dynamic> data;
    try {
      data = jsonDecode(event.dataJson) as Map<String, dynamic>;
    } on Object {
      data = {'message': '无法解析任务结果'};
    }
    if (event.state != 'terminal') {
      run.events.add(ToolEvent(event.state, data, event.elapsedMs));
      if (run.events.length > 1000) run.events.removeAt(0);
    }
    if (event.state == 'result') run.result = data;
    if (const {
      'created',
      'running',
      'listening',
      'connected',
      'stopping',
      'completed',
      'cancelled',
      'failed',
      'interrupted',
    }.contains(event.state)) {
      run.state = event.state;
    }
    if (event.errorCode != null) run.errorCode = event.errorCode;
    if (event.message != null) run.error = event.message;
    if (run.kind == 'publicIp' &&
        event.state == 'result' &&
        data['ip'] is String) {
      publicIp = data['ip'] as String;
      publicIpAt = DateTime.now();
      _publicIpNetwork = selectedNetwork?.id;
    }
    if (run.finished) {
      if (data.isNotEmpty && run.result.isEmpty) run.result = data;
      _record(run);
    }
    _rawEvents.add(event);
    _notify();
  }

  bool get publicIpFresh =>
      publicIpAt != null &&
      selectedNetwork?.id == _publicIpNetwork &&
      DateTime.now().difference(publicIpAt!) < const Duration(seconds: 60);

  Future<void> clearHistory() async {
    history.clear();
    await _saveHistory();
    _notify();
  }

  void _record(ToolRun run) {
    history.removeWhere((record) => record['id'] == run.id);
    history.insert(0, {
      'id': run.id,
      'kind': run.kind,
      'state': run.state,
      'started': run.started.toIso8601String(),
      'durationMs': run.elapsedMs,
      'network': run.networkName,
      'networkSnapshot': run.networkSnapshot,
      'parameters': run.parameters,
      'result': run.kind == 'telnet' ? <String, dynamic>{} : run.result,
      'error': run.error,
      'errorCode': run.errorCode,
      if (run.kind != 'telnet')
        'evidence': run.events
            .where(
              (event) => const {
                'target',
                'sample',
                'reply',
                'hop',
                'arp',
                'dhcp',
                'iperf',
              }.contains(event.state),
            )
            .take(300)
            .map(
              (event) => {
                'state': event.state,
                'elapsedMs': event.elapsedMs,
                'data': event.data,
              },
            )
            .toList(),
    });
    if (history.length > 100) history.removeRange(100, history.length);
    while (history.isNotEmpty &&
        utf8.encode(jsonEncode(history)).length > 5 * 1000 * 1000) {
      history.removeLast();
    }
    unawaited(_saveHistory());
    while (runs.length > 110) {
      final old = runs.entries
          .where((entry) => entry.value.finished)
          .firstOrNull;
      if (old == null) break;
      runs.remove(old.key);
    }
  }

  Future<void> _loadHistory() async {
    final file = _historyFile;
    if (file == null || !await file.exists()) return;
    try {
      if (await file.length() > 5 * 1000 * 1000) {
        throw const FormatException('History exceeds the configured limit');
      }
      final data =
          jsonDecode(await file.readAsString()) as Map<String, dynamic>;
      if (data['schemaVersion'] != 1) return;
      history.addAll(
        (data['records'] as List).whereType<Map<String, dynamic>>().take(100),
      );
      var interrupted = false;
      for (final record in history) {
        if (!const {
          'completed',
          'cancelled',
          'failed',
          'interrupted',
        }.contains(record['state'])) {
          record['state'] = 'interrupted';
          record['errorCode'] = 'APP_EXITED';
          record['error'] = '上次运行因应用退出而中断，未自动重试';
          interrupted = true;
        }
      }
      if (interrupted) await _saveHistory();
    } on Object {
      await file.rename(
        '${file.path}.damaged-${DateTime.now().millisecondsSinceEpoch}',
      );
    }
  }

  Future<void> _saveHistory() {
    final file = _historyFile;
    if (file == null) return Future.value();
    final json = jsonEncode({'schemaVersion': 1, 'records': history});
    _saving = _saving
        .then((_) async {
          await file.parent.create(recursive: true);
          final temporary = File('${file.path}.new');
          await temporary.writeAsString(json, flush: true);
          await temporary.rename(file.path);
        })
        .catchError((Object _) {
          /* History I/O must not turn a successful probe into failure. */
        });
    return _saving;
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _scanTimer?.cancel();
    NetworkFlutterApi.setUp(null);
    unawaited(_rawEvents.close());
    super.dispose();
  }
}

String readableError(Object error) {
  if (error is PlatformException) return error.message ?? error.code;
  return error.toString().replaceFirst('Exception: ', '');
}

String toolName(String kind) =>
    const {
      'ping': 'Ping',
      'tcping': 'TCPing',
      'telnet': 'Telnet',
      'trace': 'Traceroute',
      'dns': 'DNS / dig',
      'systemResolve': '系统解析',
      'iperf': '局域网测速',
      'publicIp': '公网 IPv4',
      'webspeed': '公网测速',
      'clearAppDns': '应用 DNS 缓存',
      'root.capture': '按网卡抓包',
      'root.arp': 'ARP 检查',
      'root.dhcp': 'DHCP 观察',
      'root.systemDns': '系统 DNS 缓存',
      'root.ethernet': '有线临时配置',
    }[kind] ??
    kind;

String stateName(String state) =>
    const {
      'created': '准备中',
      'running': '运行中',
      'listening': '等待连接',
      'connected': '已连接',
      'stopping': '停止中',
      'completed': '已完成',
      'cancelled': '已停止',
      'failed': '失败',
      'interrupted': '已中断',
    }[state] ??
    state;
