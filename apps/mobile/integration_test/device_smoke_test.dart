import 'package:integration_test/integration_test.dart';

import '../test/channel_zoom_test.dart' as channel_zoom;
import 'ordinary_smoke_test.dart' as ordinary;
import 'system_dns_smoke_test.dart' as system_dns;
import 'web_smoke_test.dart' as web;

/// Reuse the same gesture assertions on Android's renderer and real sockets.
/// Install the normal release APK again after running this test entry point.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  ordinary.main();
  channel_zoom.main();
  web.main();
  if (const bool.fromEnvironment('TEST_SYSTEM_DNS')) system_dns.main();
}
