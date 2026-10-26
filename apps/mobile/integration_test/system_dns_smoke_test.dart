import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/main.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'ordinary_smoke_test.dart' show DnsFixture;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'Shizuku clears the selected system network without clearing app cache',
    (tester) async {
      await tester.pumpWidget(const ProviderScope(child: BroadbandApp()));
      await tester.pumpAndSettle();
      final controller = ProviderScope.containerOf(
        tester.element(find.byType(HomeShell)),
      ).read(appControllerProvider);
      await controller.refresh();
      final access = controller.requestAdvancedAccess();
      for (
        var i = 0;
        i < 600 && controller.snapshot?.privilege.status != 'root_ready';
        i++
      ) {
        await tester.pump(const Duration(milliseconds: 100));
        if (i % 10 == 0) await controller.refresh();
      }
      await access;
      expect(controller.snapshot?.privilege.serverUid, 0);
      expect(controller.snapshot?.privilege.workerUid, 0);
      final capability = controller.snapshot!.privilege.capabilities.firstWhere(
        (item) => item.name == 'systemDns',
      );
      expect(capability.supported, true, reason: capability.reason);
      final network = controller.selectedNetwork!;

      Future<ToolRun> execute(
        String kind,
        Map<String, dynamic> parameters,
      ) async {
        final run = await controller.start(kind, parameters);
        final end = DateTime.now().add(const Duration(seconds: 10));
        while (!run.finished && DateTime.now().isBefore(end)) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        if (!run.finished) await controller.stop(run);
        expect(
          run.state,
          'completed',
          reason: '${run.errorCode}: ${run.error}',
        );
        return run;
      }

      final fixture = await DnsFixture.start();
      try {
        await execute('clearAppDns', {});
        final parameters = <String, dynamic>{
          'name': 'application-cache.example.test',
          'server': '127.0.0.1',
          'port': fixture.port,
          'type': 'A',
          'transport': 'udp',
          'timeoutMs': 1000,
          'retries': 0,
          'bypassCache': false,
        };
        await execute('dns', parameters);
        final before = fixture.udpRequests;
        expect((await execute('dns', parameters)).result['fromCache'], true);
        // The platform layer must replace a caller-provided netId with the actual
        // selected Network after checking its generation.
        final clear = await execute('root.systemDns', {'netId': 65534});
        expect(clear.result['accepted'], true);
        expect(clear.result['scope'], 'system_network');
        // NetworkData.id is an opaque Android network handle, not the resolver's netId.
        expect(clear.networkSnapshot['id'], network.id);
        expect(clear.result['netId'], inInclusiveRange(1, 65535));
        expect(clear.result['netId'], isNot(65534));
        expect(clear.result['resolverVersion'], greaterThanOrEqualTo(10));
        expect((await execute('dns', parameters)).result['fromCache'], true);
        expect(fixture.udpRequests, before);
        await controller.refresh();
        expect(controller.selectedNetwork!.id, network.id);
        expect(controller.selectedNetwork!.generation, network.generation);
        await execute('clearAppDns', {});
        await execute('dns', parameters);
        expect(fixture.udpRequests, before + 1);
        await controller.pendingPersistence;
        debugPrint(
          'Real Shizuku Root system flush passed; selected network enforced, app cache unchanged.',
        );
      } finally {
        await fixture.close();
      }
    },
    timeout: const Timeout(Duration(minutes: 2)),
  );
}
