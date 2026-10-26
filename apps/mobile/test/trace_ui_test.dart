import 'dart:convert';
import 'dart:io';

import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/design/components.dart';
import 'package:broadband_tool/features/tool_page.dart';
import 'package:broadband_tool/platform/network_api.g.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/fake_host.dart';

void main() {
  testWidgets(
    'trace accepts a hostname and retains its resolved IPv4 when stopped',
    (tester) async {
      final directory = Directory.systemTemp.createTempSync(
        'broadband-trace-ui-',
      );
      final host = FakeHost(directory);
      final controller = AppController(host: host)
        ..onSnapshot(fixtureSnapshot());
      addTearDown(() {
        controller.dispose();
        directory.deleteSync(recursive: true);
      });
      await tester.pumpWidget(
        MaterialApp(
          theme: toolTheme(Brightness.light),
          home: ToolPage(controller: controller, kind: 'trace'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('目标 IPv4 或域名'), findsOneWidget);
      await tester.enterText(find.byType(TextFormField), 'example.com');
      await tester.tap(find.text('开始追踪'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(host.requests.single.kind, 'trace');
      expect(
        (jsonDecode(host.requests.single.parametersJson) as Map)['host'],
        'example.com',
      );
      final run = controller.runs.values.single;
      controller.onTaskEvent(
        TaskEventData(
          taskId: run.id,
          kind: 'trace',
          state: 'target',
          sequence: 1,
          elapsedMs: 10,
          dataJson: jsonEncode({
            'host': 'example.com',
            'target': '192.0.2.123',
          }),
        ),
      );
      controller.onTaskEvent(
        TaskEventData(
          taskId: run.id,
          kind: 'trace',
          state: 'cancelled',
          sequence: 2,
          elapsedMs: 20,
          dataJson: '{}',
        ),
      );
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('追踪 IPv4'),
        200,
        scrollable: find
            .descendant(
              of: find.byType(ListView),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      expect(find.text('192.0.2.123'), findsOneWidget);
      final evidence = controller.history.single['evidence'] as List;
      expect((evidence.single as Map)['state'], 'target');
      expect((evidence.single['data'] as Map)['target'], '192.0.2.123');
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
