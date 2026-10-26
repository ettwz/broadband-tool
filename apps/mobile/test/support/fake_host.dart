import 'dart:io';

import 'package:broadband_tool/platform/network_api.g.dart';

PlatformSnapshot fixtureSnapshot({
  int generation = 1,
  String id = 'wifi-test',
}) => PlatformSnapshot(
  platform: 'android',
  apiLevel: 28,
  networks: [
    NetworkData(
      id: id,
      generation: generation,
      transport: 'wifi',
      interfaceName: 'wlan0',
      displayName: 'Test Wi-Fi',
      addresses: ['192.0.2.23'],
      prefixes: [24],
      dnsServers: ['192.0.2.1'],
      isDefault: true,
      validated: false,
      isVpn: false,
      gateway: '192.0.2.1',
      mtu: 1500,
      ssid: 'Test Wi-Fi',
      rssi: -50,
      frequency: 5180,
      linkSpeedMbps: 433,
    ),
  ],
  permissions: PermissionData(
    fineLocation: true,
    locationEnabled: true,
    wifiEnabled: true,
    localNetwork: true,
  ),
  accessPoints: [],
  scanStatus: 'idle',
  scanUpdatedAt: 0,
  selectedNetworkId: id,
  privilege: PrivilegeData(
    status: 'not_installed',
    generation: 0,
    authorized: false,
    capabilities: [],
    interfaces: [],
    message: 'Shizuku 未安装',
  ),
);

class FakeHost extends NetworkHostApi {
  FakeHost(this.directory);
  final Directory directory;
  final requests = <TaskRequestData>[];
  final stopped = <String>[];
  PlatformSnapshot state = fixtureSnapshot();
  @override
  Future<PlatformSnapshot> getSnapshot() async => state;
  @override
  Future<String> getSupportDirectory() async => directory.path;
  @override
  Future<String> startTask(TaskRequestData request) async {
    requests.add(request);
    return request.requestId;
  }

  @override
  Future<void> stopTask(String taskId) async {
    stopped.add(taskId);
  }

  @override
  Future<PermissionData> requestNetworkPermissions() async => state.permissions;
  @override
  Future<void> requestWifiScan() async {}
  @override
  Future<void> selectNetwork(String networkId) async {
    state.selectedNetworkId = networkId;
  }
}
