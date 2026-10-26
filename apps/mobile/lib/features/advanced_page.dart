import 'dart:async';

import 'package:flutter/material.dart';

import '../app/controller.dart';
import '../design/components.dart';
import '../platform/network_api.g.dart';
import 'tools_page.dart';

class AdvancedToolPage extends StatefulWidget {
  const AdvancedToolPage({
    super.key,
    required this.controller,
    required this.feature,
  });
  final AppController controller;
  final String feature;
  @override
  State<AdvancedToolPage> createState() => _AdvancedToolPageState();
}

class _AdvancedToolPageState extends State<AdvancedToolPage> {
  final form = GlobalKey<FormState>();
  final host = TextEditingController();
  final port = TextEditingController();
  final duration = TextEditingController();
  final maxMb = TextEditingController(text: '20');
  String? interfaceId;
  String filter = 'ipv4_arp';
  String linkMode = 'arp';
  String arpMode = 'occupancy';
  bool discover = false;
  bool fullPayload = false;
  bool alsoAppCache = false;
  bool starting = false;
  ToolRun? run;
  List<CaptureFileData> files = [];
  bool get capture => widget.feature == 'capture';
  bool get link => widget.feature == 'link';
  bool get systemDns => widget.feature == 'systemDns';
  bool get busy => starting || (run != null && !run!.finished);
  String get title => {
    'capture': '按网卡抓包',
    'link': 'ARP / DHCP 排障',
    'systemDns': '系统 DNS 缓存',
    'ethernet': '有线临时配置',
  }[widget.feature]!;
  InterfaceData? get selected => widget
      .controller
      .snapshot
      ?.privilege
      .interfaces
      .where((item) => item.id == interfaceId)
      .firstOrNull;

  @override
  void initState() {
    super.initState();
    final state = widget.controller.snapshot;
    interfaceId =
        state?.privilege.interfaces
            .where(
              (item) =>
                  item.name == widget.controller.selectedNetwork?.interfaceName,
            )
            .firstOrNull
            ?.id ??
        state?.privilege.interfaces.firstOrNull?.id;
    duration.text = capture ? '60' : '30';
    if (capture) {
      run = widget.controller.runs.values
          .where((item) => item.kind == 'root.capture' && !item.finished)
          .firstOrNull;
      unawaited(loadFiles());
    } else if (link) {
      run = widget.controller.runs.values
          .where(
            (item) =>
                const {'root.arp', 'root.dhcp'}.contains(item.kind) &&
                !item.finished,
          )
          .firstOrNull;
      if (run?.kind == 'root.dhcp') linkMode = 'dhcp';
    }
  }

  Future<void> loadFiles() async {
    try {
      final result = await widget.controller.host.listCaptures();
      if (mounted) setState(() => files = result);
    } on Exception catch (error) {
      if (mounted) showError(context, error);
    }
  }

  @override
  void dispose() {
    if (!capture && run != null && !run!.finished) {
      unawaited(widget.controller.stop(run!));
    }
    host.dispose();
    port.dispose();
    duration.dispose();
    maxMb.dispose();
    super.dispose();
  }

  Future<bool> confirm(String title, String text) async =>
      await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(title),
          content: Text(text),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              style: FilledButton.styleFrom(minimumSize: const Size(80, 44)),
              child: const Text('继续'),
            ),
          ],
        ),
      ) ??
      false;
  Future<void> start() async {
    if (!form.currentState!.validate()) return;
    if (!systemDns && selected == null) {
      showError(context, '请选择可用接口');
      return;
    }
    if (capture &&
        fullPayload &&
        !await confirm('截取完整报文', '抓包文件可能包含明文通信内容，请仅在需要深度分析时使用。文件只保存在本机。')) {
      return;
    }
    if (link &&
        linkMode == 'dhcp' &&
        discover &&
        !await confirm(
          '发送 DHCP 发现请求',
          '会发送最多 3 次 Discover。服务器可能短暂预留所提议地址；本应用不会确认租约或释放已有地址。',
        )) {
      return;
    }
    if (!mounted) return;
    setState(() => starting = true);
    try {
      if (systemDns && alsoAppCache) {
        final clear = await widget.controller.start('clearAppDns', {});
        while (!clear.finished) {
          await Future<void>.delayed(const Duration(milliseconds: 50));
        }
        if (!clear.successful) throw Exception(clear.error ?? '应用缓存清理失败');
      }
      final parameters = capture
          ? <String, dynamic>{
              'duration': int.parse(duration.text),
              'maxMb': int.parse(maxMb.text),
              'fullPayload': fullPayload,
              'filter': filter,
              if (filter == 'host_port' && host.text.trim().isNotEmpty)
                'host': host.text.trim(),
              if (filter == 'host_port' && port.text.trim().isNotEmpty)
                'port': int.parse(port.text),
            }
          : link
          ? linkMode == 'arp'
                ? <String, dynamic>{'host': host.text.trim(), 'mode': arpMode}
                : <String, dynamic>{
                    'duration': int.parse(duration.text),
                    'mode': discover ? 'discover' : 'observe',
                    'acknowledged': discover,
                  }
          : <String, dynamic>{};
      final kind = capture
          ? 'root.capture'
          : link
          ? linkMode == 'arp'
                ? 'root.arp'
                : 'root.dhcp'
          : 'root.systemDns';
      final started = await widget.controller.start(
        kind,
        parameters,
        interface: systemDns ? null : selected,
      );
      if (mounted) setState(() => run = started);
    } on Exception catch (error) {
      if (mounted) showError(context, error);
    } finally {
      if (mounted) setState(() => starting = false);
    }
  }

  Widget integerField(
    TextEditingController field,
    String label,
    int min,
    int max, {
    bool optional = false,
  }) => TextFormField(
    controller: field,
    enabled: !busy,
    keyboardType: TextInputType.number,
    decoration: InputDecoration(labelText: label),
    validator: (value) {
      if (optional && (value ?? '').isEmpty) return null;
      final number = int.tryParse(value ?? '');
      return number == null || number < min || number > max
          ? '请输入 $min–$max 之间的整数'
          : null;
    },
  );
  Widget addressField({bool optional = false}) => TextFormField(
    controller: host,
    enabled: !busy,
    decoration: InputDecoration(labelText: optional ? 'IPv4（可选）' : '目标 IPv4'),
    validator: (value) =>
        !optional && (value ?? '').trim().isEmpty ? '请填写目标 IPv4' : null,
  );

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final state = widget.controller.snapshot?.privilege;
      final capability = state?.capabilities
          .where((item) => item.name == widget.feature)
          .firstOrNull;
      final enabled = capability?.supported == true;
      final interfaces = state?.interfaces ?? <InterfaceData>[];
      final current = run;
      return Scaffold(
        appBar: AppBar(
          title: Text(title),
          actions: [
            IconButton(
              onPressed: widget.controller.refresh,
              tooltip: '刷新状态',
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        body: Form(
          key: form,
          child: PageBody(
            children: [
              if (!enabled)
                Notice(capability?.reason ?? state?.message ?? '请先连接高级访问'),
              if (state?.status != 'root_ready')
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
              if (systemDns) ...[
                NetworkCard(
                  controller: widget.controller,
                  onSelect: () => chooseNetwork(context, widget.controller),
                ),
                const Notice(
                  '仅清理所选网络的 Android 系统解析缓存，不包含浏览器、其他应用的私有缓存、路由器或上游 DNS。',
                ),
                CheckboxListTile(
                  value: alsoAppCache,
                  onChanged: busy
                      ? null
                      : (value) => setState(() => alsoAppCache = value!),
                  title: const Text('同时清理本应用 DNS 缓存'),
                ),
              ] else ...[
                DropdownButtonFormField<String>(
                  key: ValueKey(selected?.id),
                  initialValue: selected?.id,
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: '选择接口'),
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
                if (selected != null)
                  Panel(
                    child: Column(
                      children: [
                        ValueRow('接口状态', selected!.up ? '已启用' : '未启用'),
                        ValueRow('MTU', '${selected!.mtu}'),
                      ],
                    ),
                  ),
              ],
              if (capture) ...[
                const Notice(
                  '只观察所选接口可见的 IPv4 和 ARP 报文，不解密 HTTPS。可以返回工具页，同时运行一个普通诊断；测速时抓包可能影响结果。',
                ),
                DropdownButtonFormField<String>(
                  initialValue: filter,
                  decoration: const InputDecoration(labelText: '捕获范围'),
                  items: const [
                    DropdownMenuItem(
                      value: 'ipv4_arp',
                      child: Text('IPv4 + ARP'),
                    ),
                    DropdownMenuItem(value: 'dns', child: Text('DNS')),
                    DropdownMenuItem(value: 'dhcp', child: Text('DHCP')),
                    DropdownMenuItem(
                      value: 'host_port',
                      child: Text('指定 IPv4 / 端口（保留 ARP）'),
                    ),
                  ],
                  onChanged: busy
                      ? null
                      : (value) => setState(() => filter = value!),
                ),
                if (filter == 'host_port') ...[
                  addressField(optional: true),
                  integerField(port, '端口（可选）', 1, 65535, optional: true),
                ],
                integerField(duration, '最长时间（秒）', 10, 300),
                integerField(maxMb, '文件上限（MB）', 5, 100),
                SwitchListTile(
                  value: fullPayload,
                  onChanged: busy
                      ? null
                      : (value) => setState(() => fullPayload = value),
                  title: const Text('截取完整报文'),
                  subtitle: const Text('默认每包 256 字节；截断会影响深度解析'),
                ),
              ],
              if (link) ...[
                SegmentedButton<String>(
                  segments: const [
                    ButtonSegment(value: 'arp', label: Text('ARP 检查')),
                    ButtonSegment(value: 'dhcp', label: Text('DHCP 观察')),
                  ],
                  selected: {linkMode},
                  onSelectionChanged: busy
                      ? null
                      : (value) => setState(() => linkMode = value.first),
                ),
                if (linkMode == 'arp') ...[
                  SegmentedButton<String>(
                    segments: const [
                      ButtonSegment(value: 'occupancy', label: Text('地址占用')),
                      ButtonSegment(value: 'gateway', label: Text('网关回应')),
                    ],
                    selected: {arpMode},
                    onSelectionChanged: busy
                        ? null
                        : (value) => setState(() {
                            arpMode = value.first;
                            if (arpMode == 'gateway') {
                              host.text =
                                  widget.controller.selectedNetwork?.gateway ??
                                  '';
                            }
                          }),
                  ),
                  addressField(),
                  const Notice(
                    '发送 3 次 ARP 探测。未观察到回应不能保证地址空闲；本工具不会修改对方的 ARP 缓存。',
                  ),
                ] else ...[
                  integerField(duration, '观察时间（秒）', 5, 120),
                  SwitchListTile(
                    value: discover,
                    onChanged: busy
                        ? null
                        : (value) => setState(() => discover = value),
                    title: const Text('发送发现请求'),
                    subtitle: const Text('默认只观察本机 DHCP，不主动发送'),
                  ),
                  const Notice('无需接口已有 IPv4。只显示实际观察到的步骤；多个服务器回应并不直接表示配置错误。'),
                ],
              ],
              if (widget.feature == 'ethernet')
                const Notice('临时配置需要系统配置接口与恢复能力同时验证通过。当前接口信息可查看；未验证设备保持只读。'),
              if (widget.feature != 'ethernet')
                FilledButton(
                  onPressed: busy
                      ? current != null
                            ? () async {
                                await widget.controller.stop(current);
                                if (capture) await loadFiles();
                              }
                            : null
                      : enabled
                      ? start
                      : null,
                  child: Text(
                    busy
                        ? '停止'
                        : capture
                        ? '开始抓包'
                        : systemDns
                        ? '清理当前网络系统缓存'
                        : linkMode == 'arp'
                        ? '开始检查'
                        : discover
                        ? '发送并观察'
                        : '开始观察',
                  ),
                ),
              if (current != null) _AdvancedResult(run: current),
              if (capture &&
                  current?.finished == true &&
                  current?.result['fileComplete'] == true)
                OutlinedButton.icon(
                  onPressed: () async {
                    try {
                      final message = await widget.controller.host
                          .exportCapture(current!.id);
                      if (context.mounted) {
                        ScaffoldMessenger.of(context)
                            .showSnackBar(SnackBar(content: Text(message)));
                      }
                    } on Exception catch (error) {
                      if (context.mounted) showError(context, error);
                    }
                  },
                  icon: const Icon(Icons.save_alt),
                  label: const Text('导出本次抓包'),
                ),
              if (capture) ...[
                Row(
                  children: [
                    const Expanded(
                      child: Text(
                        '本机抓包文件',
                        style: TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                    TextButton(onPressed: loadFiles, child: const Text('刷新')),
                  ],
                ),
                Text(
                  '总量 ${(files.fold<int>(0, (sum, file) => sum + file.bytes) / 1000000).toStringAsFixed(1)} / 200 MB',
                  style: const TextStyle(color: muted),
                ),
                for (final file in files)
                  Card(
                    child: ListTile(
                      title: Text('${file.interfaceName} · ${file.packets} 个包'),
                      subtitle: Text(
                        '${(file.bytes / 1000000).toStringAsFixed(2)} MB · ${file.complete ? '已完成' : '未完整结束'}',
                      ),
                      trailing: Wrap(
                        children: [
                          IconButton(
                            tooltip: '导出',
                            onPressed: file.complete
                                ? () async {
                                    try {
                                      await widget.controller.host
                                          .exportCapture(file.id);
                                    } on Exception catch (error) {
                                      if (context.mounted) {
                                        showError(context, error);
                                      }
                                    }
                                  }
                                : null,
                            icon: const Icon(Icons.save_alt),
                          ),
                          IconButton(
                            tooltip: '删除',
                            onPressed: () async {
                              if (!await confirm('删除抓包文件', '删除后无法在本应用中恢复。')) {
                                return;
                              }
                              try {
                                await widget.controller.host.deleteCapture(
                                  file.id,
                                );
                                await loadFiles();
                              } on Exception catch (error) {
                                if (context.mounted) showError(context, error);
                              }
                            },
                            icon: const Icon(Icons.delete_outline),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ],
          ),
        ),
      );
    },
  );
}

class _AdvancedResult extends StatelessWidget {
  const _AdvancedResult({required this.run});
  final ToolRun run;
  @override
  Widget build(BuildContext context) {
    final live = run.events
        .where((item) => item.state == 'capture')
        .lastOrNull
        ?.data;
    final result = run.result.isEmpty
        ? live ?? <String, dynamic>{}
        : run.result;
    return Panel(
      title: run.finished
          ? '操作结果'
          : run.kind == 'root.systemDns'
          ? '正在清理'
          : '正在观察',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (run.kind == 'root.systemDns') ...[
            ValueRow('本次操作网络', run.networkName),
            ValueRow(
              'IPv4',
              (run.networkSnapshot['ipv4'] as List?)?.join(' / ') ?? '未知',
            ),
          ],
          if (run.error != null) Notice(run.error!),
          if (result['message'] != null) Text('${result['message']}'),
          if (result.containsKey('packets'))
            ValueRow('已捕获', '${result['packets']} 个包'),
          if (result.containsKey('bytes'))
            ValueRow(
              '文件大小',
              '${((result['bytes'] as num) / 1000000).toStringAsFixed(2)} MB',
            ),
          if (result.containsKey('dropped'))
            ValueRow('捕获丢包', result['dropped']?.toString() ?? '未知'),
          if (result['protocols'] case final Map protocols)
            for (final entry in protocols.entries)
              ValueRow('${entry.key}', '${entry.value}'),
          if (result['multipleServers'] == true) const Notice('观察到多个 DHCP 服务器'),
          if (result['missingSteps'] case final List steps)
            if (steps.isNotEmpty) Notice('未观察到：${steps.join(' / ')}'),
          for (final event
              in run.events
                  .where((item) => item.state == 'arp' || item.state == 'dhcp')
                  .toList()
                  .reversed
                  .take(50))
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(
                event.state == 'arp'
                    ? '${event.data['senderIp']} · ${event.data['senderMac']}'
                    : '${event.data['message']} · 事务 ${event.data['xid']}',
              ),
              subtitle: Text(
                event.state == 'arp'
                    ? '本次探测后 ${event.data['observedAfterProbeMs']} ms 观察到'
                    : [
                        if (event.data['server'] != null)
                          '服务器 ${event.data['server']}',
                        if (event.data['offeredIp'] != null)
                          '地址 ${event.data['offeredIp']}',
                        if (event.data['mask'] != null)
                          '掩码 ${event.data['mask']}',
                        if (event.data['gateways'] != null)
                          '网关 ${event.data['gateways']}',
                        if (event.data['dns'] != null)
                          'DNS ${event.data['dns']}',
                        if (event.data['leaseSeconds'] != null)
                          '租期 ${event.data['leaseSeconds']} 秒',
                      ].join('\n'),
              ),
            ),
          if (run.events
                  .where((item) => item.state == 'arp' || item.state == 'dhcp')
                  .isEmpty &&
              run.kind != 'root.capture' &&
              !run.finished)
            const Text('等待符合条件的报文…', style: TextStyle(color: muted)),
        ],
      ),
    );
  }
}
