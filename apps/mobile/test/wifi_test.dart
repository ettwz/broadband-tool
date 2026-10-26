import 'dart:ui' as ui;

import 'package:broadband_tool/app/controller.dart';
import 'package:broadband_tool/features/wifi_page.dart';
import 'package:broadband_tool/platform/network_api.g.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('channel 14 and the exceptional 6 GHz channel use real frequencies', () {
    expect(channel(2484), 14);
    expect(channel(5935), 2);
    expect(channel(5955), 1);
    expect(channel(7115), 233);
    expect(channel(4800), isNull);
  });

  test('a missing scan interval breaks the time curve instead of inventing samples', () {
    final spots = signalSpots([
      SignalPoint(1000000, -50),
      SignalPoint(31000000, -53),
      SignalPoint(151000000, -60),
    ], 1000000);
    expect(spots.length, 4);
    expect(spots[2].x.isNaN, true);
    expect(spots.last.x, 150);
    expect(spots.last.y, -60);
  });

  testWidgets(
    '80+80 MHz paints two separated regions and unknown width paints no span',
    (tester) async {
      await tester.runAsync(() async {
        Future<ui.Image> render(int width, int secondCenter) async {
          final recorder = ui.PictureRecorder();
          final canvas = Canvas(recorder);
          canvas.drawColor(Colors.white, BlendMode.src);
          ChannelPainter(
            [
              WifiAccessPointData(
                ssid: 'Fixture',
                bssid: '02:00:00:00:00:01',
                frequency: 5180,
                centerFrequency0: 5210,
                centerFrequency1: secondCenter,
                channelWidth: width,
                rssi: -50,
                timestampMicros: 1000000,
                security: 'WPA2',
              ),
            ],
            '5',
            false,
          ).paint(canvas, const Size(400, 250));
          final picture = recorder.endRecording();
          final image = await picture.toImage(400, 250);
          picture.dispose();
          return image;
        }

        final image = await render(160, 5775);
        final pixels = (await image.toByteData(
          format: ui.ImageByteFormat.rawRgba,
        ))!;
        int colorAt(int x, int y) => pixels.getUint32((y * 400 + x) * 4);
        expect(colorAt(115, 160), isNot(0xffffffff));
        expect(colorAt(334, 160), isNot(0xffffffff));
        expect(
          colorAt(224, 160),
          0xffffffff,
          reason: 'The gap must not be filled as contiguous 160 MHz.',
        );
        image.dispose();
        final unknown = await render(0, 0);
        final unknownPixels = (await unknown.toByteData(
          format: ui.ImageByteFormat.rawRgba,
        ))!;
        expect(unknownPixels.getUint32((160 * 400 + 115) * 4), 0xffffffff);
        unknown.dispose();
      });
    },
  );
}
