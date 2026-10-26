import 'dart:async';

import 'package:flutter/material.dart';

import '../app/controller.dart';
import '../design/components.dart';
import '../platform/network_api.g.dart';
import 'tools_page.dart';

class EthernetPage extends StatefulWidget {
  const EthernetPage({super.key, required this.controller});
  final AppController controller;
  @override
  State<EthernetPage> createState() => _EthernetPageState();
}

class _EthernetPageState extends State<EthernetPage> {
  final form = GlobalKey<FormState>();
  final fields = <String, TextEditingController>{};
  String mode = 'dhcp';
  String? interfaceId;
  bool route = false;
  bool reconnect = false;
  bool busy = false;
  bool allowPop = false;
  @override
  void initState() {
    super.initState();
    for (final entry in {
      'address': '',
      'prefix': '24',
      'gateway': '',
      'dns': '',
      'mtu': '',
      'leaseMinutes': '5',
      'routeDestination': '',
      'routeGateway': '',
    }.entries) {
      fields[entry.key] = TextEditingController(text: entry.value);
    }
    interfaceId = widget.controller.snapshot?.privilege.interfaces
        .where((item) => item.transport == 'ethernet')
        .firstOrNull
        ?.id;
  }

  @override
  void dispose() {
    for (final field in fields.values) {
      field.dispose();
    }
    super.dispose();
  }

  InterfaceData? get selected => widget
      .controller
      .snapshot
      ?.privilege
      .interfaces
      .where((item) => item.id == interfaceId && item.transport == 'ethernet')
      .firstOrNull;
  Map<String, dynamic>? get transaction => widget.controller.configTransaction;
  bool get outstanding =>
      transaction != null &&
      !const {'preview', 'restored'}.contains(transaction!['state']);

  Future<void> action(String kind, Map<String, dynamic> parameters) async {
    setState(() => busy = true);
    try {
      if (const {'root.ethernetApply', 'root.ethernetRestore'}.contains(kind)) {
        for (final run
            in widget.controller.runs.values
                .where((item) => !item.finished)
                .toList()) {
          await widget.controller.stop(run);
        }
        final until = DateTime.now().add(const Duration(seconds: 4));
        while (widget.controller.runs.values.any((item) => !item.finished) &&
            DateTime.now().isBefore(until)) {
          await Future<void>.delayed(const Duration(milliseconds: 100));
        }
        if (widget.controller.runs.values.any((item) => !item.finished)) {
          throw Exception('仍有任务正在停止，请稍后重试');
        }
      }
      final run = await widget.controller.start(
        kind,
        parameters,
        interface: selected,
      );
      final until = DateTime.now().add(const Duration(seconds: 35));
      while (!run.finished && DateTime.now().isBefore(until)) {
        await Future<void>.delayed(const Duration(milliseconds: 100));
      }
      if (!run.finished) throw Exception('执行端仍在核对配置，请查看恢复状态');
      if (!run.successful) throw Exception(run.error ?? '配置操作失败');
      await widget.controller.refresh();
    } on Exception catch (error) {
      if (mounted) showError(context, error);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> preview() async {
    if (!form.currentState!.validate() || selected == null) return;
    await action('root.ethernetPreview', {
      'mode': mode,
      'leaseMinutes': int.parse(fields['leaseMinutes']!.text),
      'reconnect': reconnect,
      if (fields['mtu']!.text.trim().isNotEmpty)
        'mtu': int.parse(fields['mtu']!.text),
      if (mode == 'static') ...{
        'address': fields['address']!.text.trim(),
        'prefix': int.parse(fields['prefix']!.text),
        'gateway': fields['gateway']!.text.trim(),
        'dns': fields['dns']!.text
            .split(RegExp(r'[\s,，]+'))
            .where((item) => item.isNotEmpty)
            .toList(),
      },
      if (route) ...{
        'routeDestination': fields['routeDestination']!.text.trim(),
        'routeGateway': fields['routeGateway']!.text.trim(),
      },
    });
  }

  Future<void> apply() async {
    final id = transaction?['transactionId'];
    if (id == null) return;
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('应用临时有线配置'),
        content: const Text(
          '会停止正在运行的诊断和抓包，并可能短暂中断此网卡上的其他应用连接。应用后请在 30 秒内确认，否则恢复原配置。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(minimumSize: const Size(80, 44)),
            child: const Text('应用并检查'),
          ),
        ],
      ),
    );
    if (accepted == true && mounted) {
      await action('root.ethernetApply', {'transactionId': id});
    }
  }

  Future<void> leave() async {
    if (busy) return;
    if (outstanding && transaction?['transactionId'] != null) {
      await action('root.ethernetRestore', {
        'transactionId': transaction!['transactionId'],
      });
    }
    if (!mounted) return;
    setState(() => allowPop = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.pop(context);
    });
  }

  Widget field(
    String key,
    String label, {
    int? min,
    int? max,
    bool optional = false,
  }) => TextFormField(
    controller: fields[key],
    enabled: !busy && !outstanding,
    keyboardType: min != null ? TextInputType.number : TextInputType.text,
    decoration: InputDecoration(labelText: label),
    validator: (value) {
      final text = (value ?? '').trim();
      if (text.isEmpty) return optional ? null : '请填写$label';
      if (min != null) {
        final number = int.tryParse(text);
        if (number == null || number < min || number > max!) {
          return '请输入 $min–$max 之间的整数';
        }
      }
      return null;
    },
  );
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final privilege = widget.controller.snapshot?.privilege;
      final capability = privilege?.capabilities
          .where((item) => item.name == 'ethernet')
          .firstOrNull;
      final interfaces =
          privilege?.interfaces
              .where((item) => item.transport == 'ethernet')
              .toList() ??
          <InterfaceData>[];
      final data = transaction;
      final state = data?['state'];
      return PopScope(
        canPop: allowPop || !outstanding,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) unawaited(leave());
        },
        child: Scaffold(
          appBar: AppBar(title: const Text('有线临时配置')),
          body: Form(
            key: form,
            child: PageBody(
              children: [
                const Notice(
                  '只修改系统识别的 USB 有线接口，会影响使用此接口的其他应用。默认保留 5 分钟；退出、切后台、未确认或到期会尝试恢复。失去权限时可能需要重新授权后恢复。',
                ),
                Notice(capability?.reason ?? privilege?.message ?? '请先连接高级访问'),
                if (privilege?.status != 'root_ready')
                  OutlinedButton(
                    onPressed: () => Navigator.push(
                      context,
                      MaterialPageRoute<void>(
                        builder: (_) =>
                            AdvancedAccessPage(controller: widget.controller),
                      ),
                    ),
                    child: const Text('管理高级访问'),
                  ),
                if (data != null)
                  EthernetTransactionPanel(
                    controller: widget.controller,
                    busy: busy,
                    onConfirm: () => action('root.ethernetConfirm', {
                      'transactionId': data['transactionId'],
                    }),
                    onRestore: () => action('root.ethernetRestore', {
                      'transactionId': data['transactionId'],
                    }),
                  ),
                if (!outstanding) ...[
                  DropdownButtonFormField<String>(
                    key: ValueKey(selected?.id),
                    initialValue: selected?.id,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'USB 有线接口'),
                    items: interfaces
                        .map(
                          (item) => DropdownMenuItem(
                            value: item.id,
                            child: Text(
                              '${item.name} · ${item.addresses.isEmpty ? '尚无 IPv4' : item.addresses.join(', ')}',
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        )
                        .toList(),
                    onChanged: busy
                        ? null
                        : (value) => setState(() => interfaceId = value),
                  ),
                  if (interfaces.isEmpty)
                    const Notice('没有发现有线接口。请连接 USB 网卡；Wi-Fi 和蜂窝接口保持只读。'),
                  SegmentedButton<String>(
                    segments: const [
                      ButtonSegment(value: 'dhcp', label: Text('DHCP')),
                      ButtonSegment(value: 'static', label: Text('临时静态 IPv4')),
                    ],
                    selected: {mode},
                    onSelectionChanged: busy
                        ? null
                        : (value) => setState(() => mode = value.first),
                  ),
                  if (mode == 'static') ...[
                    field('address', 'IPv4 地址'),
                    field('prefix', '前缀长度', min: 1, max: 32),
                    field('gateway', '网关（直连可留空）', optional: true),
                    field('dns', 'DNS（可选，用空格或逗号分隔）', optional: true),
                  ],
                  field(
                    'mtu',
                    'MTU（留空保留原值）',
                    min: 576,
                    max: 65535,
                    optional: true,
                  ),
                  SwitchListTile(
                    value: route,
                    onChanged: busy
                        ? null
                        : (value) => setState(() => route = value),
                    title: const Text('添加一条临时路由'),
                  ),
                  if (route) ...[
                    field('routeDestination', '目标网段，例如 192.168.10.0/24'),
                    field('routeGateway', '路由网关（直接可达时留空）', optional: true),
                  ],
                  CheckboxListTile(
                    value: reconnect,
                    onChanged: busy
                        ? null
                        : (value) => setState(() => reconnect = value ?? false),
                    title: const Text('重新连接此网卡 / 重试 DHCP'),
                    subtitle: const Text('支持此操作的系统会短暂断开并重新连接'),
                  ),
                  field('leaseMinutes', '会话期限（分钟）', min: 1, max: 30),
                  OutlinedButton(
                    onPressed:
                        busy ||
                            selected == null ||
                            privilege?.status != 'root_ready'
                        ? null
                        : preview,
                    child: const Text('预览变更'),
                  ),
                  if (state == 'preview')
                    FilledButton(
                      onPressed: busy || capability?.supported != true
                          ? null
                          : apply,
                      child: const Text('应用并检查'),
                    ),
                ],
              ],
            ),
          ),
        ),
      );
    },
  );
}

class EthernetTransactionPanel extends StatelessWidget {
  const EthernetTransactionPanel({
    super.key,
    required this.controller,
    required this.busy,
    required this.onConfirm,
    required this.onRestore,
  });
  final AppController controller;
  final bool busy;
  final VoidCallback onConfirm;
  final VoidCallback onRestore;
  @override
  Widget build(BuildContext context) {
    final data = controller.configTransaction!;
    final state = data['state'];
    final original = data['originalSummary'] as Map?;
    final system = original?['system'] as Map?;
    final plan = data['plan'] as Map?;
    final deadline =
        (state == 'awaiting_confirmation'
                ? data['confirmUntil']
                : data['expiresAt'])
            as num?;
    final remaining = deadline == null
        ? null
        : ((deadline - DateTime.now().millisecondsSinceEpoch) / 1000)
              .ceil()
              .clamp(0, 1800);
    return Panel(
      title:
          {
            'preview': '变更预览',
            'prepared': '原配置已保存',
            'applying': '正在应用',
            'awaiting_confirmation': '等待确认',
            'active': '临时配置生效中',
            'restoring': '正在恢复',
            'restored': '已恢复',
            'recovery_pending': '恢复未完成',
          }[state] ??
          '配置状态',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('${data['message'] ?? ''}'),
          if (data['interface'] != null) ValueRow('接口', '${data['interface']}'),
          if (plan != null) ...[
            ValueRow('地址模式', '${system?['mode'] ?? '未知'} → ${plan['mode']}'),
            ValueRow(
              'IPv4',
              '${original?['addresses'] ?? '未知'} → ${plan['mode'] == 'dhcp' ? '自动获取' : '${plan['address']}/${plan['prefix']}'}',
            ),
            ValueRow(
              'MTU',
              '${original?['mtu'] ?? '未知'} → ${plan['mtu'] ?? '保留原值'}',
            ),
            if (plan['mode'] == 'static')
              ValueRow(
                '临时网关',
                '${plan['gateway'] == '' ? '无网关，局域网直连' : plan['gateway']}',
              ),
            if (plan['routeDestination'] != null)
              ValueRow(
                '临时路由',
                '${plan['routeDestination']} via ${plan['routeGateway'] == '' ? '接口直达' : plan['routeGateway']}',
              ),
          ],
          if (remaining != null &&
              const {'active', 'awaiting_confirmation'}.contains(state))
            ValueRow(
              state == 'awaiting_confirmation' ? '确认剩余' : '会话剩余',
              '$remaining 秒',
            ),
          if (state == 'awaiting_confirmation')
            FilledButton(
              onPressed: busy ? null : onConfirm,
              child: const Text('继续临时使用'),
            ),
          if (const {
            'awaiting_confirmation',
            'active',
            'recovery_pending',
          }.contains(state))
            OutlinedButton(
              onPressed:
                  busy || controller.snapshot?.privilege.status != 'root_ready'
                  ? null
                  : onRestore,
              child: Text(state == 'recovery_pending' ? '重新核对并恢复' : '结束并恢复'),
            ),
          if (state == 'recovery_pending')
            const Notice('新的配置操作已冻结。请重新连接原网卡和 Shizuku；若系统已有其他配置，本应用不会覆盖它。'),
        ],
      ),
    );
  }
}
