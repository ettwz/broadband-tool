# 网络选择与出口约束

doc_id: A03

启动默认选择可用 Wi-Fi；没有 Wi-Fi 时显示接口选择，可提示有线可用但不静默改用蜂窝。用户可主动选择系统识别的 Ethernet。选择只影响本 App 的诊断，不修改全机默认网络。Wi-Fi 无公网仍可做 LAN 工具，不以 INTERNET / VALIDATED 状态过滤掉它。

这段选择行为不等同高级模式的网卡配置写入。F12 可临时改变选定有线接口并影响其他使用它的应用，必须经预览和恢复事务。抓包/ARP/DHCP 使用 A05 的 InterfaceRef，允许无 IPv4；interfaceName 不能独自作为设备身份，拔插后 generation 必须变化。

1. 用网络回调维护 Wi-Fi / Ethernet 列表；接口名只展示，不硬编码 wlan0、eth0。
2. 选择后创建 NetworkContext。IPv4 取选定网络地址；无地址返回 NO_IPV4。系统默认和用户选择分别显示。
3. 域名先通过该网络解析，选择 A 记录；连接前绑定相同网络。HTTPS 保留域名、SNI 和证书校验，不能用替换 IP 的方式破坏 TLS。
4. Android Java socket 用 Network 绑定；C socket 用 android_setsocknetwork；DNS 用网络级 API 或绑定的显式 DNS 查询。iperf 的控制、数据和监听 socket 全部覆盖，`-B 源地址`本身不作为 Android 出口证明。[S01-04、05](../research/sources-platform.md)
5. 任务期间网络丢失、IPv4 改变或用户切换：取消、关闭、终态、更新选择。不得续接另一个网卡或蜂窝后沿用同一结果。

原生库 socket 工厂应接收 context 和 socket 创建回调；上游没有合适 hook 时，以小补丁或独立工作进程验证，不能假设已有 API。补丁必须记录版本和覆盖所有 socket 的测试。

## 特殊路径

VPN、代理、强制门户可能改变出口；能观测的单独标注。严格模式不能确认是否绕过 VPN 时停止并说明，不能自行绕开用户 VPN 策略。外部浏览器属于系统默认网络，不继承本 App 选择。

WebView 按 [R02](../research/speedtest.md) 专门验证。网卡绑定不等于 IPv4 限定；同时满足才可写“已选接口 / IPv4”。iOS NWParameters 的约束适用于其连接与监听器，不自动传给 WKWebView 或 C 引擎。[S01-07](../research/sources-platform.md)

## 证明方法

用不同网段的 Wi-Fi 和 Ethernet，同时保持蜂窝开启：两边都放可识别的 TCP/UDP/DNS 服务。检查服务器看到的源地址，并在两侧网关抓包或记录流量；同一公网出口的两张网卡不能仅靠公网 IP 区分。补测同子网、无默认路由、拔线、DHCP 地址更新。每种工具的所有连接都要有证据，UI 标签不算证据。
