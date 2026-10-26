import 'dart:convert';
import 'dart:io';

import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/platform/network_api.g.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/fake_host.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory directory;
  late FakeHost host;
  late AppController controller;
  setUp(() async {
    directory = await Directory.systemTemp.createTemp(
      'broadband-controller-test-',
    );
    host = FakeHost(directory);
    controller = AppController(host: host);
    await controller.initialize();
  });
  tearDown(() async {
    await controller.pendingPersistence;
    controller.dispose();
    await directory.delete(recursive: true);
  });

  TaskEventData event(
    ToolRun run,
    int sequence,
    String state,
    Map<String, dynamic> data,
  ) => TaskEventData(
    taskId: run.id,
    kind: run.kind,
    state: state,
    sequence: sequence,
    elapsedMs: 10,
    dataJson: jsonEncode(data),
  );

  test(
    'duplicate and out-of-order events do not overwrite a final result',
    () async {
      final run = await controller.start('ping', {'host': '192.0.2.1'});
      controller.onTaskEvent(
        event(run, 2, 'result', {'received': 1, 'sent': 1}),
      );
      controller.onTaskEvent(event(run, 1, 'result', {'received': 99}));
      controller.onTaskEvent(event(run, 3, 'completed', {}));
      controller.onTaskEvent(event(run, 4, 'failed', {}));
      expect(run.result['received'], 1);
      expect(run.state, 'completed');
      expect(controller.history.length, 1);
      await controller.pendingPersistence;
      final saved = jsonDecode(
        await File('${directory.path}/history.json').readAsString(),
      ) as Map;
      expect((saved['records'] as List).single['result']['received'], 1);
    },
  );

  test('terminal bytes never enter the stored report', () async {
    final run = await controller.start('telnet', {
      'host': '192.0.2.10',
      'port': 23,
    });
    final payload = base64Encode(utf8.encode('TEST_ONLY_PRIVATE_TEXT'));
    controller.onTaskEvent(event(run, 1, 'terminal', {'bytes': payload}));
    controller.onTaskEvent(event(run, 2, 'completed', {}));
    await controller.pendingPersistence;
    final saved = await File('${directory.path}/history.json').readAsString();
    expect(saved, isNot(contains(payload)));
    expect(saved, isNot(contains('TEST_ONLY_PRIVATE_TEXT')));
    expect(run.events.any((value) => value.state == 'terminal'), isFalse);
  });

  test('network generation changes invalidate a public-IP result', () async {
    final run = await controller.start('publicIp', {});
    controller.onTaskEvent(event(run, 1, 'result', {'ip': '203.0.113.1'}));
    controller.onTaskEvent(event(run, 2, 'completed', {}));
    expect(controller.publicIpFresh, isTrue);
    controller.onSnapshot(fixtureSnapshot(generation: 2));
    expect(controller.publicIp, isNull);
    expect(controller.publicIpFresh, isFalse);
  });

  test(
    'malformed history is isolated and does not prevent a network snapshot',
    () async {
      controller.dispose();
      await File('${directory.path}/history.json')
          .writeAsString('{"schemaVersion":1,"records":false}');
      controller = AppController(host: host);
      await controller.initialize();
      expect(controller.selectedNetwork?.id, 'wifi-test');
      expect(controller.history, isEmpty);
      expect(
        directory.listSync().any((file) => file.path.contains('.damaged-')),
        isTrue,
      );
    },
  );
  test(
    'a persisted unfinished task is interrupted on restart without replay',
    () async {
      final run = await controller.start('ping', {'host': '192.0.2.1'});
      await controller.pendingPersistence;
      controller.dispose();
      controller = AppController(host: host);
      await controller.initialize();
      expect(controller.activeRun, isNull);
      expect(controller.history.single['id'], run.id);
      expect(controller.history.single['state'], 'interrupted');
      expect(controller.history.single['errorCode'], 'APP_EXITED');
      expect(controller.history.single['networkSnapshot']['ipv4'], isNotEmpty);
      await controller.pendingPersistence;
      final saved = jsonDecode(
        await File('${directory.path}/history.json').readAsString(),
      ) as Map;
      expect((saved['records'] as List).single['state'], 'interrupted');
    },
  );
}
