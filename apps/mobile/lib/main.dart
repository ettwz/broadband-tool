import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/controller.dart';
import 'design/components.dart';
import 'features/overview_page.dart';
import 'features/speed_page.dart';
import 'features/tool_page.dart';
import 'features/tools_page.dart';
import 'features/advanced_page.dart';
import 'features/ethernet_page.dart';
import 'features/wifi_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  LicenseRegistry.addLicense(() async* {
    for (final entry in {
      'iperf3 3.22 (bundled native engine)': 'iperf3.txt',
      'Shizuku API 13.1.5': 'shizuku.txt',
      'dnsjava 3.6.5': 'dnsjava.txt',
      'OkHttp 5.4.0, Okio, Kotlin, kotlinx.coroutines, AndroidX WebKit':
          'apache-2.0.txt',
      'Android DNS resolver AIDL (AOSP)': 'apache-2.0.txt',
    }.entries) {
      yield LicenseEntryWithLineBreaks([
        entry.key,
      ], await rootBundle.loadString('assets/licenses/${entry.value}'));
    }
  });
  runApp(const ProviderScope(child: BroadbandApp()));
}

class BroadbandApp extends StatelessWidget {
  const BroadbandApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'broadband-tool',
    debugShowCheckedModeBanner: false,
    locale: const Locale('zh', 'CN'),
    supportedLocales: const [Locale('zh', 'CN'), Locale('en')],
    localizationsDelegates: const [
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    theme: toolTheme(Brightness.light),
    darkTheme: toolTheme(Brightness.dark),
    home: const HomeShell(),
  );
}

class HomeShell extends ConsumerStatefulWidget {
  const HomeShell({super.key});
  @override
  ConsumerState<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends ConsumerState<HomeShell>
    with WidgetsBindingObserver {
  int index = 0;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) {
      ref.read(appControllerProvider).pauseScan();
    }
    if (state == AppLifecycleState.resumed) {
      ref.read(appControllerProvider).refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = ref.watch(appControllerProvider);
    void openTool(String kind, Map<String, dynamic> parameters) =>
        Navigator.push(
          context,
          MaterialPageRoute<void>(
            builder: (_) => ToolPage(
              controller: controller,
              kind: kind,
              initial: parameters,
            ),
          ),
        );
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) => Scaffold(
        appBar: AppBar(
          title: Text(['网络概览', 'Wi-Fi 检查', '测速', '网络工具'][index]),
          actions: [
            IconButton(
              tooltip: '历史记录',
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute<void>(
                  builder: (_) => HistoryPage(controller: controller),
                ),
              ),
              icon: const Icon(Icons.history_rounded),
            ),
            IconButton(
              tooltip: '刷新网络信息',
              onPressed: controller.refresh,
              icon: const Icon(Icons.refresh_rounded),
            ),
            IconButton(
              tooltip: '关于与许可',
              onPressed: () async {
                try {
                  final version = await controller.host.getAppVersion();
                  if (!context.mounted) return;
                  showAboutDialog(
                    context: context,
                    applicationName: 'broadband-tool',
                    applicationVersion: version,
                    children: const [
                      Text('IPv4 家庭网络诊断\n普通工具与可选高级模式\n结果与历史仅保存在本机。'),
                    ],
                  );
                } on Exception catch (error) {
                  if (context.mounted) showError(context, error);
                }
              },
              icon: const Icon(Icons.info_outline_rounded),
            ),
          ],
        ),
        body: SafeArea(
          child: Column(
            children: [
              if (controller.configTransaction != null &&
                  !const {
                    'preview',
                    'restored',
                  }.contains(controller.configTransaction!['state']))
                Material(
                  color: Colors.orange.withValues(alpha: .12),
                  child: ListTile(
                    dense: true,
                    leading: const Icon(
                      Icons.settings_ethernet,
                      color: Colors.orange,
                    ),
                    title: Text(
                      controller.configTransaction!['state'] ==
                              'recovery_pending'
                          ? '有线配置恢复未完成'
                          : '临时有线配置 · ${controller.configTransaction!['interface'] ?? ''}',
                    ),
                    subtitle: Text(
                      '${controller.configTransaction!['message'] ?? ''}',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute<void>(
                        builder: (_) => EthernetPage(controller: controller),
                      ),
                    ),
                  ),
                ),
              if (controller.activeCapture != null)
                Material(
                  color: Colors.amber.withValues(alpha: .1),
                  child: ListTile(
                    dense: true,
                    leading: const Icon(
                      Icons.fiber_manual_record,
                      color: Colors.orange,
                      size: 18,
                    ),
                    title: const Text('正在抓包 · 测速结果可能受影响'),
                    trailing: TextButton(
                      onPressed: () =>
                          controller.stop(controller.activeCapture!),
                      child: const Text('停止'),
                    ),
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute<void>(
                        builder: (_) => AdvancedToolPage(
                          controller: controller,
                          feature: 'capture',
                        ),
                      ),
                    ),
                  ),
                ),
              if (controller.activeRun != null)
                Material(
                  color: accent.withValues(alpha: .08),
                  child: ListTile(
                    dense: true,
                    leading: const SizedBox(
                      width: 17,
                      height: 17,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                    title: Text(
                      '${toolName(controller.activeRun!.kind)} · ${stateName(controller.activeRun!.state)}',
                    ),
                    trailing: TextButton(
                      onPressed: () => controller.stop(controller.activeRun!),
                      child: const Text('停止'),
                    ),
                    onTap: controller.activeRun!.kind == 'webspeed'
                        ? null
                        : () => Navigator.push(
                            context,
                            MaterialPageRoute<void>(
                              builder: (_) =>
                                  controller.activeRun!.kind.startsWith('root.')
                                  ? AdvancedToolPage(
                                      controller: controller,
                                      feature:
                                          controller.activeRun!.kind ==
                                              'root.systemDns'
                                          ? 'systemDns'
                                          : 'link',
                                    )
                                  : ToolPage(
                                      controller: controller,
                                      kind: controller.activeRun!.kind,
                                      existing: controller.activeRun,
                                    ),
                            ),
                          ),
                  ),
                ),
              Expanded(
                child: IndexedStack(
                  index: index,
                  children: [
                    OverviewPage(controller: controller, openTool: openTool),
                    WifiPage(controller: controller, active: index == 1),
                    SpeedPage(controller: controller),
                    ToolsPage(controller: controller, openTool: openTool),
                  ],
                ),
              ),
            ],
          ),
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: index,
          onDestinationSelected: (value) => setState(() => index = value),
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.home_outlined),
              selectedIcon: Icon(Icons.home_rounded, color: accent),
              label: '概览',
            ),
            NavigationDestination(
              icon: Icon(Icons.wifi_rounded),
              selectedIcon: Icon(Icons.wifi_rounded, color: accent),
              label: 'Wi-Fi',
            ),
            NavigationDestination(
              icon: Icon(Icons.speed_outlined),
              selectedIcon: Icon(Icons.speed_rounded, color: accent),
              label: '测速',
            ),
            NavigationDestination(
              icon: Icon(Icons.grid_view_outlined),
              selectedIcon: Icon(Icons.grid_view_rounded, color: accent),
              label: '工具',
            ),
          ],
        ),
      ),
    );
  }
}

class HistoryPage extends StatelessWidget {
  const HistoryPage({super.key, required this.controller});
  final AppController controller;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) => Scaffold(
      appBar: AppBar(
        title: const Text('本机历史'),
        actions: [
          IconButton(
            tooltip: '清空历史',
            icon: const Icon(Icons.delete_outline),
            onPressed: controller.history.isEmpty
                ? null
                : () async {
                    final clear = await showDialog<bool>(
                      context: context,
                      builder: (context) => AlertDialog(
                        title: const Text('清空本机历史？'),
                        content: const Text('不会修改系统网络配置，也不会删除未完成的恢复记录。'),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(context, false),
                            child: const Text('取消'),
                          ),
                          TextButton(
                            onPressed: () => Navigator.pop(context, true),
                            child: const Text('清空'),
                          ),
                        ],
                      ),
                    );
                    if (clear == true) await controller.clearHistory();
                  },
          ),
        ],
      ),
      body: controller.history.isEmpty
          ? const Center(
              child: Text('完成诊断后，摘要会保存在这里', style: TextStyle(color: muted)),
            )
          : PageBody(
              children: [
                for (final item in controller.history)
                  Panel(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                toolName(item['kind'] as String),
                                style: const TextStyle(
                                  fontSize: 17,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                            StatusBadge(
                              stateName(item['state'] as String),
                              color: item['state'] == 'failed'
                                  ? Colors.orange
                                  : accent,
                            ),
                          ],
                        ),
                        const SizedBox(height: 7),
                        Text(
                          '${item['network']} · ${item['started'].toString().replaceFirst('T', ' ').split('.').first}',
                          style: const TextStyle(fontSize: 12, color: muted),
                        ),
                        if (item['parameters'] is Map &&
                            (item['parameters'] as Map)['host'] != null)
                          ValueRow(
                            '目标',
                            '${(item['parameters'] as Map)['host']}',
                          ),
                        if (item['parameters'] is Map &&
                            (item['parameters'] as Map)['name'] != null)
                          ValueRow(
                            '域名',
                            '${(item['parameters'] as Map)['name']}',
                          ),
                        if (item['error'] != null)
                          Text(
                            '${item['error']}',
                            style: const TextStyle(fontSize: 12, color: muted),
                          ),
                        if (const {
                          'ping',
                          'trace',
                          'tcping',
                          'telnet',
                          'iperf',
                          'dns',
                        }.contains(item['kind']))
                          Align(
                            alignment: Alignment.centerRight,
                            child: TextButton(
                              onPressed: () => Navigator.push(
                                context,
                                MaterialPageRoute<void>(
                                  builder: (_) => ToolPage(
                                    controller: controller,
                                    kind: item['kind'] as String,
                                    initial: Map<String, dynamic>.from(
                                      item['parameters'] as Map,
                                    ),
                                  ),
                                ),
                              ),
                              child: const Text('带入参数再测'),
                            ),
                          ),
                      ],
                    ),
                  ),
              ],
            ),
    ),
  );
}
