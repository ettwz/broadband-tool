import 'dart:convert';
import 'dart:io';

import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/main.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  const peer = String.fromEnvironment('PEER_IPV4');
  const udpServerOnly = bool.fromEnvironment('UDP_SERVER_ONLY');
  testWidgets('packaged iperf3 interoperates with a Linux peer in both roles', (
    tester,
  ) async {
    expect(
      peer,
      isNotEmpty,
      reason: 'Pass the controlled Linux peer IPv4 via PEER_IPV4',
    );
    await tester.pumpWidget(const ProviderScope(child: BroadbandApp()));
    await tester.pumpAndSettle();
    final controller = ProviderScope.containerOf(
      tester.element(find.byType(HomeShell)),
    ).read(appControllerProvider);
    await controller.refresh();
    expect(controller.selectedNetwork?.addresses, isNotEmpty);
    Future<void> finish(ToolRun run, {int seconds = 20}) async {
      final deadline = DateTime.now().add(Duration(seconds: seconds));
      while (!run.finished && DateTime.now().isBefore(deadline)) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      if (!run.finished) await controller.stop(run);
      expect(run.state, 'completed', reason: '${run.errorCode}: ${run.error}');
      expect(run.result['iperf'], isA<Map>());
    }

    try {
      for (final mode
          in udpServerOnly
              ? <(String, bool)>[]
              : [('tcp', false), ('tcp', true), ('udp', false)]) {
        final run = await controller.start('iperf', {
          'role': 'client',
          'host': peer,
          'port': 15201,
          'protocol': mode.$1,
          'reverse': mode.$2,
          'duration': 5,
          'streams': 1,
          'rateMbps': 10,
        });
        await finish(run);
        final end = run.result['iperf'] as Map;
        final received = (end['sum_received'] ?? end['sum']) as Map;
        expect(received['bits_per_second'] as num, greaterThan(0));
        debugPrint('iperf client ${mode.$1} reverse=${mode.$2}: passed');
      }
      for (final protocol in udpServerOnly ? ['udp'] : ['tcp', 'udp']) {
        final server = await controller.start('iperf', {
          'role': 'server',
          'port': 15202,
        });
        final deadline = DateTime.now().add(const Duration(seconds: 5));
        while (server.state != 'listening' &&
            !server.finished &&
            DateTime.now().isBefore(deadline)) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(server.state, 'listening', reason: server.error);
        final client = HttpClient()
          ..connectionTimeout = const Duration(seconds: 3);
        try {
          final request = await client.getUrl(
            Uri.parse('http://127.0.0.1:18890/client?protocol=$protocol'),
          );
          final response = await request.close().timeout(
            const Duration(seconds: 18),
          );
          final result =
              jsonDecode(await utf8.decoder.bind(response).join()) as Map;
          expect(
            result['exit'],
            0,
            reason:
                '$protocol: ${result['error']}; '
                'server=${server.state} ${server.error}',
          );
          expect(result['connected'], isNotEmpty);
        } finally {
          client.close(force: true);
        }
        await finish(server);
        debugPrint('iperf server $protocol: passed');
      }
      final waiting = await controller.start('iperf', {
        'role': 'server',
        'port': 15202,
      });
      for (
        var i = 0;
        i < 40 && waiting.state != 'listening' && !waiting.finished;
        i++
      ) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(waiting.state, 'listening');
      await controller.stop(waiting);
      for (var i = 0; i < 20 && !waiting.finished; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(waiting.state, 'cancelled');
      await controller.pendingPersistence;
    } finally {
      final active = controller.activeRun;
      if (active != null) await controller.stop(active);
    }
  }, timeout: const Timeout(Duration(minutes: 2)));
}
