import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../app/controller.dart';
import '../design/components.dart';

class OverviewPage extends StatefulWidget {
  const OverviewPage({
    super.key,
    required this.controller,
    required this.openTool,
  });
  final AppController controller;
  final void Function(String, Map<String, dynamic>) openTool;
  @override
  State<OverviewPage> createState() => _OverviewPageState();
}

class _OverviewPageState extends State<OverviewPage> {
  String? _ipRunId;
  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final network = controller.selectedNetwork;
    final ipRun = controller.runs[_ipRunId];
    return RefreshIndicator(
      onRefresh: controller.refresh,
      child: PageBody(
        children: [
          NetworkCard(
            controller: controller,
            onSelect: () => chooseNetwork(context, controller),
          ),
          if (controller.loading) const LinearProgressIndicator(),
          if (controller.lastError != null)
            Notice(controller.lastError!, warning: true),
          if (network == null)
            Notice(
              '请选择 Wi-Fi 或已连接的有线网卡。没有互联网连接时，也可以检查局域网。',
              action: TextButton(
                onPressed: () => controller.host.openSystemSettings('wifi'),
                child: const Text('打开 Wi-Fi 设置'),
              ),
            ),
          Panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const StatusBadge('本机 IPv4'),
                const SizedBox(height: 12),
                SelectableText(
                  network?.addresses.firstOrNull ?? '尚未获取',
                  style: const TextStyle(
                    fontSize: 31,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                if (network != null && network.addresses.length > 1) ...[
                  const SizedBox(height: 8),
                  Text(
                    network.addresses.skip(1).join('\n'),
                    style: const TextStyle(color: muted),
                  ),
                ],
                const SizedBox(height: 10),
                const Divider(),
                ValueRow('网关', network?.gateway ?? '无默认网关', copy: true),
                if (network?.gateway != null)
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton(
                      onPressed: () =>
                          widget.openTool('ping', {'host': network!.gateway}),
                      child: const Text('Ping 网关'),
                    ),
                  ),
              ],
            ),
          ),
          Panel(
            title: '连接信息',
            child: Column(
              children: [
                ValueRow('接口', network?.interfaceName ?? '—'),
                ValueRow(
                  '子网掩码',
                  network?.prefixes.firstOrNull == null
                      ? '—'
                      : mask(network!.prefixes.first),
                ),
                ValueRow(
                  'DNS',
                  network?.dnsServers.join('\n').takeUnlessEmpty() ?? '系统未提供',
                  copy: true,
                ),
                ValueRow('MTU', network?.mtu?.toString() ?? '系统未提供'),
                if (network?.transport == 'wifi') ...[
                  ValueRow(
                    '当前信号',
                    network?.rssi == null ? '权限或系统限制' : '${network!.rssi} dBm',
                  ),
                  ValueRow(
                    '链路协商速率',
                    network?.linkSpeedMbps == null
                        ? '系统未提供'
                        : '${network!.linkSpeedMbps} Mbps',
                  ),
                  ValueRow(
                    '频率',
                    network?.frequency == null
                        ? '系统未提供'
                        : '${network!.frequency} MHz',
                  ),
                  if (network?.bssid != null)
                    ValueRow('接入点 BSSID', network!.bssid!, copy: true),
                ],
                ValueRow(
                  '互联网验证',
                  network == null
                      ? '—'
                      : network.validated
                      ? '系统验证通过'
                      : '未验证，仍可检查局域网',
                ),
              ],
            ),
          ),
          Panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    const StatusBadge('公网 IPv4'),
                    const Spacer(),
                    IconButton(
                      tooltip: '复制公网 IPv4',
                      onPressed: controller.publicIp == null
                          ? null
                          : () => Clipboard.setData(
                              ClipboardData(text: controller.publicIp!),
                            ),
                      icon: const Icon(Icons.copy_rounded, size: 19),
                    ),
                  ],
                ),
                Text(
                  controller.publicIp ?? '尚未查询',
                  style: const TextStyle(
                    fontSize: 27,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  controller.publicIpAt == null
                      ? '使用所选网络访问 IP.SB'
                      : '${controller.publicIpFresh ? '最近查询' : '上次查询'} ${clockLabel(controller.publicIpAt!)} · IP.SB',
                  style: const TextStyle(color: muted, fontSize: 12),
                ),
                const SizedBox(height: 13),
                OutlinedButton(
                  onPressed:
                      network == null || (ipRun != null && !ipRun.finished)
                      ? null
                      : () async {
                          try {
                            final run = await controller.start('publicIp', {});
                            if (mounted) setState(() => _ipRunId = run.id);
                          } on Exception catch (error) {
                            if (context.mounted) showError(context, error);
                          }
                        },
                  child: Text(
                    ipRun != null && !ipRun.finished ? '正在查询…' : '查询公网 IP',
                  ),
                ),
                if (ipRun?.error != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      ipRun!.error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                        fontSize: 12,
                      ),
                    ),
                  ),
              ],
            ),
          ),
          Panel(
            title: '可用网络',
            child: Column(
              children: [
                for (final item in controller.snapshot?.networks ?? [])
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(switch (item.transport) {
                      'wifi' => Icons.wifi,
                      'ethernet' => Icons.settings_ethernet,
                      'vpn' => Icons.vpn_lock,
                      _ => Icons.network_cell,
                    }, color: item.id == network?.id ? accent : muted),
                    title: Text(
                      item.displayName,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    subtitle: Text(
                      '${item.interfaceName} · ${item.addresses.isEmpty ? '无 IPv4' : item.addresses.join(', ')}${item.isDefault ? '\n系统默认网络' : ''}',
                    ),
                    trailing: item.id == network?.id
                        ? const Icon(Icons.check_circle, color: accent)
                        : item.transport == 'wifi' ||
                              item.transport == 'ethernet'
                        ? const Icon(Icons.chevron_right)
                        : const Text(
                            '仅展示',
                            style: TextStyle(color: muted, fontSize: 12),
                          ),
                    onTap:
                        item.transport == 'wifi' || item.transport == 'ethernet'
                        ? () => chooseNetwork(context, controller)
                        : null,
                  ),
                if (controller.snapshot?.networks.isEmpty ?? true)
                  const Text('尚未发现网络'),
              ],
            ),
          ),
          const Notice('链路协商速率不等于实际吞吐；公网 IP 是服务看到的出口地址，可能来自 NAT、VPN 或代理。'),
        ],
      ),
    );
  }
}

String mask(int prefix) {
  if (prefix < 0 || prefix > 32) return '未知';
  final value = prefix == 0 ? 0 : (0xffffffff << (32 - prefix)) & 0xffffffff;
  return [24, 16, 8, 0].map((shift) => (value >> shift) & 255).join('.');
}

extension on String {
  String? takeUnlessEmpty() => isEmpty ? null : this;
}
