import 'dart:async';
import 'dart:io';

import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webview_flutter/webview_flutter.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'WebView proxy carries IPv4 traffic and cuts off a still-running page',
    (tester) async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      var ticks = 0;
      var mainRequests = 0;
      final subscription = server.listen((request) async {
        request.response.headers.set('Cache-Control', 'no-store');
        if (request.uri.path == '/tick') {
          ticks++;
          request.response.write('ok');
        } else {
          mainRequests++;
          request.response.headers.contentType = ContentType.html;
          request.response.write(
            '<!doctype html><title>IPv4 fixture</title><p>Bound page ready</p><script>setInterval(()=>fetch("/tick?t="+Date.now()).catch(()=>{}),150);</script>',
          );
        }
        await request.response.close();
      });
      await tester.pumpWidget(const ProviderScope(child: BroadbandApp()));
      await tester.pumpAndSettle();
      final controller = ProviderScope.containerOf(
        tester.element(find.byType(HomeShell)),
      ).read(appControllerProvider);
      await controller.refresh();
      final run = await controller.start('webspeed', {
        'provider': 'test',
        'url': 'http://127.0.0.1:${server.port}/',
      });
      Future<void> pumpUntil(bool Function() condition, int seconds) async {
        final end = DateTime.now().add(Duration(seconds: seconds));
        while (!condition() && DateTime.now().isBefore(end)) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(condition(), true, reason: '${run.state}: ${run.error}');
      }

      final web = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted);
      try {
        await pumpUntil(
          () =>
              run.events.any((event) => event.state == 'webReady') ||
              run.finished,
          8,
        );
        expect(run.finished, false, reason: run.error);
        final context = tester.element(find.byType(HomeShell));
        unawaited(
          Navigator.of(context).push<void>(
            MaterialPageRoute(
              builder: (_) => Scaffold(
                appBar: AppBar(title: const Text('Proxy verification')),
                body: WebViewWidget(controller: web),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await web.loadRequest(Uri.parse('http://127.0.0.1:${server.port}/'));
        await pumpUntil(() => ticks >= 3, 10);
        expect(mainRequests, greaterThan(0));
        expect(
          await web.runJavaScriptReturningResult('document.body.innerText'),
          contains('Bound page ready'),
        );
        final connections = run.events
            .where((event) => event.state == 'webConnection')
            .toList();
        expect(connections, isNotEmpty);
        expect(
          connections.every(
            (event) => event.data['destinationIPv4'] == '127.0.0.1',
          ),
          true,
        );
        expect(
          connections.every(
            (event) =>
                event.data['networkId'] == controller.selectedNetwork!.id,
          ),
          true,
        );
        await controller.stop(run);
        await pumpUntil(() => run.finished, 5);
        await tester.pump(const Duration(milliseconds: 500));
        final afterStop = ticks;
        await tester.pump(const Duration(seconds: 2));
        expect(
          ticks,
          afterStop,
          reason:
              'A stopped proxy must not fall back to direct network traffic.',
        );
        await web.loadRequest(Uri.parse('about:blank'));
        debugPrint('Real WebView HTTP traffic and native stop cutoff passed.');
        await controller.pendingPersistence;
      } finally {
        if (!run.finished) await controller.stop(run);
        await web.loadRequest(Uri.parse('about:blank'));
        await subscription.cancel();
        await server.close(force: true);
      }
    },
  );
}
