import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/main.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('real Android sockets: ping, trace, TCP, DNS and Telnet', (
    tester,
  ) async {
    await tester.pumpWidget(const ProviderScope(child: BroadbandApp()));
    await tester.pumpAndSettle();
    final container = ProviderScope.containerOf(
      tester.element(find.byType(HomeShell)),
    );
    final controller = container.read(appControllerProvider);
    await controller.refresh();
    expect(controller.snapshot?.platform, 'android');
    expect(
      controller.selectedNetwork,
      isNotNull,
      reason: 'A real IPv4 Wi-Fi/Ethernet network is required',
    );
    expect(controller.selectedNetwork!.addresses, isNotEmpty);

    Future<ToolRun> execute(
      String kind,
      Map<String, dynamic> parameters, {
      int timeout = 15,
    }) async {
      final run = await controller.start(kind, parameters);
      final until = DateTime.now().add(Duration(seconds: timeout));
      while (!run.finished && DateTime.now().isBefore(until)) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      if (!run.finished) {
        await controller.stop(run);
        fail('$kind did not finish within $timeout seconds');
      }
      expect(
        run.state,
        'completed',
        reason: '$kind: ${run.errorCode} ${run.error}',
      );
      return run;
    }

    final tcp = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    final openSockets = <Socket>[];
    tcp.listen((socket) {
      openSockets.add(socket);
      socket.listen((_) {}, onError: (_) {}, onDone: socket.destroy);
    });
    final unused = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    final refusedPort = unused.port;
    await unused.close();
    final dns = await DnsFixture.start();
    final telnet = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    final typedInput = Completer<void>();
    telnet.listen((socket) {
      openSockets.add(socket);
      socket.add([255, 251, 1]);
      final greeting = utf8.encode('Welcome 网络工具\r\n');
      socket.add(greeting.sublist(0, 10));
      socket.add(greeting.sublist(10));
      final input = <int>[];
      socket.listen(
        (bytes) {
          input.addAll(bytes);
          final readable = String.fromCharCodes(input);
          if (readable.contains('hello\r\n') && !typedInput.isCompleted) {
            typedInput.complete();
            socket.add(utf8.encode('OK hello\r\n'));
          }
        },
        onError: (_) {},
        onDone: socket.destroy,
      );
    });

    try {
      final ping = await execute('ping', {
        'host': '127.0.0.1',
        'count': 2,
        'intervalMs': 200,
        'timeoutMs': 1000,
        'payloadBytes': 56,
      });
      expect(ping.result['received'], 2);
      expect(ping.result['lossPercent'], 0);
      final trace = await execute('trace', {'host': '127.0.0.1'});
      expect(trace.result['reached'], isTrue);
      expect(trace.events.where((event) => event.state == 'hop'), isNotEmpty);
      final connected = await execute('tcping', {
        'host': '127.0.0.1',
        'port': tcp.port,
        'count': 2,
        'intervalMs': 200,
        'timeoutMs': 500,
      });
      expect(connected.result['connected'], 2);
      final refused = await execute('tcping', {
        'host': '127.0.0.1',
        'port': refusedPort,
        'count': 1,
        'intervalMs': 200,
        'timeoutMs': 500,
      });
      expect(refused.result['refused'], 1);
      expect(refused.result['connected'], 0);

      final parameters = <String, dynamic>{
        'name': 'fixture.example.test',
        'server': '127.0.0.1',
        'port': dns.port,
        'type': 'A',
        'transport': 'udp',
        'timeoutMs': 1000,
        'retries': 0,
        'bypassCache': false,
      };
      final answer = await execute('dns', parameters);
      expect(answer.result['rcode'], 'NOERROR');
      expect((answer.result['answer'] as List).single['value'], '192.0.2.123');
      final requests = dns.udpRequests;
      final cached = await execute('dns', parameters);
      expect(cached.result['fromCache'], isTrue);
      expect(dns.udpRequests, requests);
      final clear = await execute('clearAppDns', {});
      expect(clear.result['cleared'], greaterThanOrEqualTo(1));
      await execute('dns', parameters);
      expect(dns.udpRequests, requests + 1);
      dns.truncateUdp = true;
      final fallback = await execute('dns', {
        ...parameters,
        'bypassCache': true,
      });
      expect(fallback.result['tcpFallback'], isTrue);
      expect(fallback.result['transport'], 'TCP');
      expect(dns.tcpRequests, 1);
      dns.truncateUdp = false;
      dns.nxdomain = true;
      final missing = await execute('dns', {
        ...parameters,
        'name': 'missing.example.test',
        'bypassCache': true,
      });
      expect(missing.result['rcode'], 'NXDOMAIN');
      expect(missing.result['answer'], isEmpty);

      final terminalBytes = <int>[];
      final terminalSubscription = controller.rawEvents.listen((event) {
        if (event.kind == 'telnet' && event.state == 'terminal') {
          terminalBytes.addAll(
            base64Decode(
              (jsonDecode(event.dataJson) as Map)['bytes'] as String,
            ),
          );
        }
      });
      final terminal = await controller.start('telnet', {
        'host': '127.0.0.1',
        'port': telnet.port,
        'timeoutMs': 1000,
      });
      for (
        var i = 0;
        i < 50 && terminal.state != 'connected' && !terminal.finished;
        i++
      ) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(terminal.state, 'connected', reason: terminal.error);
      await controller.host.sendTerminalInput(
        terminal.id,
        Uint8List.fromList(utf8.encode('hello\r')),
      );
      await typedInput.future.timeout(const Duration(seconds: 3));
      for (
        var i = 0;
        i < 30 &&
            !utf8
                .decode(terminalBytes, allowMalformed: true)
                .contains('OK hello');
        i++
      ) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(utf8.decode(terminalBytes), contains('网络工具'));
      expect(utf8.decode(terminalBytes), contains('OK hello'));
      await controller.stop(terminal);
      for (var i = 0; i < 30 && !terminal.finished; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(terminal.state, 'cancelled');
      await terminalSubscription.cancel();
      expect(
        controller.history
            .where((entry) => entry['kind'] == 'telnet')
            .first['result'],
        isEmpty,
      );

      final cancel = await controller.start('ping', {
        'host': '127.0.0.1',
        'count': 1000,
        'intervalMs': 1000,
        'timeoutMs': 1000,
      });
      await tester.pump(const Duration(milliseconds: 300));
      final stopped = DateTime.now();
      await controller.stop(cancel);
      while (!cancel.finished &&
          DateTime.now().difference(stopped) < const Duration(seconds: 3)) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(cancel.state, 'cancelled');
      expect(
        DateTime.now().difference(stopped),
        lessThan(const Duration(seconds: 3)),
      );
      await controller.pendingPersistence;
    } finally {
      final active = controller.activeRun;
      if (active != null) await controller.stop(active);
      for (final socket in openSockets) {
        socket.destroy();
      }
      await tcp.close();
      await telnet.close();
      await dns.close();
    }
  }, timeout: const Timeout(Duration(minutes: 3)));
}

class DnsFixture {
  DnsFixture(this.udp, this.tcp);
  final RawDatagramSocket udp;
  final ServerSocket tcp;
  final List<Socket> clients = [];
  bool truncateUdp = false;
  bool nxdomain = false;
  int udpRequests = 0, tcpRequests = 0;
  int get port => udp.port;

  static Future<DnsFixture> start() async {
    final udp = await RawDatagramSocket.bind(InternetAddress.loopbackIPv4, 0);
    final tcp = await ServerSocket.bind(InternetAddress.loopbackIPv4, udp.port);
    final fixture = DnsFixture(udp, tcp);
    udp.listen((event) {
      if (event != RawSocketEvent.read) return;
      Datagram? packet;
      while ((packet = udp.receive()) != null) {
        fixture.udpRequests++;
        udp.send(
          fixture.response(packet!.data, truncated: fixture.truncateUdp),
          packet.address,
          packet.port,
        );
      }
    });
    tcp.listen((socket) {
      fixture.clients.add(socket);
      final input = <int>[];
      var answered = false;
      socket.listen(
        (chunk) async {
          input.addAll(chunk);
          if (answered || input.length < 2) return;
          final size = input[0] * 256 + input[1];
          if (input.length < size + 2) return;
          answered = true;
          fixture.tcpRequests++;
          final answer = fixture.response(
            Uint8List.fromList(input.sublist(2, size + 2)),
          );
          socket.add([answer.length >> 8, answer.length & 255, ...answer]);
          await socket.flush();
          await socket.close();
        },
        onError: (_) {},
        onDone: socket.destroy,
      );
    });
    return fixture;
  }

  List<int> response(Uint8List request, {bool truncated = false}) {
    var end = 12;
    while (end < request.length && request[end] != 0) {
      end += request[end] + 1;
    }
    end += 5;
    if (request.length < 12 || end > request.length) return [];
    final hasAnswer = !truncated && !nxdomain;
    return [
      request[0],
      request[1],
      truncated ? 0x83 : 0x81,
      nxdomain ? 0x83 : 0x80,
      0,
      1,
      0,
      hasAnswer ? 1 : 0,
      0,
      0,
      0,
      0,
      ...request.sublist(12, end),
      if (hasAnswer) ...[
        0xc0,
        0x0c,
        0,
        1,
        0,
        1,
        0,
        0,
        0,
        60,
        0,
        4,
        192,
        0,
        2,
        123,
      ],
    ];
  }

  Future<void> close() async {
    udp.close();
    for (final socket in clients) {
      socket.destroy();
    }
    await tcp.close();
  }
}
