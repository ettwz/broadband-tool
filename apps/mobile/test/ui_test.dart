import 'dart:io';

import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/fake_host.dart';

void main() {
  testWidgets(
    'overview and tools remain usable on a narrow screen with large text',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = 1.8;
      final directory = Directory.systemTemp.createTempSync(
        'broadband-ui-test-',
      );
      final controller = AppController(host: FakeHost(directory));
      controller.onSnapshot(fixtureSnapshot());
      addTearDown(() {
        controller.dispose();
        directory.deleteSync(recursive: true);
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
        tester.platformDispatcher.clearTextScaleFactorTestValue();
      });
      await tester.pumpWidget(
        ProviderScope(
          overrides: [appControllerProvider.overrideWithValue(controller)],
          child: const BroadbandApp(),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('网络概览'), findsOneWidget);
      expect(find.text('192.0.2.23'), findsWidgets);
      expect(tester.takeException(), isNull);
      await tester.tap(find.byIcon(Icons.grid_view_outlined));
      await tester.pumpAndSettle();
      expect(find.text('网络工具'), findsOneWidget);
      expect(find.text('Ping'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.tap(find.text('Ping'));
      await tester.pumpAndSettle();
      expect(find.text('目标 IPv4 或域名'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
