import 'package:flutter/material.dart';

import '../app/controller.dart';
import '../design/components.dart';
import 'advanced_page.dart';
import 'ethernet_page.dart';

class ToolsPage extends StatefulWidget {
  const ToolsPage({
    super.key,
    required this.controller,
    required this.openTool,
  });
  final AppController controller;
  final void Function(String, Map<String, dynamic>) openTool;
  @override
  State<ToolsPage> createState() => _ToolsPageState();
}

class _ToolsPageState extends State<ToolsPage> {
  bool advanced = false;
  @override
  Widget build(BuildContext context) {
    final state = widget.controller.snapshot?.privilege;
    void manage() => Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (_) => AdvancedAccessPage(controller: widget.controller),
      ),
    );
    return PageBody(
      children: [
        NetworkCard(
          controller: widget.controller,
          onSelect: () => chooseNetwork(context, widget.controller),
        ),
        SegmentedButton<bool>(
          showSelectedIcon: false,
          segments: const [
            ButtonSegment(value: false, label: Text('基础工具')),
            ButtonSegment(value: true, label: Text('高级工具')),
          ],
          selected: {advanced},
          onSelectionChanged: (value) => setState(() => advanced = value.first),
        ),
        if (!advanced) ...[
          Panel(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
            child: Column(
              children: [
                for (final item in [
                  ('ping', 'Ping', '检查延迟与丢包', Icons.monitor_heart_outlined),
                  ('tcping', 'TCPing', '检查 TCP 端口', Icons.dns_outlined),
                  ('telnet', 'Telnet', '连接设备终端', Icons.terminal_rounded),
                  ('trace', 'Traceroute', '查看逐跳路径', Icons.route_outlined),
                  ('dns', 'DNS / dig', '查询域名解析', Icons.language_rounded),
                ])
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(item.$4, color: accent, size: 29),
                    title: Text(
                      item.$2,
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    subtitle: Text(
                      item.$3,
                      style: const TextStyle(color: muted, fontSize: 12),
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => widget.openTool(item.$1, {}),
                  ),
              ],
            ),
          ),
          if (widget.controller.selectedNetwork?.gateway != null)
            Panel(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text(
                    '快速检查',
                    style: TextStyle(
                      color: accent,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    '当前网关',
                    style: TextStyle(color: muted, fontSize: 12),
                  ),
                  Text(
                    widget.controller.selectedNetwork!.gateway!,
                    style: const TextStyle(
                      fontSize: 25,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 14),
                  FilledButton(
                    onPressed: () => widget.openTool('ping', {
                      'host': widget.controller.selectedNetwork!.gateway,
                    }),
                    child: const Text('Ping 网关'),
                  ),
                ],
              ),
            ),
        ],
        Card(
          child: ListTile(
            leading: Icon(
              Icons.verified_user_outlined,
              color: state?.status == 'root_ready' ? Colors.green : accent,
            ),
            title: Text(privilegeLabel(state?.status)),
            subtitle: const Text(
              'KernelSU Next + Shizuku',
              style: TextStyle(fontSize: 12),
            ),
            trailing: const Icon(Icons.chevron_right),
            onTap: manage,
          ),
        ),
        if (advanced) ...[
          for (final item in [
            ('capture', '按网卡抓包', '观察报文，导出抓包文件', Icons.find_in_page_outlined),
            (
              'link',
              'ARP / DHCP 排障',
              '检查地址占用与获取过程',
              Icons.account_tree_outlined,
            ),
            ('systemDns', '系统 DNS 缓存', '清理当前网络的系统缓存', Icons.storage_rounded),
            (
              'ethernet',
              '有线临时配置',
              '临时修改，结束后恢复',
              Icons.settings_ethernet_rounded,
            ),
          ])
            Card(
              child: ListTile(
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 7,
                ),
                leading: Icon(item.$4, size: 29, color: accent),
                title: Text(
                  item.$2,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                subtitle: Text(
                  item.$3,
                  style: const TextStyle(fontSize: 12, color: muted),
                ),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => item.$1 == 'ethernet'
                        ? EthernetPage(controller: widget.controller)
                        : AdvancedToolPage(
                            controller: widget.controller,
                            feature: item.$1,
                          ),
                  ),
                ),
              ),
            ),
          Notice(state?.message ?? '高级功能需要连接 Shizuku 并完成设备能力检查。'),
        ] else
          const Notice('基础工具无需 Root。高级功能按设备能力单独开放。'),
      ],
    );
  }
}

String privilegeLabel(String? state) => switch (state) {
  'root_ready' => '高级权限已连接',
  'not_installed' => '尚未安装 Shizuku',
  'not_running' => 'Shizuku 服务未启动',
  'unauthorized' => '尚未授权本应用',
  'shell_mode' => '当前是 ADB 模式',
  'capability_limited' => '高级能力待检查',
  'disconnected' => '高级访问已断开',
  _ => '正在检查高级访问',
};

class AdvancedAccessPage extends StatelessWidget {
  const AdvancedAccessPage({super.key, required this.controller});
  final AppController controller;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final state = controller.snapshot?.privilege;
      final available = state?.status == 'root_ready';
      final authorizationNeeded = state?.status == 'unauthorized';
      return Scaffold(
        appBar: AppBar(title: const Text('高级访问')),
        body: PageBody(
          children: [
            const Text('连接 Shizuku，启用高级诊断', style: TextStyle(color: muted)),
            Panel(
              child: Column(
                children: [
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const CircleAvatar(radius: 17, child: Text('1')),
                    title: const Text('KernelSU Next'),
                    subtitle: const Text('Root 环境由设备端配置'),
                    trailing: const Icon(Icons.info_outline, color: muted),
                  ),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const CircleAvatar(radius: 17, child: Text('2')),
                    title: const Text('Shizuku 服务'),
                    subtitle: Text(
                      state?.serverUid == 0
                          ? '已运行 · Root 模式'
                          : state?.serverUid == 2000
                          ? '已运行 · ADB 模式'
                          : privilegeLabel(state?.status),
                    ),
                    trailing: Icon(
                      state?.serverUid == 0
                          ? Icons.check_circle
                          : Icons.info_outline,
                      color: state?.serverUid == 0 ? Colors.green : muted,
                    ),
                  ),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const CircleAvatar(radius: 17, child: Text('3')),
                    title: const Text('本应用授权'),
                    subtitle: Text(
                      state?.authorized == true ? '已授权，按功能检查可用性' : '尚未授权',
                    ),
                    trailing: Icon(
                      state?.authorized == true
                          ? Icons.check_circle
                          : Icons.info_outline,
                      color: state?.authorized == true
                          ? Colors.green
                          : Colors.orange,
                    ),
                  ),
                ],
              ),
            ),
            const Notice(
              'KernelSU Next 提供 Root；Shizuku 管理本应用的访问。通过无线调试启动的 ADB 模式，不等于 Root 模式。',
            ),
            FilledButton(
              onPressed: () async {
                try {
                  if (state?.status == 'not_installed' ||
                      state?.status == 'not_running' ||
                      state?.status == 'shell_mode') {
                    await controller.host.openSystemSettings('shizuku');
                  } else {
                    await controller.requestAdvancedAccess();
                  }
                } on Exception catch (error) {
                  if (context.mounted) showError(context, error);
                }
              },
              child: Text(
                authorizationNeeded
                    ? '通过 Shizuku 授权'
                    : state?.authorized == true
                    ? '检查可用功能'
                    : '打开 Shizuku',
              ),
            ),
            if (state?.message != null)
              Notice(state!.message!, warning: !available),
            if (state != null)
              Panel(
                title: '功能状态',
                child: Column(
                  children: [
                    for (final capability in state.capabilities)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                          const {
                                'capture': '按网卡抓包',
                                'link': 'ARP / DHCP 排障',
                                'systemDns': '系统 DNS 缓存',
                                'ethernet': '有线临时配置',
                              }[capability.name] ??
                              capability.name,
                        ),
                        subtitle: capability.reason.isEmpty
                            ? null
                            : Text(
                                capability.reason,
                                style: const TextStyle(fontSize: 12),
                              ),
                        trailing: StatusBadge(
                          capability.supported ? '可用' : '不可用',
                          color: capability.supported ? Colors.green : muted,
                        ),
                      ),
                  ],
                ),
              ),
          ],
        ),
      );
    },
  );
}
