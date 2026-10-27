import 'dart:io';
import 'dart:ui' as ui;

import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/features/overview_page.dart';
import 'package:broadband_tool/features/wifi_page.dart';
import 'package:broadband_tool/main.dart';
import 'package:broadband_tool/platform/network_api.g.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'channel_zoom_test.dart' show denseScan;
import 'support/fake_host.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    const font = String.fromEnvironment('RELEASE_PREVIEW_FONT');
    if (font.isEmpty) return;
    final bytes = ByteData.sublistView(await File(font).readAsBytes());
    for (final family in ['Roboto', 'Ahem']) {
      await (FontLoader(family)..addFont(Future.value(bytes))).load();
    }
    await (FontLoader(
      'MaterialIcons',
    )..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  late Directory directory;
  late FakeHost host;
  late AppController controller;

  setUp(() {
    directory = Directory.systemTemp.createTempSync('broadband-release-fixes-');
    host = FakeHost(directory);
    controller = AppController(host: host);
    controller.onSnapshot(host.state);
  });
  tearDown(() {
    controller.dispose();
    directory.deleteSync(recursive: true);
  });

  Future<void> launch(
    WidgetTester tester,
    Size size, {
    double scale = 1,
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    tester.platformDispatcher.textScaleFactorTestValue = scale;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
      tester.platformDispatcher.clearTextScaleFactorTestValue();
    });
    await tester.pumpWidget(
      RepaintBoundary(
        key: const ValueKey('release-preview'),
        child: ProviderScope(
          overrides: [appControllerProvider.overrideWithValue(controller)],
          child: const BroadbandApp(),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('About reads the package version on every opening', (
    tester,
  ) async {
    await launch(tester, const Size(390, 844));
    for (final version in ['9.8.7', '10.0.0']) {
      host.appVersion = version;
      await tester.tap(find.byTooltip('关于与许可'));
      await tester.pumpAndSettle();
      expect(find.text(version), findsOneWidget);
      await tester.tap(find.text('关闭'));
      await tester.pumpAndSettle();
    }
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final scenario in [
    (size: const Size(320, 480), scale: 1.0, crowded: false),
    (size: const Size(320, 568), scale: 1.5, crowded: true),
    (size: const Size(360, 640), scale: 2.0, crowded: true),
  ]) {
    testWidgets(
      'scan stays visible before and after scrolling at ${scenario.size}, text ${scenario.scale}',
      (tester) async {
        if (scenario.crowded) host.state.accessPoints = denseScan();
        await launch(tester, scenario.size, scale: scenario.scale);
        await tester.tap(find.text('Wi-Fi').last);
        await tester.pumpAndSettle();
        final button = find.byKey(const ValueKey('wifi-scan-toggle'));
        final before = tester.getRect(button);
        expect(button.hitTestable(), findsOneWidget);
        expect(before.top, lessThan(140));
        expect(before.bottom, lessThan(scenario.size.height / 2));
        await preview(
          tester,
          'wifi-${scenario.size.width.toInt()}-${scenario.scale}',
        );
        await tester.tap(button);
        await tester.pumpAndSettle();
        expect(host.scanRequests, 1);
        expect(find.text('暂停扫描'), findsOneWidget);
        final list = find.descendant(
          of: find.byType(WifiPage),
          matching: find.byType(ListView),
        );
        await tester.drag(list, const Offset(0, -800));
        await tester.pumpAndSettle();
        expect(button.hitTestable(), findsOneWidget);
        expect(tester.getRect(button), before);
        await tester.tap(button);
        await tester.pumpAndSettle();
        expect(controller.scanPaused, isTrue);
        expect(find.text('开始扫描'), findsOneWidget);
        final error = tester.takeException();
        expect(
          error,
          isNull,
          reason: error is FlutterError ? error.toStringDeep() : null,
        );
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }

  testWidgets('denied scan remains stopped and can be retried', (tester) async {
    host.state.permissions.fineLocation = false;
    host.scanError = PlatformException(
      code: 'PERMISSION_DENIED',
      message: '扫描 Wi-Fi 需要精确定位权限',
    );
    await launch(tester, const Size(320, 568));
    await tester.tap(find.text('Wi-Fi').last);
    await tester.pumpAndSettle();
    final button = find.byKey(const ValueKey('wifi-scan-toggle'));
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(controller.scanPaused, isTrue);
    expect(find.text('开始扫描'), findsOneWidget);
    expect(find.textContaining('需要精确定位权限'), findsWidgets);
    host.scanError = null;
    host.state.permissions.fineLocation = true;
    controller.onSnapshot(host.state);
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(host.scanRequests, 2);
    expect(find.text('暂停扫描'), findsOneWidget);
    controller.pauseScan();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final count in [0, 1, 2]) {
    testWidgets(
      'overview renders exactly $count supplied data networks, including IPv6-only',
      (tester) async {
        for (var i = 0; i < count; i++) {
          host.state.networks.add(
            NetworkData(
              id: 'cell-$i',
              generation: 1,
              transport: 'cellular',
              interfaceName: 'rmnet$i',
              displayName: '蜂窝网络',
              addresses: i == 0 ? [] : ['192.0.2.30'],
              prefixes: i == 0 ? [] : [24],
              dnsServers: [],
              isDefault: false,
              validated: false,
              isVpn: false,
            ),
          );
        }
        await launch(tester, const Size(390, 844));
        final list = find.descendant(
          of: find.byType(OverviewPage),
          matching: find.byWidgetPredicate(
            (widget) =>
                widget is Scrollable &&
                widget.axisDirection == AxisDirection.down,
          ),
        );
        await tester.scrollUntilVisible(
          find.text('可用网络'),
          300,
          scrollable: list.first,
        );
        await tester.pumpAndSettle();
        expect(find.text('蜂窝网络'), findsNWidgets(count));
        if (count > 0) {
          expect(find.textContaining('rmnet0 · 无 IPv4'), findsOneWidget);
        }
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }
}

Future<void> preview(WidgetTester tester, String name) async {
  const directory = String.fromEnvironment('RELEASE_PREVIEW_DIR');
  if (directory.isEmpty) return;
  final boundary = tester.renderObject<RenderRepaintBoundary>(
    find.byKey(const ValueKey('release-preview')),
  );
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 2);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    await Directory(directory).create(recursive: true);
    await File('$directory/$name.png').writeAsBytes(data!.buffer.asUint8List());
    image.dispose();
  });
}
