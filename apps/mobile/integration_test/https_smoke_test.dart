import 'dart:async';
import 'dart:convert';
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
  testWidgets('HTTPS, Worker and concurrent requests obey proxy stop', (
    tester,
  ) async {
    final client = HttpClient();
    Future<Map<String, dynamic>> counters() async {
      final request = await client.getUrl(Uri.parse('http://127.0.0.1:18892/'));
      final response = await request.close();
      return jsonDecode(await utf8.decoder.bind(response).join())
          as Map<String, dynamic>;
    }

    await tester.pumpWidget(const ProviderScope(child: BroadbandApp()));
    await tester.pumpAndSettle();
    final controller = ProviderScope.containerOf(
      tester.element(find.byType(HomeShell)),
    ).read(appControllerProvider);
    await controller.refresh();
    final run = await controller.start('webspeed', {
      'provider': 'test',
      'url': 'https://127.0.0.1:19443/',
    });
    final web = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted);
    Future<void> until(bool Function() check, int seconds) async {
      final deadline = DateTime.now().add(Duration(seconds: seconds));
      while (!check() && DateTime.now().isBefore(deadline)) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(check(), true, reason: '${run.state}: ${run.error}');
    }

    try {
      await until(
        () => run.events.any((e) => e.state == 'webReady') || run.finished,
        8,
      );
      expect(run.finished, false, reason: run.error);
      unawaited(
        Navigator.of(tester.element(find.byType(HomeShell))).push<void>(
          MaterialPageRoute(
            builder: (_) => Scaffold(body: WebViewWidget(controller: web)),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await web.loadRequest(Uri.parse('https://127.0.0.1:19443/'));
      var observed = await counters();
      final deadline = DateTime.now().add(const Duration(seconds: 15));
      while ((observed['tick'] as int) < 9 &&
          DateTime.now().isBefore(deadline)) {
        await tester.pump(const Duration(milliseconds: 200));
        observed = await counters();
      }
      expect(observed['worker'], greaterThan(0));
      expect(observed['tick'], greaterThanOrEqualTo(9));
      expect(await web.getTitle(), 'Worker ready');
      final connections = run.events
          .where((e) => e.state == 'webConnection')
          .toList();
      expect(connections.length, greaterThan(3));
      expect(
        connections.every(
          (e) =>
              e.data['destinationIPv4'] == '127.0.0.1' &&
              e.data['networkId'] == controller.selectedNetwork!.id,
        ),
        true,
      );
      await controller.stop(run);
      await until(() => run.finished, 5);
      await tester.pump(const Duration(milliseconds: 700));
      final stopped = await counters();
      await tester.pump(const Duration(seconds: 2));
      expect(
        await counters(),
        stopped,
        reason: 'Page and Worker must not escape a stopped proxy.',
      );
      debugPrint(
        'HTTPS certificate validation, Worker, concurrent IPv4 proxy connections and no direct fallback passed: $stopped',
      );
      await controller.pendingPersistence;
    } finally {
      if (!run.finished) await controller.stop(run);
      await web.loadRequest(Uri.parse('about:blank'));
      client.close(force: true);
    }
  });
}
