import 'dart:io';

import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/main.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'Shizuku root capture and link diagnostics use the selected interface',
    (tester) async {
      await tester.pumpWidget(const ProviderScope(child: BroadbandApp()));
      await tester.pumpAndSettle();
      final controller = ProviderScope.containerOf(
        tester.element(find.byType(HomeShell)),
      ).read(appControllerProvider);
      await controller.refresh();
      debugPrint(
        'Requesting Shizuku access; approve the app in the emulator if prompted.',
      );
      final authorization = controller.requestAdvancedAccess();
      for (
        var i = 0;
        i < 900 && controller.snapshot?.privilege.status != 'root_ready';
        i++
      ) {
        await tester.pump(const Duration(milliseconds: 100));
        if (i % 10 == 0) await controller.refresh();
      }
      await authorization;
      expect(controller.snapshot?.privilege.serverUid, 0);
      expect(
        controller.snapshot?.privilege.workerUid,
        0,
        reason: controller.snapshot?.privilege.message,
      );
      final interfaces = controller.snapshot!.privilege.interfaces;
      final selected = interfaces
          .where(
            (item) => item.name == controller.selectedNetwork?.interfaceName,
          )
          .first;
      expect(selected.id, isNotEmpty);
      expect(
        controller.snapshot!.privilege.capabilities
            .firstWhere((item) => item.name == 'capture')
            .supported,
        true,
      );
      Future<void> finish(ToolRun run, {int seconds = 20}) async {
        final end = DateTime.now().add(Duration(seconds: seconds));
        while (!run.finished && DateTime.now().isBefore(end)) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(
          run.state,
          'completed',
          reason: '${run.errorCode}: ${run.error}',
        );
      }

      try {
        final kernel = await controller.start('root.interfaceInfo', {}, interface: selected);
        await finish(kernel);
        expect(kernel.result['routes'], isA<List>());
        expect((kernel.result['routes'] as List), isNotEmpty);
        final capture = await controller.start('root.capture', {
          'duration': 10,
          'maxMb': 5,
          'filter': 'ipv4_arp',
        }, interface: selected);
        for (
          var i = 0;
          i < 50 && capture.state != 'running' && !capture.finished;
          i++
        ) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(capture.state, 'running', reason: capture.error);
        final ping = await controller.start('ping', {
          'host': controller.selectedNetwork!.gateway!,
          'count': 2,
          'intervalMs': 200,
          'timeoutMs': 1000,
        });
        await finish(ping);
        final arp = await controller.start('root.arp', {
          'host': controller.selectedNetwork!.gateway!,
          'mode': 'gateway',
        }, interface: selected);
        await finish(arp);
        expect(arp.result['responses'], isA<List>());
        await finish(capture);
        expect(capture.result['packets'] as num, greaterThan(0));
        expect(capture.result['fileComplete'], true);
        final directory = await controller.host.getSupportDirectory();
        final bytes = await File('$directory/captures/${capture.id}.pcap')
            .readAsBytes();
        final header = ByteData.sublistView(bytes);
        expect(header.getUint32(0, Endian.little), 0xa1b2c3d4);
        expect(header.getUint32(20, Endian.little), 1);
        var offset = 24;
        var count = 0;
        while (offset < bytes.length) {
          expect(offset + 16, lessThanOrEqualTo(bytes.length));
          final length = header.getUint32(offset + 8, Endian.little);
          final original = header.getUint32(offset + 12, Endian.little);
          expect(length, lessThanOrEqualTo(256));
          expect(length, lessThanOrEqualTo(original));
          offset += 16 + length;
          expect(offset, lessThanOrEqualTo(bytes.length));
          count++;
        }
        expect(count, capture.result['packets']);
        final passive = await controller.start('root.dhcp', {
          'mode': 'observe',
          'duration': 5,
        }, interface: selected);
        await finish(passive);
        expect(passive.result['mode'], 'observe');
        final stopped = await controller.start('root.capture', {
          'duration': 60,
          'maxMb': 5,
        }, interface: selected);
        for (var i = 0; i < 30 && stopped.state != 'running'; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        await controller.stop(stopped);
        for (var i = 0; i < 50 && !stopped.finished; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(stopped.state, 'cancelled');
        expect(stopped.result['fileComplete'], true);
        final files = await controller.host.listCaptures();
        expect(
          files.any((file) => file.id == capture.id && file.complete),
          true,
        );
        await controller.host.deleteCapture(stopped.id);
        expect(
          (await controller.host.listCaptures()).any(
            (file) => file.id == stopped.id,
          ),
          false,
        );
        await controller.pendingPersistence;
        debugPrint(
          'Root capture, real ARP, passive DHCP, ordinary concurrency, PCAP records, stop and file cleanup passed.',
        );
      } finally {
        for (final run in controller.runs.values.where(
          (run) => !run.finished,
        )) {
          await controller.stop(run);
        }
      }
    },
    timeout: const Timeout(Duration(minutes: 3)),
  );
}
