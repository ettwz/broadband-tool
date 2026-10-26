import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/platform/network_api.g.dart',
    kotlinOut:
        'android/app/src/main/kotlin/dev/broadband/toolbox/NetworkApi.g.kt',
    kotlinOptions: KotlinOptions(package: 'dev.broadband.toolbox'),
    swiftOut: 'ios/Runner/NetworkApi.g.swift',
  ),
)
class NetworkData {
  NetworkData({
    required this.id,
    required this.generation,
    required this.transport,
    required this.interfaceName,
    required this.displayName,
    required this.addresses,
    required this.prefixes,
    required this.dnsServers,
    required this.isDefault,
    required this.validated,
    required this.isVpn,
    this.gateway,
    this.mtu,
    this.ssid,
    this.bssid,
    this.rssi,
    this.frequency,
    this.linkSpeedMbps,
  });
  String id;
  int generation;
  String transport;
  String interfaceName;
  String displayName;
  List<String> addresses;
  List<int> prefixes;
  List<String> dnsServers;
  bool isDefault;
  bool validated;
  bool isVpn;
  String? gateway;
  int? mtu;
  String? ssid;
  String? bssid;
  int? rssi;
  int? frequency;
  int? linkSpeedMbps;
}

class WifiAccessPointData {
  WifiAccessPointData({
    required this.ssid,
    required this.bssid,
    required this.frequency,
    required this.centerFrequency0,
    required this.centerFrequency1,
    required this.channelWidth,
    required this.rssi,
    required this.timestampMicros,
    required this.security,
  });
  String ssid;
  String bssid;
  int frequency;
  int centerFrequency0;
  int centerFrequency1;
  int channelWidth;
  int rssi;
  int timestampMicros;
  String security;
}

class PermissionData {
  PermissionData({
    required this.fineLocation,
    required this.locationEnabled,
    required this.wifiEnabled,
    required this.localNetwork,
  });
  bool fineLocation;
  bool locationEnabled;
  bool wifiEnabled;
  bool localNetwork;
}

class CapabilityData {
  CapabilityData({
    required this.name,
    required this.supported,
    required this.reason,
  });
  String name;
  bool supported;
  String reason;
}

class InterfaceData {
  InterfaceData({
    required this.id,
    required this.name,
    required this.index,
    required this.generation,
    required this.transport,
    required this.addresses,
    required this.prefixes,
    required this.up,
    required this.mtu,
  });
  String id;
  String name;
  int index;
  int generation;
  String transport;
  List<String> addresses;
  List<int> prefixes;
  bool up;
  int mtu;
}

class PrivilegeData {
  PrivilegeData({
    required this.status,
    required this.generation,
    required this.authorized,
    required this.capabilities,
    required this.interfaces,
    this.serverUid,
    this.workerUid,
    this.message,
    this.recoveryJson,
  });
  String status;
  int generation;
  bool authorized;
  List<CapabilityData> capabilities;
  List<InterfaceData> interfaces;
  int? serverUid;
  int? workerUid;
  String? message;
  String? recoveryJson;
}

class PlatformSnapshot {
  PlatformSnapshot({
    required this.platform,
    required this.apiLevel,
    required this.networks,
    required this.permissions,
    required this.accessPoints,
    required this.scanStatus,
    required this.scanUpdatedAt,
    required this.privilege,
    this.selectedNetworkId,
  });
  String platform;
  int apiLevel;
  List<NetworkData> networks;
  PermissionData permissions;
  List<WifiAccessPointData> accessPoints;
  String scanStatus;
  int scanUpdatedAt;
  PrivilegeData privilege;
  String? selectedNetworkId;
}

class TaskRequestData {
  TaskRequestData({
    required this.requestId,
    required this.kind,
    required this.parametersJson,
    this.networkId,
    this.networkGeneration,
    this.interfaceId,
    this.privilegeGeneration,
  });
  String requestId;
  String kind;
  String parametersJson;
  String? networkId;
  int? networkGeneration;
  String? interfaceId;
  int? privilegeGeneration;
}

class TaskEventData {
  TaskEventData({
    required this.taskId,
    required this.kind,
    required this.state,
    required this.sequence,
    required this.elapsedMs,
    required this.dataJson,
    this.errorCode,
    this.message,
  });
  String taskId;
  String kind;
  String state;
  int sequence;
  int elapsedMs;
  String dataJson;
  String? errorCode;
  String? message;
}

class CaptureFileData {
  CaptureFileData({
    required this.id,
    required this.createdAt,
    required this.bytes,
    required this.packets,
    required this.complete,
    required this.interfaceName,
  });
  String id;
  int createdAt;
  int bytes;
  int packets;
  bool complete;
  String interfaceName;
}

@HostApi()
abstract class NetworkHostApi {
  @asyncCallback
  PlatformSnapshot getSnapshot();
  void selectNetwork(String networkId);
  @asyncCallback
  PermissionData requestNetworkPermissions();
  void requestWifiScan();
  String startTask(TaskRequestData request);
  void stopTask(String taskId);
  void sendTerminalInput(String taskId, Uint8List bytes);
  void resizeTerminal(String taskId, int columns, int rows);
  @asyncCallback
  PrivilegeData requestAdvancedAccess();
  void openSystemSettings(String section);
  @asyncCallback
  String exportCapture(String captureId);
  List<CaptureFileData> listCaptures();
  void deleteCapture(String captureId);
  @asyncCallback
  String getSupportDirectory();
}

@FlutterApi()
abstract class NetworkFlutterApi {
  void onSnapshot(PlatformSnapshot snapshot);
  void onTaskEvent(TaskEventData event);
}
