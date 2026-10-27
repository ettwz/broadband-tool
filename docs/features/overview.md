# 当前设备网络概览

doc_id: F07 · 原需求 11，并承载需求 4

以当前选择网络为主卡，同时列出系统可见的 Wi-Fi / Ethernet，蜂窝与 VPN 作为状态信息展示。使用原生 API 的结构化数据，不解析厂商差异很大的 ifconfig 文本。[S01-06、07](../research/sources-platform.md)

蜂窝项只展示带 `NET_CAPABILITY_INTERNET` 的普通数据网络，不把 IMS/彩信等运营商专用网络混入“可用网络”。不按 SIM 卡数量补占位行，也不按 IPv4 是否为空或 `VALIDATED` 过滤；IPv6-only 数据网、未验证的数据网及仅局域网的 Wi-Fi/有线仍保留。依据 [Android NetworkCapabilities](https://developer.android.com/reference/android/net/NetworkCapabilities)。

| 分组 | 字段 |
| --- | --- |
| 接口 | 显示名、系统接口名、类型、连接状态、选中/系统默认标签 |
| IPv4 | 地址列表、前缀长度、掩码；多地址不能只取第一项而隐去其余 |
| 路由 | 该接口的 IPv4 默认网关；无默认路由或多条路由明确显示 |
| DNS | 地址列表、可见的 Private DNS 状态；无 IPv4 DNS 单独提示 |
| 链路 | MTU、Wi-Fi 协商速率/频率/信道/RSSI（系统提供时） |
| Wi-Fi | 当前 SSID、BSSID、安全信息（可获取时）；定位受限说明 |
| 公网 | 经该接口查询的公网 IPv4、查询时间及服务来源 |
| 路径提示 | VPN、代理、强制门户、互联网验证状态及未知状态 |

设备 MAC 不作为必备字段，系统隐藏时写“系统未提供”；不拿 AP 的 BSSID 冒充手机 MAC。Ethernet 协商速率可能不可得，不显示虚构 1000 Mbps。MTU 返回默认/未知时不能直接显示 0 字节；IPv4 链路本地地址可展示但提示可能未取得 DHCP 地址。

接口变化由回调触发；手工刷新重新取快照。公网 IP 单独按用户操作查询，不在每秒刷新时请求第三方。复制和导出包含采集时间与缺失原因；单击网关可带入 ping，单击 DNS 可带入 DNS 工具。

验收：Wi-Fi 单网卡、有线单网卡、双网卡、无默认路由、多 IPv4、无 IPv4、被隐藏字段、USB 拔插和地址更新。与系统设置及实验服务器观测交叉核对；iOS 只展示其可获取字段，不为追求表格对齐读取私有 API。
