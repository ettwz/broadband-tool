import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/main.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'trace resolves a domain on the selected network then probes and stops',
    (tester) async {
      await tester.pumpWidget(const ProviderScope(child: BroadbandApp()));
      await tester.pumpAndSettle();
      final controller = ProviderScope.containerOf(
        tester.element(find.byType(HomeShell)),
      ).read(appControllerProvider);
      await controller.refresh();
      final selected = controller.selectedNetwork!;
      final domain = await controller.start('trace', {'host': 'example.com'});
      Future<void> until(bool Function() condition, int seconds) async {
        final deadline = DateTime.now().add(Duration(seconds: seconds));
        while (!condition() && DateTime.now().isBefore(deadline)) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(condition(), true, reason: '${domain.state}: ${domain.error}');
      }

      try {
        await until(
          () => domain.finished || domain.events.any((e) => e.state == 'hop'),
          10,
        );
        final target = domain.events
            .where((e) => e.state == 'target')
            .single
            .data;
        expect(target['host'], 'example.com');
        expect(target['networkId'], selected.id);
        final address = target['target'] as String;
        expect(address.split('.').length, 4);
        expect(
          address
              .split('.')
              .map(int.tryParse)
              .every((value) => value != null && value >= 0 && value <= 255),
          true,
        );
        expect(domain.events.where((e) => e.state == 'hop'), isNotEmpty);
        if (!domain.finished) await controller.stop(domain);
        await until(() => domain.finished, 4);
        expect(domain.state, isIn(['completed', 'cancelled']));
        final ip = await controller.start('trace', {'host': '127.0.0.1'});
        await until(() => ip.finished, 5);
        expect(ip.state, 'completed', reason: ip.error);
        expect(ip.result['reached'], true);
        expect(ip.result['target'], '127.0.0.1');
        await controller.pendingPersistence;
        debugPrint(
        'Domain trace resolved an IPv4 on the selected network, emitted hops and stopped; literal IPv4 still reached loopback.',
        );
      } finally {
        for (final run
            in controller.runs.values.where((run) => !run.finished).toList()) {
          await controller.stop(run);
        }
      }
    },
  );
}
