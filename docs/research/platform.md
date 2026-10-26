# 平台可行性

doc_id: R01 · 本表为普通模式；Android 高级扩展另见 R06

用户已选择 KernelSU Next + Shizuku Root 作为高级环境，范围见 [R06](root-access.md)；以下普通权限结论仍有效，不能覆盖或取消高级需求。

| 能力 | Android | iOS | 结论 |
| --- | --- | --- | --- |
| 周边 Wi-Fi / RSSI / 信道 | WifiManager 扫描，有权限与频率限制 | 无通用公开扫描 API | Android 可规划；iOS 不承诺 |
| USB Ethernet 枚举 | 系统提供 Network 后可纳入选择 | 系统支持配件后通过网络接口识别 | App 不负责安装 USB 网卡驱动 |
| 指定诊断连接出口 | Network.bindSocket / NDK socket 绑定 | Network.framework 或 BSD socket 适配 | 可行路径，必须双接口验证 |
| 网卡参数 | LinkProperties 较完整 | getifaddrs / NWPath 等只能取得部分信息 | 缺失字段带原因，不伪造 |
| ping / traceroute | 非特权 socket 路径待 OEM 验证 | ping 有参考；逐跳 ICMP 接收需独立验证 | 高风险项提前验证 |
| 全系统 DNS 清理 | 特权权限保护 | 未找到普通应用的公开通用接口 | 不作为可实现承诺 |
| 公网网页绑定与 IPv4 | WebView 的独立风险 | WKWebView 不能套用 NWConnection 的接口能力 | 独立门槛 T03 / T13 |

## 权限设计

Android 基础使用 INTERNET、ACCESS_NETWORK_STATE；Wi-Fi 扫描需要 ACCESS_WIFI_STATE、CHANGE_WIFI_STATE、精确定位及定位服务。Android 13+ 不能用 NEARBY_WIFI_DEVICES 替代扫描定位权限，也不能把扫描所需定位权限错误限制为 maxSdk 32。[S01-01、02](sources-platform.md)

Android 17 且 target 37+：直接局域网通信要处理 ACCESS_LOCAL_NETWORK；Android 16 有 opt-in 差异。根据最终 target 配置请求，不能拿旧版本权限结论覆盖新版本。[S01-14](sources-platform.md)

iOS 使用 NSLocalNetworkUsageDescription；不同 TCP/UDP 方向的触发规则不同，须真机拒绝/恢复验证。手工目标 IP 的一期工具不主动引入组播发现；若以后加广播/组播，再评估 entitlement。[S01-15](sources-platform.md)

## 设备与发布边界

USB-C 形状相同不代表网卡驱动、供电、OTG、链路速率均可用。验证设备至少包括两家 Android 厂商、两类网卡及有源扩展坞。普通模式只选择系统可用连接；高级模式可按 [F12](../features/ethernet-config.md) 临时修改选定有线网卡，仍不自动修改全机默认网络，也不安装缺失的网卡驱动。

未来 iOS 上架前必须处理 Apple 的 IPv6-only 网络要求。Android 一期仍按 IPv4 执行；共享契约保留地址族字段，iOS 发布任务单独评估诊断范围与基础联网在 IPv6-only 环境的行为，不能直接复制 AF_INET 限制后宣称可以上架。[S01-17](sources-platform.md)
