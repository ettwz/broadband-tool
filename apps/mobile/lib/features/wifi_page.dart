import 'dart:async';
import 'dart:math' as math;

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import '../app/controller.dart';
import '../design/components.dart';
import '../platform/network_api.g.dart';

const signalColors = [
  accent,
  Color(0xff009c93),
  Color(0xffd79a00),
  Color(0xff9655d5),
  Color(0xffe86164),
  Color(0xff3279a8),
  Color(0xff678b30),
  Color(0xffb16091),
];

Color apColor(String bssid) =>
    signalColors[bssid.codeUnits.fold<int>(
          0,
          (sum, value) => (sum * 31 + value) & 0x7fffffff,
        ) %
        signalColors.length];

String apAge(WifiAccessPointData ap, PlatformSnapshot? snapshot) {
  if (ap.timestampMicros <= 0 ||
      snapshot == null ||
      snapshot.scanUpdatedAt <= 0) {
    return '发现时间未知';
  }
  final latest = snapshot.accessPoints
      .map((item) => item.timestampMicros)
      .fold<int>(0, math.max);
  final observed =
      snapshot.scanUpdatedAt - (latest - ap.timestampMicros) ~/ 1000;
  final age = math.max(
    0,
    (DateTime.now().millisecondsSinceEpoch - observed) ~/ 1000,
  );
  return age > 120 ? '未再发现 · ${age ~/ 60} 分钟前' : '$age 秒前发现';
}

String signalSummary(AppController controller, WifiAccessPointData ap) {
  final points = controller.signalHistory[ap.bssid] ?? <SignalPoint>[];
  if (points.isEmpty) return '没有带有效时间的采样';
  final values = points.map((point) => point.rssi);
  return '当前 ${ap.rssi} · 最低 ${values.reduce(math.min)} · 最高 ${values.reduce(math.max)} · 平均 ${(values.reduce((a, b) => a + b) / values.length).toStringAsFixed(1)} dBm';
}

class WifiPage extends StatefulWidget {
  const WifiPage({super.key, required this.controller, required this.active});
  final AppController controller;
  final bool active;
  @override
  State<WifiPage> createState() => _WifiPageState();
}

class _WifiPageState extends State<WifiPage> {
  String tab = 'channels';
  String band = '2.4';
  String filter = '';
  int minimumSignal = -100;
  Timer? timer;
  @override
  void initState() {
    super.initState();
    timer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (mounted &&
          widget.active &&
          WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
        setState(() {});
      }
    });
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final state = controller.snapshot;
    final permission = state?.permissions;
    final all = state?.accessPoints ?? <WifiAccessPointData>[];
    final filtered =
        all
            .where(
              (ap) =>
                  ap.rssi >= minimumSignal &&
                  ap.ssid.toLowerCase().contains(filter.toLowerCase()),
            )
            .toList()
          ..sort((a, b) => b.rssi.compareTo(a.rssi));
    final visible = filtered
        .where(
          (ap) =>
              frequencyBand(ap.frequency) == band &&
              ap.ssid.toLowerCase().contains(filter.toLowerCase()),
        )
        .toList();
    final chosen = all
        .where((ap) => controller.comparedBssids.contains(ap.bssid))
        .toList();
    final age = state == null || state.scanUpdatedAt == 0
        ? null
        : DateTime.now()
              .difference(
                DateTime.fromMillisecondsSinceEpoch(state.scanUpdatedAt),
              )
              .inSeconds;
    return PageBody(
      children: [
        NetworkCard(
          controller: controller,
          onSelect: () => chooseNetwork(context, controller),
        ),
        SegmentedButton<String>(
          showSelectedIcon: false,
          segments: const [
            ButtonSegment(value: 'networks', label: Text('网络')),
            ButtonSegment(value: 'comparison', label: Text('Comparison')),
            ButtonSegment(value: 'channels', label: Text('Channels')),
          ],
          selected: {tab},
          onSelectionChanged: (value) => setState(() => tab = value.first),
        ),
        if (permission?.fineLocation != true)
          Notice(
            '系统要求精确定位权限，才能显示周边 Wi-Fi。',
            action: TextButton(
              onPressed: () async {
                try {
                  await controller.requestPermissions();
                } on Exception catch (error) {
                  if (context.mounted) showError(context, error);
                }
              },
              child: const Text('允许扫描所需权限'),
            ),
          ),
        if (permission?.fineLocation == true &&
            permission?.locationEnabled != true)
          Notice(
            '请开启系统定位服务。App 不会获取或上传 GPS 位置。',
            action: TextButton(
              onPressed: () => controller.host.openSystemSettings('location'),
              child: const Text('打开定位设置'),
            ),
          ),
        if (permission?.wifiEnabled != true)
          Notice(
            'Wi-Fi 尚未开启。',
            action: TextButton(
              onPressed: () => controller.host.openSystemSettings('wifi'),
              child: const Text('打开 Wi-Fi 设置'),
            ),
          ),
        if (tab != 'comparison')
          SegmentedButton<String>(
            showSelectedIcon: false,
            segments: const [
              ButtonSegment(value: '2.4', label: Text('2.4 GHz')),
              ButtonSegment(value: '5', label: Text('5 GHz')),
              ButtonSegment(value: '6', label: Text('6 GHz')),
            ],
            selected: {band},
            onSelectionChanged: (value) => setState(() => band = value.first),
          ),
        if (tab == 'channels')
          Panel(
            title: '信道分布',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  '接收信号强度 · dBm',
                  style: TextStyle(color: muted, fontSize: 12),
                ),
                const SizedBox(height: 12),
                ChannelChart(aps: visible, band: band),
                const Text(
                  '频率重叠示意，不代表实测占用率或噪声',
                  style: TextStyle(color: muted, fontSize: 11),
                ),
              ],
            ),
          ),
        if (tab == 'comparison')
          Panel(
            title: '信号趋势',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  '最近 10 分钟 · 按实际扫描时间采样',
                  style: TextStyle(color: muted, fontSize: 12),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  height: 225,
                  child: SignalChart(controller: controller, aps: chosen),
                ),
                if (chosen.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Wrap(
                      spacing: 12,
                      runSpacing: 6,
                      children: [
                        for (var i = 0; i < chosen.length; i++)
                          Text(
                            '● ${chosen[i].ssid}',
                            style: TextStyle(
                              color: apColor(chosen[i].bssid),
                              fontSize: 12,
                            ),
                          ),
                      ],
                    ),
                  ),
                for (final ap in chosen)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      '${ap.ssid} (${ap.bssid})\n${signalSummary(controller, ap)}',
                      style: TextStyle(fontSize: 11, color: apColor(ap.bssid)),
                    ),
                  ),
              ],
            ),
          ),
        TextField(
          onChanged: (value) => setState(() => filter = value),
          decoration: const InputDecoration(
            prefixIcon: Icon(Icons.search),
            hintText: '筛选网络名称',
          ),
        ),
        DropdownButtonFormField<int>(
          initialValue: minimumSignal,
          decoration: const InputDecoration(labelText: '信号筛选 · 按强度排序'),
          items: const [
            DropdownMenuItem(value: -100, child: Text('全部信号')),
            DropdownMenuItem(value: -80, child: Text('至少 −80 dBm')),
            DropdownMenuItem(value: -70, child: Text('至少 −70 dBm')),
            DropdownMenuItem(value: -60, child: Text('至少 −60 dBm')),
          ],
          onChanged: (value) => setState(() => minimumSignal = value ?? -100),
        ),
        Panel(
          title: tab == 'comparison'
              ? '选择对比网络（最多 8 个）'
              : '周边网络 · ${visible.length}',
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 6),
          child: Column(
            children: [
              for (final ap in tab == 'comparison' ? filtered : visible)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: tab == 'comparison'
                      ? Checkbox(
                          value: controller.comparedBssids.contains(ap.bssid),
                          onChanged: (enabled) {
                            if (enabled == true &&
                                controller.comparedBssids.length >= 8) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('最多同时对比 8 个接入点')),
                              );
                              return;
                            }
                            controller.compare(ap.bssid, enabled ?? false);
                          },
                        )
                      : Icon(Icons.circle, size: 12, color: apColor(ap.bssid)),
                  title: Text(
                    ap.ssid,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  subtitle: Text(
                    '信道 ${channel(ap.frequency) ?? '?'} · ${ap.channelWidth == 0
                        ? '宽度未知'
                        : ap.centerFrequency1 > 0
                        ? '80+80 MHz'
                        : '${ap.channelWidth} MHz'}\n${ap.bssid} · ${apAge(ap, controller.snapshot)}',
                    style: const TextStyle(fontSize: 11, color: muted),
                  ),
                  trailing: Text(
                    '${ap.rssi} dBm',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  onTap: () => showModalBottomSheet<void>(
                    context: context,
                    showDragHandle: true,
                    builder: (context) => SafeArea(
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              ap.ssid,
                              style: const TextStyle(
                                fontSize: 22,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            ValueRow('BSSID', ap.bssid, copy: true),
                            ValueRow('信号', '${ap.rssi} dBm'),
                            ValueRow('频率', '${ap.frequency} MHz'),
                            ValueRow('安全方式', ap.security),
                            ValueRow('最近发现', apAge(ap, controller.snapshot)),
                            ValueRow(
                              '带宽',
                              ap.channelWidth == 0
                                  ? '系统未提供'
                                  : ap.centerFrequency1 > 0
                                  ? '80+80 MHz'
                                  : '${ap.channelWidth} MHz',
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              if ((tab == 'comparison' ? filtered : visible).isEmpty)
                const Padding(
                  padding: EdgeInsets.all(20),
                  child: Text('尚无扫描结果', style: TextStyle(color: muted)),
                ),
            ],
          ),
        ),
        Row(
          children: [
            Expanded(
              child: Text(
                age == null
                    ? '尚未扫描'
                    : '最近扫描：${age < 60 ? '$age 秒' : '${age ~/ 60} 分钟'}前',
                style: const TextStyle(color: muted, fontSize: 12),
              ),
            ),
            TextButton(
              onPressed: () async {
                if (!controller.scanPaused) {
                  controller.pauseScan();
                  return;
                }
                try {
                  await controller.scan();
                } on Exception catch (error) {
                  if (context.mounted) showError(context, error);
                }
              },
              child: Text(controller.scanPaused ? '开始扫描' : '暂停扫描'),
            ),
          ],
        ),
        if (state?.scanStatus == 'SCAN_THROTTLED')
          const Notice('系统限制了扫描频率，正在保留上次结果。通常约 30 秒后可再次扫描。'),
        if (controller.activeRun != null && !controller.scanPaused)
          const Notice('诊断运行期间暂停主动扫描，避免影响测量。'),
      ],
    );
  }
}

String frequencyBand(int mhz) => mhz >= 5925
    ? '6'
    : mhz >= 4900
    ? '5'
    : '2.4';
int? channel(int mhz) {
  if (mhz == 2484) return 14;
  if (mhz >= 2412 && mhz <= 2472) return (mhz - 2407) ~/ 5;
  if (mhz == 5935) return 2;
  if (mhz >= 5955 && mhz <= 7115) return (mhz - 5950) ~/ 5;
  if (mhz >= 5000 && mhz < 5925) return (mhz - 5000) ~/ 5;
  return null;
}

class SignalChart extends StatelessWidget {
  const SignalChart({super.key, required this.controller, required this.aps});
  final AppController controller;
  final List<WifiAccessPointData> aps;
  @override
  Widget build(BuildContext context) {
    final points = aps
        .expand((ap) => controller.signalHistory[ap.bssid] ?? <SignalPoint>[])
        .toList();
    if (points.isEmpty) {
      return const Center(
        child: Text('选择网络并开始扫描后显示曲线', style: TextStyle(color: muted)),
      );
    }
    final start = points.map((point) => point.timestampMicros).reduce(math.min);
    final end = points.map((point) => point.timestampMicros).reduce(math.max);
    return LineChart(
      LineChartData(
        minX: 0,
        maxX: math.max(1, (end - start) / 1000000),
        minY: -110,
        maxY: -20,
        gridData: const FlGridData(
          show: true,
          horizontalInterval: 20,
          drawVerticalLine: false,
        ),
        borderData: FlBorderData(show: false),
        titlesData: FlTitlesData(
          topTitles: const AxisTitles(
            sideTitles: SideTitles(showTitles: false),
          ),
          rightTitles: const AxisTitles(
            sideTitles: SideTitles(showTitles: false),
          ),
          leftTitles: const AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              interval: 20,
              reservedSize: 35,
            ),
          ),
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 24,
              getTitlesWidget: (value, meta) => Text(
                '${value.toInt()}s',
                style: const TextStyle(fontSize: 10, color: muted),
              ),
            ),
          ),
        ),
        lineBarsData: [
          for (var i = 0; i < aps.length; i++)
            LineChartBarData(
              spots: signalSpots(
                controller.signalHistory[aps[i].bssid] ?? [],
                start,
              ),
              isCurved: false,
              barWidth: 2,
              color: apColor(aps[i].bssid),
              dotData: const FlDotData(show: true),
              belowBarData: BarAreaData(show: false),
            ),
        ],
      ),
    );
  }
}

List<FlSpot> signalSpots(List<SignalPoint> points, int start) {
  final spots = <FlSpot>[];
  for (var i = 0; i < points.length; i++) {
    if (i > 0 &&
        points[i].timestampMicros - points[i - 1].timestampMicros > 90000000) {
      spots.add(FlSpot.nullSpot);
    }
    spots.add(
      FlSpot(
        (points[i].timestampMicros - start) / 1000000,
        points[i].rssi.toDouble(),
      ),
    );
  }
  return spots;
}

const fullChannelView = Rect.fromLTWH(0, 0, 1, 1);

(double, double) channelFrequencyRange(String band) => switch (band) {
  '5' => (5000.0, 5925.0),
  '6' => (5925.0, 7125.0),
  _ => (2395.0, 2505.0),
};

Rect channelPlot(Size size) =>
    Rect.fromLTRB(34, 12, size.width - 8, size.height - 28);

/// Normalized viewport coordinates keep zoom stable across scans and resizing.
class ChannelChart extends StatefulWidget {
  const ChannelChart({super.key, required this.aps, required this.band});
  final List<WifiAccessPointData> aps;
  final String band;

  @override
  State<ChannelChart> createState() => _ChannelChartState();
}

class _ChannelChartState extends State<ChannelChart> {
  static const maxZoom = 8.0;
  Rect view = fullChannelView;
  Offset lastFocal = Offset.zero;
  double lastGestureScale = 1;
  int lastPointerCount = 0;
  double get zoom => 1 / view.width;

  @override
  void didUpdateWidget(ChannelChart oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.band != widget.band) view = fullChannelView;
  }

  Offset normalized(Offset point, Size size) {
    final plot = channelPlot(size);
    return Offset(
      (point.dx - plot.left) / plot.width,
      (point.dy - plot.top) / plot.height,
    );
  }

  void transform(double nextZoom, Offset previous, Offset next) {
    final scale = nextZoom.clamp(1.0, maxZoom);
    final span = 1 / scale;
    final anchor = view.left + previous.dx * view.width;
    setState(() {
      view = Rect.fromLTWH(
        (anchor - next.dx * span).clamp(0.0, 1 - span),
        0,
        span,
        1,
      );
    });
  }

  void scaleStart(ScaleStartDetails details, Size size) {
    lastFocal = normalized(details.localFocalPoint, size);
    lastGestureScale = 1;
    lastPointerCount = details.pointerCount;
  }

  void scaleUpdate(ScaleUpdateDetails details, Size size) {
    if (!details.scale.isFinite || details.scale <= 0) return;
    final focal = normalized(details.localFocalPoint, size);
    // Adding/lifting a finger changes the focal point; do not jump the plot.
    if (details.pointerCount == lastPointerCount && details.scale > 0) {
      transform(zoom * details.scale / lastGestureScale, lastFocal, focal);
    }
    lastFocal = focal;
    lastGestureScale = details.scale;
    lastPointerCount = details.pointerCount;
  }

  @override
  Widget build(BuildContext context) {
    final range = channelFrequencyRange(widget.band);
    final start = range.$1 + (range.$2 - range.$1) * view.left;
    final end = range.$1 + (range.$2 - range.$1) * view.right;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: 4,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(
              '${zoom.toStringAsFixed(1)}×',
              style: const TextStyle(color: muted),
            ),
            IconButton(
              tooltip: '缩小信道图',
              onPressed: widget.aps.isNotEmpty && zoom > 1.001
                  ? () => transform(
                      zoom / 1.5,
                      const Offset(.5, .5),
                      const Offset(.5, .5),
                    )
                  : null,
              icon: const Icon(Icons.remove_circle_outline),
            ),
            IconButton(
              tooltip: '放大信道图',
              onPressed: widget.aps.isNotEmpty && zoom < maxZoom - .001
                  ? () => transform(
                      zoom * 1.5,
                      const Offset(.5, .5),
                      const Offset(.5, .5),
                    )
                  : null,
              icon: const Icon(Icons.add_circle_outline),
            ),
            TextButton.icon(
              onPressed: view != fullChannelView
                  ? () => setState(() => view = fullChannelView)
                  : null,
              icon: const Icon(Icons.restart_alt, size: 20),
              label: const Text('复位'),
            ),
          ],
        ),
        const Text(
          '双指缩放横轴，放大后左右拖动',
          style: TextStyle(color: muted, fontSize: 12),
        ),
        Text(
          '${start.round()}–${end.round()} MHz · -110～-20 dBm',
          style: const TextStyle(color: muted, fontSize: 11),
        ),
        const SizedBox(height: 8),
        SizedBox(
          height: 235,
          child: widget.aps.isEmpty
              ? const Center(
                  child: Text('扫描后显示该频段的信道分布', style: TextStyle(color: muted)),
                )
              : LayoutBuilder(
                  builder: (context, constraints) {
                    final size = Size(
                      constraints.maxWidth,
                      constraints.maxHeight,
                    );
                    return Semantics(
                      label: 'Wi-Fi 信道分布图',
                      value: '横轴缩放 ${zoom.toStringAsFixed(1)} 倍',
                      child: RawGestureDetector(
                        key: const ValueKey('channel-plot'),
                        behavior: HitTestBehavior.opaque,
                        gestures: {
                          _ChannelScaleRecognizer:
                              GestureRecognizerFactoryWithHandlers<
                                _ChannelScaleRecognizer
                              >(_ChannelScaleRecognizer.new, (recognizer) {
                                // Match the page's drag slop; direction decides
                                // between horizontal chart pan and page scroll.
                                recognizer.gestureSettings =
                                    DeviceGestureSettings(
                                      touchSlop:
                                          (MediaQuery.gestureSettingsOf(context)
                                                  .touchSlop ??
                                              kTouchSlop) /
                                          2,
                                    );
                                recognizer.canPan = () => zoom > 1.001;
                                recognizer.onStart = (details) =>
                                    scaleStart(details, size);
                                recognizer.onUpdate = (details) =>
                                    scaleUpdate(details, size);
                              }),
                        },
                        child: CustomPaint(
                          painter: ChannelPainter(
                            widget.aps,
                            widget.band,
                            Theme.of(context).brightness == Brightness.dark,
                            view: view,
                            fontFamily: Theme.of(context)
                                .textTheme
                                .bodySmall
                                ?.fontFamily,
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }
}

/// Vertical one-finger movement always belongs to the enclosing page scroll.
class _ChannelScaleRecognizer extends ScaleGestureRecognizer {
  bool Function() canPan = () => false;
  Offset start = Offset.zero;
  Offset movement = Offset.zero;
  bool accepted = false;

  @override
  void addAllowedPointer(PointerDownEvent event) {
    if (pointerCount == 0) {
      start = event.position;
      movement = Offset.zero;
      accepted = false;
    }
    super.addAllowedPointer(event);
  }

  @override
  void handleEvent(PointerEvent event) {
    if (event is PointerMoveEvent && pointerCount == 1) {
      movement = event.position - start;
    }
    super.handleEvent(event);
  }

  @override
  void acceptGesture(int pointer) {
    accepted = true;
    super.acceptGesture(pointer);
  }

  @override
  void resolve(GestureDisposition disposition) {
    super.resolve(
      disposition == GestureDisposition.accepted &&
              !accepted &&
              pointerCount < 2 &&
              (!canPan() || movement.dx.abs() <= movement.dy.abs())
          ? GestureDisposition.rejected
          : disposition,
    );
  }
}

class ChannelPainter extends CustomPainter {
  ChannelPainter(
    this.aps,
    this.band,
    this.dark, {
    this.view = fullChannelView,
    this.fontFamily,
  });
  final List<WifiAccessPointData> aps;
  final String band;
  final bool dark;
  final Rect view;
  final String? fontFamily;
  @override
  void paint(Canvas canvas, Size size) {
    final plot = channelPlot(size);
    final range = channelFrequencyRange(band);
    double x(double frequency) =>
        plot.left +
        ((frequency - range.$1) / (range.$2 - range.$1) - view.left) /
            view.width *
            plot.width;
    double y(int rssi) => plot.top + (-20 - rssi) / 90 * plot.height;
    final baseline = y(-110);
    final grid = Paint()
      ..color = dark ? const Color(0xff354054) : const Color(0xffe6edf5)
      ..strokeWidth = .7;
    void text(String value, Offset point, {bool centered = false}) {
      final painter = TextPainter(
        text: TextSpan(
          text: value,
          style: TextStyle(fontSize: 10, color: muted, fontFamily: fontFamily),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      painter.paint(
        canvas,
        centered ? point - Offset(painter.width / 2, 0) : point,
      );
    }

    for (final level in [-30, -50, -70, -90, -110]) {
      canvas.drawLine(
        Offset(plot.left, y(level)),
        Offset(plot.right, y(level)),
        grid,
      );
      text('$level', Offset(0, y(level) - 6));
    }
    final ticks = view == fullChannelView
        ? switch (band) {
            '5' => [36, 64, 100, 132, 165],
            '6' => [1, 49, 97, 145, 193, 233],
            _ => [1, 3, 6, 9, 11, 14],
          }
        : switch (band) {
            '5' => [
              for (var ch = 36; ch <= 64; ch += 4) ch,
              for (var ch = 100; ch <= 144; ch += 4) ch,
              for (var ch = 149; ch <= 177; ch += 4) ch,
            ],
            '6' => [2, for (var ch = 1; ch <= 233; ch += 4) ch],
            _ => [for (var ch = 1; ch <= 14; ch++) ch],
          };
    var lastTickX = double.negativeInfinity;
    for (final tick in ticks) {
      final frequency = band == '5'
          ? 5000 + tick * 5
          : band == '6'
          ? (tick == 2 ? 5935 : 5950 + tick * 5)
          : tick == 14
          ? 2484
          : 2407 + tick * 5;
      final tickX = x(frequency.toDouble());
      if (tickX < plot.left || tickX > plot.right || tickX - lastTickX < 32) {
        continue;
      }
      lastTickX = tickX;
      canvas.drawLine(
        Offset(tickX, plot.top),
        Offset(tickX, plot.bottom),
        grid,
      );
      text('$tick', Offset(tickX, plot.bottom + 7), centered: true);
    }
    canvas.save();
    canvas.clipRect(plot);
    for (var i = aps.length - 1; i >= 0; i--) {
      final ap = aps[i];
      final color = apColor(ap.bssid);
      if (ap.channelWidth <= 0) {
        canvas.drawCircle(
          Offset(x(ap.frequency.toDouble()), y(ap.rssi)),
          3,
          Paint()..color = color,
        );
        continue;
      }
      final centers = ap.centerFrequency1 > 0
          ? [ap.centerFrequency0, ap.centerFrequency1]
          : [
              ap.channelWidth == 20 || ap.centerFrequency0 == 0
                  ? ap.frequency
                  : ap.centerFrequency0,
            ];
      final width = ap.centerFrequency1 > 0 ? 80 : ap.channelWidth;
      for (final center in centers) {
        final left = x(center - width / 2);
        final right = x(center + width / 2);
        final path = Path()
          ..moveTo(left, baseline)
          ..cubicTo(
            left + (right - left) * .15,
            baseline,
            left + (right - left) * .22,
            y(ap.rssi),
            (left + right) / 2,
            y(ap.rssi),
          )
          ..cubicTo(
            right - (right - left) * .22,
            y(ap.rssi),
            right - (right - left) * .15,
            baseline,
            right,
            baseline,
          );
        canvas.drawPath(
          path,
          Paint()
            ..color = color.withValues(alpha: .13)
            ..style = PaintingStyle.fill,
        );
        canvas.drawPath(
          path,
          Paint()
            ..color = color
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1.6,
        );
      }
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant ChannelPainter old) =>
      old.aps != aps ||
      old.band != band ||
      old.dark != dark ||
      old.view != view ||
      old.fontFamily != fontFamily;
}
