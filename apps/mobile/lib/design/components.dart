import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../app/controller.dart';

const accent = Color(0xff087cff);
const muted = Color(0xff66758c);
const canvas = Color(0xfff3f5f9);

ThemeData toolTheme(Brightness brightness) {
  final scheme = ColorScheme.fromSeed(
    seedColor: accent,
    brightness: brightness,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme.copyWith(primary: accent),
    scaffoldBackgroundColor: brightness == Brightness.light
        ? canvas
        : const Color(0xff10151e),
    appBarTheme: AppBarTheme(
      backgroundColor: brightness == Brightness.light
          ? canvas
          : const Color(0xff10151e),
      surfaceTintColor: Colors.transparent,
      centerTitle: false,
      titleTextStyle: TextStyle(
        fontSize: 25,
        fontWeight: FontWeight.w700,
        color: scheme.onSurface,
      ),
    ),
    cardTheme: CardThemeData(
      color: brightness == Brightness.light
          ? Colors.white
          : const Color(0xff1c2431),
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: brightness == Brightness.light
          ? const Color(0xfff7f9fc)
          : const Color(0xff151d29),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(11),
        borderSide: BorderSide.none,
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(11),
        borderSide: BorderSide(color: scheme.outlineVariant),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(11)),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size.fromHeight(46),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(11)),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: brightness == Brightness.light
          ? Colors.white
          : const Color(0xff1c2431),
      indicatorColor: accent.withValues(alpha: .12),
      height: 72,
    ),
    dividerTheme: DividerThemeData(
      color: scheme.outlineVariant.withValues(alpha: .55),
      thickness: .7,
    ),
  );
}

class PageBody extends StatelessWidget {
  const PageBody({super.key, required this.children, this.controller});
  final List<Widget> children;
  final ScrollController? controller;
  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.topCenter,
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 760),
      child: ListView(
        controller: controller,
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          for (final child in children)
            Padding(padding: const EdgeInsets.only(bottom: 14), child: child),
        ],
      ),
    ),
  );
}

class Panel extends StatelessWidget {
  const Panel({
    super.key,
    required this.child,
    this.title,
    this.padding = const EdgeInsets.all(16),
  });
  final Widget child;
  final String? title;
  final EdgeInsetsGeometry padding;
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: padding,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (title != null) ...[
            Text(
              title!,
              style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 14),
          ],
          child,
        ],
      ),
    ),
  );
}

class ValueRow extends StatelessWidget {
  const ValueRow(this.label, this.value, {super.key, this.copy = false});
  final String label;
  final String value;
  final bool copy;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 9),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          flex: 4,
          child: Text(label, style: const TextStyle(color: muted)),
        ),
        const SizedBox(width: 12),
        Expanded(
          flex: 6,
          child: GestureDetector(
            onLongPress: copy
                ? () => Clipboard.setData(ClipboardData(text: value))
                : null,
            child: SelectableText(
              value,
              textAlign: TextAlign.right,
              style: const TextStyle(fontWeight: FontWeight.w500),
            ),
          ),
        ),
      ],
    ),
  );
}

class Notice extends StatelessWidget {
  const Notice(this.message, {super.key, this.warning = false, this.action});
  final String message;
  final bool warning;
  final Widget? action;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: (warning ? Colors.orange : accent).withValues(alpha: .09),
      borderRadius: BorderRadius.circular(12),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          warning ? Icons.info_outline : Icons.info_outline_rounded,
          color: warning ? Colors.orange : accent,
          size: 21,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(message, style: const TextStyle(fontSize: 13, height: 1.5)),
              ?action,
            ],
          ),
        ),
      ],
    ),
  );
}

class StatusBadge extends StatelessWidget {
  const StatusBadge(this.label, {super.key, this.color = accent});
  final String label;
  final Color color;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
    decoration: BoxDecoration(
      color: color.withValues(alpha: .1),
      borderRadius: BorderRadius.circular(20),
    ),
    child: Text(
      label,
      style: TextStyle(fontSize: 12, color: color, fontWeight: FontWeight.w600),
    ),
  );
}

class NetworkCard extends StatelessWidget {
  const NetworkCard({super.key, required this.controller, this.onSelect});
  final AppController controller;
  final VoidCallback? onSelect;
  @override
  Widget build(BuildContext context) {
    final network = controller.selectedNetwork;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: accent.withValues(alpha: .09),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                network?.transport == 'ethernet'
                    ? Icons.settings_ethernet_rounded
                    : Icons.wifi_rounded,
                color: accent,
                size: 30,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    network?.displayName ?? '未选择网络',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    network == null
                        ? '请选择 Wi-Fi 或有线网卡'
                        : '${network.transport == 'ethernet' ? '有线' : 'Wi-Fi'} · IPv4',
                    style: const TextStyle(color: muted, fontSize: 12),
                  ),
                  if (network != null)
                    Text(
                      network.addresses.isEmpty
                          ? '等待获取 IPv4'
                          : network.addresses.join(' / '),
                      style: const TextStyle(color: muted, fontSize: 13),
                    ),
                ],
              ),
            ),
            if (onSelect != null)
              TextButton(onPressed: onSelect, child: const Text('切换')),
          ],
        ),
      ),
    );
  }
}

Future<void> chooseNetwork(
  BuildContext context,
  AppController controller,
) async {
  final networks =
      controller.snapshot?.networks
          .where(
            (network) =>
                network.transport == 'wifi' || network.transport == 'ethernet',
          )
          .toList() ??
      [];
  if (!context.mounted) return;
  await showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (sheetContext) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              '选择网络',
              style: TextStyle(fontSize: 23, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            const Text(
              '仅切换本应用的诊断网络，不会自动改用蜂窝网络',
              style: TextStyle(color: muted, fontSize: 13),
            ),
            const SizedBox(height: 14),
            if (networks.isEmpty)
              const Padding(
                padding: EdgeInsets.all(24),
                child: Text('尚未发现可用的 Wi-Fi 或有线网卡'),
              ),
            for (final network in networks)
              ListTile(
                leading: Icon(
                  network.transport == 'ethernet'
                      ? Icons.settings_ethernet
                      : Icons.wifi,
                  color: accent,
                ),
                title: Text(network.displayName),
                subtitle: Text(
                  '${network.interfaceName} · ${network.addresses.isEmpty ? '等待 IPv4' : network.addresses.join(', ')}',
                ),
                trailing: controller.selectedNetwork?.id == network.id
                    ? const Icon(Icons.check_circle, color: accent)
                    : null,
                onTap: () async {
                  if (controller.activeRun != null) {
                    final approved = await showDialog<bool>(
                      context: sheetContext,
                      builder: (context) => AlertDialog(
                        title: const Text('停止当前任务并切换？'),
                        content: const Text('当前诊断会停止，结果会标记为中断。'),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(context, false),
                            child: const Text('取消'),
                          ),
                          TextButton(
                            onPressed: () => Navigator.pop(context, true),
                            child: const Text('停止并切换'),
                          ),
                        ],
                      ),
                    );
                    if (approved != true) return;
                  }
                  try {
                    await controller.selectNetwork(network);
                    if (sheetContext.mounted) Navigator.pop(sheetContext);
                  } on Exception catch (error) {
                    if (sheetContext.mounted) showError(sheetContext, error);
                  }
                },
              ),
          ],
        ),
      ),
    ),
  );
}

void showError(BuildContext context, Object error) =>
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(readableError(error)),
        behavior: SnackBarBehavior.floating,
      ),
    );

String number(num? value, {int digits = 1, String missing = '—'}) =>
    value == null ? missing : value.toStringAsFixed(digits);
String bytesLabel(num bytes) => bytes >= 1000000
    ? '${number(bytes / 1000000)} MB'
    : bytes >= 1000
    ? '${number(bytes / 1000)} KB'
    : '${bytes.toInt()} B';
String clockLabel(DateTime value) =>
    '${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}:${value.second.toString().padLeft(2, '0')}';

Widget metric(String label, String value) => Expanded(
  child: Padding(
    padding: const EdgeInsets.symmetric(horizontal: 6),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(color: muted, fontSize: 12)),
        const SizedBox(height: 5),
        Text(
          value,
          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
        ),
      ],
    ),
  ),
);
