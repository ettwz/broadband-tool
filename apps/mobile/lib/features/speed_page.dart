import 'dart:async';

import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../app/controller.dart';
import '../design/components.dart';
import 'tool_page.dart';

class SpeedPage extends StatefulWidget {
  const SpeedPage({super.key, required this.controller});
  final AppController controller;
  @override
  State<SpeedPage> createState() => _SpeedPageState();
}

class _SpeedPageState extends State<SpeedPage> {
  String area = 'lan';
  @override
  Widget build(BuildContext context) => PageBody(
    children: [
      NetworkCard(
        controller: widget.controller,
        onSelect: () => chooseNetwork(context, widget.controller),
      ),
      SegmentedButton<String>(
        showSelectedIcon: false,
        segments: const [
          ButtonSegment(value: 'lan', label: Text('局域网')),
          ButtonSegment(value: 'internet', label: Text('公网')),
        ],
        selected: {area},
        onSelectionChanged: (value) => setState(() => area = value.first),
      ),
      if (area == 'lan') ...[
        Panel(
          title: 'iperf3 局域网测速',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                '测量本机与另一台设备之间的实际吞吐。',
                style: TextStyle(color: muted, height: 1.5),
              ),
              const SizedBox(height: 18),
              const Row(
                children: [
                  Icon(Icons.devices_rounded, color: accent, size: 40),
                  SizedBox(width: 20),
                  Icon(Icons.swap_horiz_rounded, color: muted),
                  SizedBox(width: 20),
                  Icon(Icons.router_rounded, color: accent, size: 40),
                ],
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => ToolPage(
                      controller: widget.controller,
                      kind: 'iperf',
                      initial: const {'role': 'client'},
                    ),
                  ),
                ),
                child: const Text('作为客户端测速'),
              ),
              const SizedBox(height: 10),
              OutlinedButton(
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => ToolPage(
                      controller: widget.controller,
                      kind: 'iperf',
                      initial: const {'role': 'server'},
                    ),
                  ),
                ),
                child: const Text('启动本机服务端'),
              ),
            ],
          ),
        ),
        const Notice('支持 TCP、UDP 和反向接收。服务端需要保持 App 在前台；另一端也需运行 iperf3。'),
      ] else ...[
        for (final service in [
          ('ustc', '国内', '中国科学技术大学', 'test.ustc.edu.cn', '测试到中科大站点的连接速度'),
          ('fast', '国际服务', 'Netflix FAST', 'fast.com', '测试到 Netflix 节点的连接速度'),
        ])
          Panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Align(
                  alignment: Alignment.centerLeft,
                  child: StatusBadge(
                    service.$2,
                    color: service.$1 == 'ustc' ? accent : Colors.teal,
                  ),
                ),
                const SizedBox(height: 14),
                Text(
                  service.$3,
                  style: const TextStyle(
                    fontSize: 23,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                Text(service.$5, style: const TextStyle(color: muted)),
                const SizedBox(height: 5),
                Text(
                  service.$4,
                  style: const TextStyle(color: muted, fontSize: 12),
                ),
                const SizedBox(height: 18),
                FilledButton(
                  onPressed: () async {
                    final proceed = await showDialog<bool>(
                      context: context,
                      builder: (context) => AlertDialog(
                        title: Text('打开${service.$3}测速？'),
                        content: const Text(
                          '测速会产生较大流量，网站可能自动开始。使用当前选择的网络，关闭页面会停止连接。网页会话最长 3 分钟。',
                        ),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(context, false),
                            child: const Text('取消'),
                          ),
                          TextButton(
                            onPressed: () => Navigator.pop(context, true),
                            child: const Text('打开测速'),
                          ),
                        ],
                      ),
                    );
                    if (proceed == true && context.mounted) {
                      Navigator.push(
                        context,
                        MaterialPageRoute<void>(
                          builder: (_) => WebSpeedPage(
                            controller: widget.controller,
                            provider: service.$1,
                            title: service.$3,
                          ),
                        ),
                      );
                    }
                  },
                  child: const Text('打开测速'),
                ),
              ],
            ),
          ),
        const Notice('Netflix 节点不一定在国外。测速结果保留在官方页面，不将网页内容冒充统一测量指标。'),
      ],
    ],
  );
}

class WebSpeedPage extends StatefulWidget {
  const WebSpeedPage({
    super.key,
    required this.controller,
    required this.provider,
    required this.title,
  });
  final AppController controller;
  final String provider;
  final String title;
  @override
  State<WebSpeedPage> createState() => _WebSpeedPageState();
}

class _WebSpeedPageState extends State<WebSpeedPage> {
  ToolRun? run;
  WebViewController? web;
  String? error;
  int progress = 0;
  bool loaded = false;
  bool allowPop = false;
  bool pageStopped = false;
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(update);
    unawaited(begin());
  }

  Future<void> begin() async {
    try {
      await widget.controller.start('webspeed', {
        'provider': widget.provider,
      }, onCreated: (value) => run = value);
      update();
    } on Exception catch (exception) {
      if (mounted) setState(() => error = readableError(exception));
    }
  }

  void update() {
    if (!mounted) return;
    if (run?.finished == true) {
      unawaited(stopPage());
      setState(() {
        loaded = false;
        error = run!.error ?? run!.result['message'] as String? ?? '测速会话已关闭';
      });
      return;
    }
    final ready = run?.events
        .where((event) => event.state == 'webReady')
        .lastOrNull;
    if (ready != null && !loaded) {
      loaded = true;
      web = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setNavigationDelegate(
          NavigationDelegate(
            onProgress: (value) {
              if (mounted) setState(() => progress = value);
            },
            onNavigationRequest: (request) =>
                Uri.tryParse(request.url)?.scheme == 'https' ||
                    request.url == 'about:blank'
                ? NavigationDecision.navigate
                : NavigationDecision.prevent,
            onWebResourceError: (failure) {
              if (failure.isForMainFrame == true && mounted) {
                setState(() => error = '网页加载失败：${failure.description}');
              }
            },
          ),
        )
        ..loadRequest(Uri.parse(ready.data['url'] as String));
      setState(() {});
    }
  }

  Future<void> stopPage() async {
    if (pageStopped || web == null) return;
    pageStopped = true;
    try {
      await web!.runJavaScript('window.stop();');
      await web!.loadRequest(Uri.parse('about:blank'));
    } on Exception {
      // Native proxy shutdown is independent of renderer responsiveness.
    }
  }

  Future<void> leave() async {
    await stopPage();
    if (run != null && !run!.finished) await widget.controller.stop(run!);
    if (!mounted) return;
    setState(() => allowPop = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.pop(context);
    });
  }

  @override
  void dispose() {
    widget.controller.removeListener(update);
    unawaited(stopPage());
    if (run != null && !run!.finished) unawaited(widget.controller.stop(run!));
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: allowPop,
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop) unawaited(leave());
    },
    child: Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        actions: [TextButton(onPressed: leave, child: const Text('停止并关闭'))],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                const Icon(Icons.route_outlined, size: 18, color: accent),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '${run?.networkName ?? '当前网络'} · IPv4 转发',
                    style: const TextStyle(fontSize: 12, color: muted),
                  ),
                ),
              ],
            ),
          ),
          if (run?.finished != true &&
              error == null &&
              (!loaded || progress < 100))
            LinearProgressIndicator(
              value: loaded ? progress / 100 : null,
              minHeight: 2,
            ),
          if (error != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Notice(error!, warning: true),
            ),
          if (loaded && web != null && run?.finished != true)
            Expanded(child: WebViewWidget(controller: web!))
          else
            Expanded(
              child: Center(
                child: Text(
                  error == null ? '等待测速页面就绪' : '测速页面未运行',
                  style: const TextStyle(color: muted),
                ),
              ),
            ),
        ],
      ),
    ),
  );
}
