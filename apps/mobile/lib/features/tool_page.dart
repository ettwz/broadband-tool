import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:xterm/xterm.dart';

import '../app/controller.dart';
import '../design/components.dart';
import '../platform/network_api.g.dart';

class ToolPage extends StatefulWidget {
  const ToolPage({
    super.key,
    required this.controller,
    required this.kind,
    this.initial = const {},
    this.existing,
  });
  final AppController controller;
  final String kind;
  final Map<String, dynamic> initial;
  final ToolRun? existing;
  @override
  State<ToolPage> createState() => _ToolPageState();
}

class _ToolPageState extends State<ToolPage> {
  final form = GlobalKey<FormState>();
  final fields = <String, TextEditingController>{};
  ToolRun? run;
  String role = 'client';
  String protocol = 'tcp';
  String dnsType = 'A';
  String dnsTransport = 'udp';
  bool reverse = false;
  bool bypassCache = true;
  bool allowPop = false;
  final terminal = Terminal(maxLines: 2000);
  StreamSubscription<TaskEventData>? terminalEvents;
  ByteConversionSink? decoder;
  bool localEcho = false;
  int columns = 80, rows = 24;

  bool get busy => run != null && !run!.finished;
  bool get dns => widget.kind == 'dns';
  bool get iperf => widget.kind == 'iperf';
  bool get telnet => widget.kind == 'telnet';
  bool get trace => widget.kind == 'trace';

  @override
  void initState() {
    super.initState();
    run = widget.existing;
    final values = widget.existing?.parameters ?? widget.initial;
    final defaults = <String, String>{
      'host': widget.kind == 'ping'
          ? widget.controller.selectedNetwork?.gateway ?? ''
          : '',
      'port': iperf
          ? '5201'
          : telnet
          ? '23'
          : dns
          ? '53'
          : '443',
      'count': '10',
      'payloadBytes': '56',
      'interval': '1',
      'timeout': telnet
          ? '5'
          : dns
          ? '3'
          : '2',
      'duration': '10',
      'streams': '1',
      'rateMbps': '10',
      'name': '',
      'server': '',
    };
    for (final entry in defaults.entries) {
      fields[entry.key] = TextEditingController(
        text: values[entry.key]?.toString() ?? entry.value,
      );
    }
    role = values['role'] as String? ?? role;
    protocol = values['protocol'] as String? ?? protocol;
    dnsType = values['type'] as String? ?? dnsType;
    dnsTransport = values['transport'] as String? ?? dnsTransport;
    reverse = values['reverse'] as bool? ?? false;
    bypassCache = values['bypassCache'] as bool? ?? true;
    if (values['intervalMs'] is num) {
      fields['interval']!.text = ((values['intervalMs'] as num) / 1000)
          .toString();
    }
    if (values['timeoutMs'] is num) {
      fields['timeout']!.text = ((values['timeoutMs'] as num) / 1000)
          .toString();
    }
    decoder = const Utf8Decoder(allowMalformed: true).startChunkedConversion(
      StringConversionSink.fromStringSink(_TerminalSink(terminal)),
    );
    terminal.onOutput = sendInput;
    terminal.onResize = (width, height, _, _) {
      columns = width;
      rows = height;
      final current = run;
      if (current?.state == 'connected') {
        unawaited(
          widget.controller.host
              .resizeTerminal(current!.id, columns, rows)
              .catchError((Object _) {}),
        );
      }
    };
    terminalEvents = widget.controller.rawEvents.listen((event) {
      if (!telnet || event.taskId != run?.id) return;
      final data = jsonDecode(event.dataJson) as Map<String, dynamic>;
      if (event.state == 'terminal' && data['bytes'] is String) {
        decoder?.add(base64Decode(data['bytes'] as String));
      }
      if (event.state == 'terminalState') localEcho = data['localEcho'] == true;
      if (event.state == 'connected') {
        unawaited(
          widget.controller.host
              .resizeTerminal(event.taskId, columns, rows)
              .catchError((Object _) {}),
        );
      }
    });
  }

  void sendInput(String value) {
    final current = run;
    if (current?.state != 'connected') return;
    if (localEcho) terminal.write(value.replaceAll('\r', '\r\n'));
    unawaited(
      widget.controller.host
          .sendTerminalInput(
            current!.id,
            Uint8List.fromList(utf8.encode(value)),
          )
          .catchError((Object error) {
            if (mounted) showError(context, error);
          }),
    );
  }

  @override
  void dispose() {
    terminalEvents?.cancel();
    decoder?.close();
    for (final field in fields.values) {
      field.dispose();
    }
    super.dispose();
  }

  Future<void> start() async {
    if (form.currentState?.validate() != true) return;
    FocusScope.of(context).unfocus();
    final values = <String, dynamic>{};
    if (dns) {
      values.addAll({
        'name': fields['name']!.text.trim(),
        'server': fields['server']!.text.trim(),
        'type': dnsType,
        'transport': dnsTransport,
        'bypassCache': bypassCache,
        'port': int.parse(fields['port']!.text),
        'timeoutMs': (double.parse(fields['timeout']!.text) * 1000).round(),
        'retries': 1,
      });
    } else if (iperf) {
      values.addAll({
        'role': role,
        'host': fields['host']!.text.trim(),
        'port': int.parse(fields['port']!.text),
        'protocol': protocol,
        'duration': int.parse(fields['duration']!.text),
        'streams': int.parse(fields['streams']!.text),
        'rateMbps': int.parse(fields['rateMbps']!.text),
        'reverse': reverse,
      });
    } else {
      values['host'] = fields['host']!.text.trim();
      if (!trace) {
        values['timeoutMs'] = (double.parse(fields['timeout']!.text) * 1000)
            .round();
      }
      if (telnet || widget.kind == 'tcping') {
        values['port'] = int.parse(fields['port']!.text);
      }
      if (!trace && !telnet) {
        values.addAll({
          'count': int.parse(fields['count']!.text),
          'intervalMs': (double.parse(fields['interval']!.text) * 1000).round(),
          if (widget.kind == 'ping')
            'payloadBytes': int.parse(fields['payloadBytes']!.text),
        });
      }
    }
    try {
      final next = await widget.controller.start(
        widget.kind,
        values,
        onCreated: (next) {
          if (mounted) {
            setState(() {
              run = next;
              allowPop = false;
            });
          }
        },
      );
      if (mounted) {
        setState(() {
          run = next;
          allowPop = false;
        });
      }
    } on Exception catch (error) {
      if (mounted) showError(context, error);
    }
  }

  Widget textField(
    String key,
    String label, {
    String? hint,
    bool optional = false,
    bool numeric = false,
    num? min,
    num? max,
    bool decimal = false,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 13),
    child: TextFormField(
      controller: fields[key],
      enabled: !busy,
      autocorrect: false,
      keyboardType: numeric
          ? TextInputType.numberWithOptions(decimal: decimal)
          : TextInputType.text,
      textInputAction: TextInputAction.next,
      decoration: InputDecoration(labelText: label, hintText: hint),
      validator: (text) {
        final value = text?.trim() ?? '';
        if (value.isEmpty) return optional ? null : '请填写$label';
        if (numeric) {
          final parsed = decimal ? double.tryParse(value) : int.tryParse(value);
          if (parsed == null ||
              !parsed.isFinite ||
              (!decimal && parsed % 1 != 0) ||
              (min != null && parsed < min) ||
              (max != null && parsed > max)) {
            return '$label范围为 $min–$max';
          }
        }
        return null;
      },
    ),
  );

  Future<void> handlePop(bool didPop) async {
    if (didPop || !busy) return;
    final stop = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('停止当前任务并返回？'),
        content: const Text('当前结果将保留，活动连接会关闭。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('继续查看'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('停止并返回'),
          ),
        ],
      ),
    );
    if (stop == true && mounted) {
      await widget.controller.stop(run!);
      if (!mounted) return;
      setState(() => allowPop = true);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) Navigator.pop(context);
      });
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      return PopScope(
        canPop: allowPop || !busy,
        onPopInvokedWithResult: (didPop, _) => handlePop(didPop),
        child: Scaffold(
          appBar: AppBar(title: Text(toolName(widget.kind))),
          body: telnet && run != null
              ? terminalBody()
              : PageBody(
                  children: [
                    NetworkCard(
                      controller: widget.controller,
                      onSelect: () => chooseNetwork(context, widget.controller),
                    ),
                    Form(
                      key: form,
                      child: Panel(
                        title: iperf
                            ? 'iperf3 参数'
                            : dns
                            ? '查询参数'
                            : '检查目标',
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (iperf) ...[
                              SegmentedButton<String>(
                                showSelectedIcon: false,
                                segments: const [
                                  ButtonSegment(
                                    value: 'client',
                                    label: Text('客户端'),
                                  ),
                                  ButtonSegment(
                                    value: 'server',
                                    label: Text('服务端'),
                                  ),
                                ],
                                selected: {role},
                                onSelectionChanged: busy
                                    ? null
                                    : (value) =>
                                          setState(() => role = value.first),
                              ),
                              const SizedBox(height: 16),
                            ],
                            if (dns) ...[
                              textField(
                                'name',
                                '域名或 PTR 地址',
                                hint: 'example.com',
                              ),
                              DropdownButtonFormField<String>(
                                initialValue: dnsType,
                                decoration: const InputDecoration(
                                  labelText: '记录类型',
                                ),
                                items:
                                    [
                                          'A',
                                          'CNAME',
                                          'MX',
                                          'TXT',
                                          'NS',
                                          'SOA',
                                          'PTR',
                                        ]
                                        .map(
                                          (value) => DropdownMenuItem(
                                            value: value,
                                            child: Text(value),
                                          ),
                                        )
                                        .toList(),
                                onChanged: busy
                                    ? null
                                    : (value) =>
                                          setState(() => dnsType = value!),
                              ),
                              const SizedBox(height: 13),
                              textField(
                                'server',
                                'DNS 服务器',
                                hint: '留空使用当前网络的 IPv4 DNS',
                                optional: true,
                              ),
                              Row(
                                children: [
                                  Expanded(
                                    child: textField(
                                      'port',
                                      '端口',
                                      numeric: true,
                                      min: 1,
                                      max: 65535,
                                    ),
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: textField(
                                      'timeout',
                                      '超时（秒）',
                                      numeric: true,
                                      decimal: true,
                                      min: .2,
                                      max: 10,
                                    ),
                                  ),
                                ],
                              ),
                              SegmentedButton<String>(
                                showSelectedIcon: false,
                                segments: const [
                                  ButtonSegment(
                                    value: 'udp',
                                    label: Text('UDP'),
                                  ),
                                  ButtonSegment(
                                    value: 'tcp',
                                    label: Text('TCP'),
                                  ),
                                ],
                                selected: {dnsTransport},
                                onSelectionChanged: busy
                                    ? null
                                    : (value) => setState(
                                        () => dnsTransport = value.first,
                                      ),
                              ),
                              SwitchListTile.adaptive(
                                contentPadding: EdgeInsets.zero,
                                title: const Text('绕过本应用缓存'),
                                subtitle: const Text(
                                  '上游 DNS 仍可能使用缓存',
                                  style: TextStyle(fontSize: 12),
                                ),
                                value: bypassCache,
                                onChanged: busy
                                    ? null
                                    : (value) =>
                                          setState(() => bypassCache = value),
                              ),
                            ] else ...[
                              if (!iperf || role == 'client')
                                textField(
                                  'host',
                                  '目标 IPv4 或域名',
                                  hint: trace
                                      ? '例如 example.com 或 192.168.1.1'
                                      : '输入目标地址',
                                ),
                              if (iperf || telnet || widget.kind == 'tcping')
                                textField(
                                  'port',
                                  '端口',
                                  numeric: true,
                                  min: iperf && role == 'server' ? 1024 : 1,
                                  max: 65535,
                                ),
                              if (iperf && role == 'server')
                                const Notice(
                                  '仅监听所选 IPv4，完成一场测试后停止。等待上限 5 分钟；切到后台会停止监听。',
                                ),
                              if (iperf && role == 'client') ...[
                                SegmentedButton<String>(
                                  showSelectedIcon: false,
                                  segments: const [
                                    ButtonSegment(
                                      value: 'tcp',
                                      label: Text('TCP'),
                                    ),
                                    ButtonSegment(
                                      value: 'udp',
                                      label: Text('UDP'),
                                    ),
                                  ],
                                  selected: {protocol},
                                  onSelectionChanged: busy
                                      ? null
                                      : (value) => setState(() {
                                          protocol = value.first;
                                          if (protocol == 'udp') {
                                            fields['streams']!.text = '1';
                                          }
                                        }),
                                ),
                                const SizedBox(height: 14),
                                Row(
                                  children: [
                                    Expanded(
                                      child: textField(
                                        'duration',
                                        '时长（秒）',
                                        numeric: true,
                                        min: 5,
                                        max: 120,
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: textField(
                                        'streams',
                                        '并发流',
                                        numeric: true,
                                        min: 1,
                                        max: protocol == 'udp' ? 1 : 8,
                                      ),
                                    ),
                                  ],
                                ),
                                if (protocol == 'udp')
                                  textField(
                                    'rateMbps',
                                    'UDP 目标速率（Mbps）',
                                    numeric: true,
                                    min: 1,
                                    max: 1000,
                                  ),
                                SwitchListTile.adaptive(
                                  contentPadding: EdgeInsets.zero,
                                  title: const Text('反向接收'),
                                  subtitle: Text(
                                    reverse ? '本机接收对端数据' : '本机向对端发送数据',
                                  ),
                                  value: reverse,
                                  onChanged: busy
                                      ? null
                                      : (value) =>
                                            setState(() => reverse = value),
                                ),
                              ],
                              if (!iperf && !trace) ...[
                                if (!telnet)
                                  Row(
                                    children: [
                                      Expanded(
                                        child: textField(
                                          'count',
                                          '次数',
                                          numeric: true,
                                          min: 1,
                                          max: 1000,
                                        ),
                                      ),
                                      const SizedBox(width: 12),
                                      Expanded(
                                        child: textField(
                                          'interval',
                                          '间隔（秒）',
                                          numeric: true,
                                          decimal: true,
                                          min: .2,
                                          max: 10,
                                        ),
                                      ),
                                    ],
                                  ),
                                textField(
                                  'timeout',
                                  '超时（秒）',
                                  numeric: true,
                                  decimal: true,
                                  min: .2,
                                  max: 10,
                                ),
                                if (widget.kind == 'ping')
                                  textField(
                                    'payloadBytes',
                                    '数据长度（字节）',
                                    numeric: true,
                                    min: 8,
                                    max: 1400,
                                  ),
                              ],
                            ],
                            const SizedBox(height: 8),
                            FilledButton(
                              onPressed: busy
                                  ? () => widget.controller.stop(run!)
                                  : start,
                              child: Text(
                                busy
                                    ? (iperf && role == 'server'
                                          ? '停止服务端'
                                          : '停止')
                                    : telnet
                                    ? '连接'
                                    : dns
                                    ? '查询'
                                    : iperf && role == 'server'
                                    ? '启动服务端'
                                    : trace
                                    ? '开始追踪'
                                    : '开始测试',
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    if (trace)
                      const Notice(
                        '域名通过所选网络解析为 IPv4 后追踪。最多 30 跳，每跳 3 次探测；星号表示本次没有收到回复，不代表后续链路不可达。',
                      ),
                    if (telnet)
                      const Notice(
                        'Telnet 使用明文连接。输入和会话正文不会保存到历史。',
                        warning: true,
                      ),
                    if (run != null) RunReport(run: run!),
                    if (dns)
                      Panel(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            OutlinedButton(
                              onPressed: busy
                                  ? null
                                  : () async {
                                      try {
                                        await widget.controller.start(
                                          'clearAppDns',
                                          {},
                                        );
                                        if (context.mounted) {
                                          ScaffoldMessenger.of(context)
                                              .showSnackBar(
                                                const SnackBar(
                                                  content: Text('已提交本应用缓存清理'),
                                                ),
                                              );
                                        }
                                      } on Exception catch (error) {
                                        if (context.mounted) {
                                          showError(context, error);
                                        }
                                      }
                                    },
                              child: const Text('清除本应用 DNS 缓存'),
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              '仅影响本应用。系统 DNS 缓存清理位于高级工具。',
                              style: TextStyle(color: muted, fontSize: 12),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
        ),
      );
    },
  );

  Widget terminalBody() => Column(
    children: [
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Row(
          children: [
            Expanded(
              child: Text(
                '${run!.parameters['host']}:${run!.parameters['port']} · ${stateName(run!.state)}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            TextButton(
              onPressed: busy
                  ? () => widget.controller.stop(run!)
                  : () => setState(() => run = null),
              child: Text(busy ? '断开' : '重新连接'),
            ),
          ],
        ),
      ),
      if (run!.error != null)
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Notice(run!.error!, warning: true),
        ),
      Expanded(child: TerminalView(terminal, autofocus: true)),
      SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            for (final key in [
              ('Esc', '\x1b'),
              ('Tab', '\t'),
              ('Ctrl-C', '\x03'),
              ('↑', '\x1b[A'),
              ('↓', '\x1b[B'),
              ('Enter', '\r'),
            ])
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: TextButton(
                  onPressed: busy ? () => sendInput(key.$2) : null,
                  child: Text(key.$1),
                ),
              ),
          ],
        ),
      ),
    ],
  );
}

class _TerminalSink implements StringSink {
  _TerminalSink(this.terminal);
  final Terminal terminal;
  @override
  void write(Object? object) => terminal.write('$object');
  @override
  void writeAll(Iterable<dynamic> objects, [String separator = '']) =>
      terminal.write(objects.join(separator));
  @override
  void writeCharCode(int charCode) =>
      terminal.write(String.fromCharCode(charCode));
  @override
  void writeln([Object? object = '']) => terminal.write('$object\r\n');
}

class RunReport extends StatelessWidget {
  const RunReport({super.key, required this.run});
  final ToolRun run;
  @override
  Widget build(BuildContext context) {
    final samples = run.events
        .where((event) => event.state == 'sample')
        .toList();
    final hops = run.events.where((event) => event.state == 'hop').toList();
    final result = run.result;
    final target =
        result['target'] ??
        run.events
            .where((event) => event.state == 'target')
            .lastOrNull
            ?.data['target'];
    return Panel(
      title: '结果 · ${stateName(run.state)}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            '${run.networkName} · ${(run.elapsedMs / 1000).toStringAsFixed(1)} 秒',
            style: const TextStyle(color: muted, fontSize: 12),
          ),
          const SizedBox(height: 12),
          if (!run.finished) ...[
            const LinearProgressIndicator(minHeight: 3),
            const SizedBox(height: 14),
          ],
          if (run.error != null)
            Notice(run.error!, warning: run.state == 'failed'),
          if (run.kind == 'iperf') IperfReport(run: run),
          if (run.kind == 'dns' && result.isNotEmpty) DnsReport(data: result),
          if (run.kind == 'trace') ...[
            ValueRow(
              '目标',
              '${run.parameters['host'] ?? result['host'] ?? '未知'}',
              copy: true,
            ),
            if (target != null && target != run.parameters['host'])
              ValueRow('追踪 IPv4', '$target', copy: true),
            if (target == null &&
                !run.finished &&
                run.events.any((event) => event.state == 'resolving'))
              const Text('正在解析域名…', style: TextStyle(color: muted)),
            if (result.containsKey('reached'))
              StatusBadge(
                result['reached'] == true ? '已到达目标' : '未确认到达目标',
                color: result['reached'] == true ? Colors.green : Colors.orange,
              ),
            const SizedBox(height: 12),
            for (final hop
                in hops.map((event) => event.data['hop'] as int).toSet())
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SizedBox(
                      width: 28,
                      child: Text('$hop', style: const TextStyle(color: muted)),
                    ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            hops
                                .where((event) => event.data['hop'] == hop)
                                .map(
                                  (event) => event.data['address'] as String?,
                                )
                                .whereType<String>()
                                .toSet()
                                .join(' / ')
                                .takeOr('*'),
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            hops
                                .where((event) => event.data['hop'] == hop)
                                .map(
                                  (event) => event.data['rttMs'] == null
                                      ? '*'
                                      : '${number(event.data['rttMs'] as num?, digits: 2)} ms',
                                )
                                .join('   '),
                            style: const TextStyle(color: muted, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
          ],
          if (run.kind == 'ping' || run.kind == 'tcping') ...[
            if (result.isNotEmpty) ...[
              Row(
                children: [
                  metric(
                    '成功',
                    '${result['received'] ?? result['connected'] ?? 0} / ${result['sent'] ?? 0}',
                  ),
                  metric(
                    '平均延迟',
                    '${number(result['avgMs'] as num?, digits: 2)} ms',
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (run.kind == 'ping')
                ValueRow('丢包率', '${number(result['lossPercent'] as num?)} %'),
              if (run.kind == 'tcping')
                ValueRow(
                  '拒绝 / 超时',
                  '${result['refused'] ?? 0} / ${result['timeouts'] ?? 0}',
                ),
              ValueRow(
                '最小 / 最大',
                '${number(result['minMs'] as num?, digits: 2)} / ${number(result['maxMs'] as num?, digits: 2)} ms',
              ),
              const Divider(),
            ],
            for (final sample in samples.reversed.take(60))
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Row(
                  children: [
                    SizedBox(
                      width: 35,
                      child: Text(
                        '#${sample.data['sequence']}',
                        style: const TextStyle(color: muted, fontSize: 12),
                      ),
                    ),
                    Expanded(
                      child: Text(
                        sample.data['address'] as String? ??
                            run.parameters['host'] as String? ??
                            '—',
                        style: const TextStyle(fontSize: 13),
                      ),
                    ),
                    Text(
                      switch (sample.data['status']) {
                        'reply' || 'connected' =>
                          '${number(sample.data['rttMs'] as num?, digits: 2)} ms',
                        'refused' => '连接被拒绝',
                        'timeout' => '未收到回复',
                        _ => '不可达回复',
                      },
                      style: TextStyle(
                        color: sample.data['rttMs'] == null
                            ? Colors.orange.shade800
                            : accent,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),
              ),
            if (samples.isEmpty && !run.finished)
              const Text('正在等待响应…', style: TextStyle(color: muted)),
          ],
          if (result['message'] != null)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Text('${result['message']}'),
            ),
        ],
      ),
    );
  }
}

class DnsReport extends StatelessWidget {
  const DnsReport({super.key, required this.data});
  final Map<String, dynamic> data;
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Row(
        children: [
          StatusBadge(
            '${data['rcode']}',
            color: data['rcode'] == 'NOERROR' ? Colors.green : Colors.orange,
          ),
          const Spacer(),
          Text('${data['elapsedMs']} ms', style: const TextStyle(color: muted)),
        ],
      ),
      ValueRow('服务器', '${data['server']}:${data['port']}'),
      ValueRow(
        '传输',
        '${data['transport']}${data['tcpFallback'] == true ? '（UDP 截断后切换）' : ''}',
      ),
      ValueRow('结果来源', data['fromCache'] == true ? '本应用缓存' : '本次 DNS 查询'),
      for (final section in [
        'question',
        'answer',
        'authority',
        'additional',
      ]) ...[
        const Divider(),
        Text(
          section.toUpperCase(),
          style: const TextStyle(
            fontSize: 12,
            color: muted,
            fontWeight: FontWeight.w600,
          ),
        ),
        if ((data[section] as List? ?? []).isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Text('无记录', style: TextStyle(color: muted)),
          ),
        for (final raw in (data[section] as List? ?? []).take(128))
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        '${(raw as Map)['name']}',
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                    ),
                    StatusBadge('${raw['type']}'),
                  ],
                ),
                if ('${raw['value']}'.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 5),
                    child: SelectableText('${raw['value']}'),
                  ),
                if (section != 'question')
                  Text(
                    'TTL ${raw['ttl']} 秒',
                    style: const TextStyle(color: muted, fontSize: 12),
                  ),
              ],
            ),
          ),
      ],
    ],
  );
}

class IperfReport extends StatelessWidget {
  const IperfReport({super.key, required this.run});
  final ToolRun run;
  @override
  Widget build(BuildContext context) {
    final intervals = run.events
        .where(
          (event) =>
              event.state == 'iperf' && event.data['event'] == 'interval',
        )
        .toList();
    final latest = intervals.lastOrNull?.data['data'] as Map?;
    final sum = latest?['sum'] as Map?;
    final end = run.result['iperf'] as Map?;
    final sent = end?['sum_sent'] as Map?;
    final received = end?['sum_received'] as Map?;
    final udp = end?['sum'] as Map?;
    final rate =
        (sum?['bits_per_second'] ??
                received?['bits_per_second'] ??
                sent?['bits_per_second'] ??
                udp?['bits_per_second'])
            as num?;
    final totalBytes = intervals.fold<num>(
      0,
      (total, event) =>
          total +
          (((event.data['data'] as Map?)?['sum'] as Map?)?['bytes'] as num? ??
              0),
    );
    final listening = run.events
        .where((event) => event.state == 'listening')
        .lastOrNull
        ?.data;
    final start =
        run.events
                .where(
                  (event) =>
                      event.state == 'iperf' && event.data['event'] == 'start',
                )
                .lastOrNull
                ?.data['data']
            as Map?;
    final connected = (start?['connected'] as List?)
        ?.whereType<Map>()
        .firstOrNull;
    final sender = sum?['sender'] as bool?;
    final spots = intervals
        .map((event) => (event.data['data'] as Map?)?['sum'])
        .whereType<Map>()
        .where(
          (value) => value['end'] is num && value['bits_per_second'] is num,
        )
        .map(
          (value) => FlSpot(
            (value['end'] as num).toDouble(),
            (value['bits_per_second'] as num).toDouble() / 1000000,
          ),
        )
        .toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (listening != null)
          ValueRow('监听地址', '${listening['address']}:${listening['port']}'),
        if (connected != null)
          ValueRow(
            '对端',
            '${connected['remote_host']}:${connected['remote_port']}',
          ),
        if (sender != null) ValueRow('本机方向', sender ? '发送' : '接收'),
        if (run.state == 'listening') ...[
          const Center(
            child: Icon(
              Icons.settings_ethernet_rounded,
              color: accent,
              size: 42,
            ),
          ),
          const SizedBox(height: 12),
          const Center(
            child: Text(
              '等待客户端连接',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
            ),
          ),
        ] else ...[
          const Text('当前吞吐', style: TextStyle(color: muted, fontSize: 13)),
          const SizedBox(height: 6),
          Text(
            '${rate == null ? '—' : number(rate / 1000000)} Mbps',
            style: const TextStyle(fontSize: 35, fontWeight: FontWeight.w700),
          ),
          ValueRow('已传输', bytesLabel(totalBytes)),
        ],
        if (spots.isNotEmpty) ...[
          const SizedBox(height: 12),
          SizedBox(
            height: 155,
            child: LineChart(
              LineChartData(
                minY: 0,
                minX: 0,
                borderData: FlBorderData(show: false),
                gridData: const FlGridData(drawVerticalLine: false),
                titlesData: const FlTitlesData(
                  topTitles: AxisTitles(
                    sideTitles: SideTitles(showTitles: false),
                  ),
                  rightTitles: AxisTitles(
                    sideTitles: SideTitles(showTitles: false),
                  ),
                  leftTitles: AxisTitles(
                    sideTitles: SideTitles(showTitles: true, reservedSize: 44),
                  ),
                  bottomTitles: AxisTitles(
                    axisNameWidget: Text(
                      '经过时间（秒）',
                      style: TextStyle(fontSize: 11),
                    ),
                    sideTitles: SideTitles(showTitles: true, reservedSize: 24),
                  ),
                ),
                lineBarsData: [
                  LineChartBarData(
                    spots: spots,
                    color: accent,
                    barWidth: 2,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      color: accent.withValues(alpha: .08),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const Text('吞吐 Mbps', style: TextStyle(fontSize: 11, color: muted)),
        ],
        if (sent != null)
          ValueRow(
            '发送侧吞吐',
            '${number((sent['bits_per_second'] as num?) == null ? null : (sent['bits_per_second'] as num) / 1000000)} Mbps',
          ),
        if (received != null)
          ValueRow(
            '接收侧吞吐',
            '${number((received['bits_per_second'] as num?) == null ? null : (received['bits_per_second'] as num) / 1000000)} Mbps',
          ),
        if (sent?['retransmits'] != null)
          ValueRow('TCP 重传', '${sent!['retransmits']}'),
        if (udp?['jitter_ms'] != null)
          ValueRow(
            'UDP 抖动',
            '${number(udp!['jitter_ms'] as num?, digits: 3)} ms',
          ),
        if (udp?['lost_percent'] != null)
          ValueRow('UDP 丢包', '${number(udp!['lost_percent'] as num?)} %'),
        const SizedBox(height: 8),
        const Text(
          'iperf3 · 本机与指定设备间的有效吞吐',
          style: TextStyle(color: muted, fontSize: 12),
        ),
      ],
    );
  }
}

extension on String {
  String takeOr(String fallback) => isEmpty ? fallback : this;
}
