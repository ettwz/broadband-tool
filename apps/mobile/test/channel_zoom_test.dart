import 'dart:io';
import 'dart:ui' as ui;

import 'package:broadband_tool/design/components.dart';
import 'package:broadband_tool/features/wifi_page.dart';
import 'package:broadband_tool/platform/network_api.g.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

List<WifiAccessPointData> denseScan({int shift = 0}) => [
  for (var i = 0; i < 40; i++)
    WifiAccessPointData(
      ssid: 'Mesh ${i % 5}',
      bssid: '02:00:00:00:00:${i.toRadixString(16).padLeft(2, '0')}',
      frequency: 2412 + (i % 13) * 5,
      centerFrequency0: 2412 + (i % 13) * 5,
      centerFrequency1: 0,
      channelWidth: i % 3 == 0 ? 40 : 20,
      rssi: -40 - (i % 8) * 6 + shift,
      timestampMicros: 1000000 + shift,
      security: 'WPA2',
    ),
];

Widget chartPage({
  String band = '2.4',
  int shift = 0,
  ScrollController? scroll,
}) => MaterialApp(
  theme: toolTheme(Brightness.light),
  home: Scaffold(
    body: SingleChildScrollView(
      controller: scroll,
      child: Column(
        children: [
          RepaintBoundary(
            key: const ValueKey('chart-preview'),
            child: ColoredBox(
              color: canvas,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Panel(
                  title: '信道分布 · 40 个模拟 AP',
                  child: ChannelChart(
                    aps: denseScan(shift: shift),
                    band: band,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 900),
        ],
      ),
    ),
  ),
);

ChannelPainter painter(WidgetTester tester) =>
    (tester
            .widget<CustomPaint>(
              find.byWidgetPredicate(
                (widget) =>
                    widget is CustomPaint && widget.painter is ChannelPainter,
              ),
            )
            .painter!
        as ChannelPainter);

Future<void> pinch(WidgetTester tester) async {
  final center = tester.getCenter(find.byKey(const ValueKey('channel-plot')));
  final first = await tester.startGesture(
    center - const Offset(40, 0),
    pointer: 1,
  );
  final second = await tester.startGesture(
    center + const Offset(40, 0),
    pointer: 2,
  );
  await tester.pump();
  for (var step = 1; step <= 8; step++) {
    await first.moveTo(center - Offset(40 + step * 8, 0));
    await second.moveTo(center + Offset(40 + step * 8, 0));
    await tester.pump(const Duration(milliseconds: 16));
  }
  await first.up();
  await second.up();
  await tester.pumpAndSettle();
}

Future<void> preview(WidgetTester tester, String name) async {
  const directory = String.fromEnvironment('CHANNEL_PREVIEW_DIR');
  if (directory.isEmpty) return;
  final boundary = tester.renderObject<RenderRepaintBoundary>(
    find.byKey(const ValueKey('chart-preview')),
  );
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 2);
    final bytes = (await image.toByteData(format: ui.ImageByteFormat.png))!;
    final file = File('$directory/$name.png');
    await file.parent.create(recursive: true);
    await file.writeAsBytes(bytes.buffer.asUint8List());
    image.dispose();
  });
}

void main() {
  testWidgets('pinch zoom and horizontal pan keep the RSSI axis fixed', (
    tester,
  ) async {
    final scroll = ScrollController();
    addTearDown(scroll.dispose);
    await tester.pumpWidget(chartPage(scroll: scroll));
    await pinch(tester);
    final before = painter(tester).view;
    expect(before.width, lessThan(.8));
    expect(before.height, 1);
    expect(before.top, 0);
    await tester.drag(
      find.byKey(const ValueKey('channel-plot')),
      const Offset(100, 40),
    );
    await tester.pumpAndSettle();
    final after = painter(tester).view;
    expect(after.left, lessThan(before.left));
    expect(after.top, 0);
    expect(after.height, 1);
    expect(after.width, closeTo(before.width, .0001));
    expect(scroll.offset, 0);
    expect(tester.takeException(), isNull);
  });

  testWidgets('vertical swipe still scrolls the page after zooming', (
    tester,
  ) async {
    final scroll = ScrollController();
    addTearDown(scroll.dispose);
    await tester.pumpWidget(chartPage(scroll: scroll));
    await pinch(tester);
    final before = painter(tester).view;
    await tester.drag(
      find.byKey(const ValueKey('channel-plot')),
      const Offset(0, -100),
    );
    await tester.pumpAndSettle();
    expect(scroll.offset, greaterThan(50));
    expect(painter(tester).view, before);
    expect(tester.takeException(), isNull);
  });

  testWidgets('unzoomed one-finger swipe scrolls the surrounding page', (
    tester,
  ) async {
    final scroll = ScrollController();
    addTearDown(scroll.dispose);
    await tester.pumpWidget(chartPage(scroll: scroll));
    await tester.drag(
      find.byKey(const ValueKey('channel-plot')),
      const Offset(0, -100),
    );
    await tester.pumpAndSettle();
    expect(scroll.offset, greaterThan(50));
    expect(painter(tester).view, fullChannelView);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'zoom limits, panning bounds, reset and band change are enforced',
    (tester) async {
      await tester.pumpWidget(chartPage());
      for (var i = 0; i < 8; i++) {
        await tester.tap(find.byTooltip('放大信道图'));
        await tester.pump();
      }
      expect(painter(tester).view.width, closeTo(1 / 8, .0001));
      expect(
        tester
            .widget<IconButton>(
              find.byWidgetPredicate(
                (widget) => widget is IconButton && widget.tooltip == '放大信道图',
              ),
            )
            .onPressed,
        isNull,
      );
      await tester.drag(
        find.byKey(const ValueKey('channel-plot')),
        const Offset(10000, 0),
      );
      await tester.pumpAndSettle();
      expect(painter(tester).view.topLeft, Offset.zero);
      await tester.drag(
        find.byKey(const ValueKey('channel-plot')),
        const Offset(-10000, 0),
      );
      await tester.pumpAndSettle();
      expect(painter(tester).view.bottomRight, const Offset(1, 1));
      await tester.tap(find.text('复位'));
      await tester.pump();
      expect(painter(tester).view, fullChannelView);
      await tester.tap(find.byTooltip('放大信道图'));
      await tester.pump();
      await tester.pumpWidget(chartPage(band: '5'));
      expect(painter(tester).view, fullChannelView);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('dense scan refresh preserves zoom and fits a narrow screen', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    const font = String.fromEnvironment('CHANNEL_PREVIEW_FONT');
    if (font.isNotEmpty) {
      await tester.runAsync(() async {
        final bytes = ByteData.sublistView(await File(font).readAsBytes());
        for (final family in ['Roboto', 'Ahem']) {
          await (FontLoader(family)..addFont(Future.value(bytes))).load();
        }
        await (FontLoader(
          'MaterialIcons',
        )..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
      });
    }
    await tester.pumpWidget(chartPage());
    await tester.pumpAndSettle();
    await preview(tester, 'channels-full');
    await pinch(tester);
    final before = painter(tester).view;
    await tester.pumpWidget(chartPage(shift: 2));
    expect(painter(tester).view, before);
    expect(painter(tester).aps.length, 40);
    await tester.pumpAndSettle();
    await preview(tester, 'channels-zoomed');
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    tester.view.physicalSize = const Size(320, 800);
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    await tester.pumpAndSettle();
    expect(painter(tester).view, before);
    expect(tester.takeException(), isNull);
  });
}
