# 11 项需求对照

doc_id: P02 · 原编号保持不变

“可行”指有合理公开技术路径，不代表已通过手机测试。具体输入、输出和边界以 F 文档为准。

| 原需求 | 一期 Android 交付范围 | iOS 路径 / 限制 | 规格 | 开发任务 / 验收 |
| --- | --- | --- | --- | --- |
| 1 Wi-Fi 功率图 | AP 列表、Comparison 时间曲线、Channels 重叠图；RSSI dBm；2.4/5/6 GHz 按能力显示 | 普通公开 API 不提供周边扫描，不承诺同等图表 | [F01](../features/wifi.md) | T07 / V01 |
| 2 局域网与公网测速 | iperf3 s/c，TCP/UDP；中科大与 FAST 独立入口；先查原生集成再选网页 | libiperf 可移植候选；网页出口约束另验 | [F02](../features/lan-speed.md)、[F03](../features/internet-speed.md)、[R02](../research/speedtest.md) | T02、T03、T08、T11 / V02、V03 |
| 3 telnet/ping/tcping | 随包内置编译引擎、可视化参数、结果；telnet 是可输入命令的会话 | 共享表单与模型，Swift/C 适配；不依赖派生 shell 进程 | [F04](../features/diagnostics.md)、[R03](../research/tools.md) | T02、T09 / V04 |
| 4 Wi-Fi 默认与有线网卡 | 枚举可用网络、明确选择、逐连接绑定、拔线停止；不暗中走蜂窝 | NWParameters / BSD socket 接口适配；USB 网卡依赖系统驱动 | [A03](../architecture/network-context.md) | T01、T05 / V05 |
| 5 双端架构 | Flutter 共享界面 / 业务；Kotlin 网络适配；iOS 可编译边界验证列为早期门槛 | 二期 Swift 接入；系统能力差异无法靠换框架消除 | [R04](../research/stack.md)、[A01](../architecture/overview.md) | T01–T04、T13 / V06 |
| 6 curl ip.sb 模式 | 选定网络查询公网 IPv4，使用专用纯文本 API | 共享服务契约，HTTP 出口需平台实现 | [F08](../features/public-ip.md) | T06 / V07 |
| 7 traceroute | 输入域名或 IPv4；域名按所选网络解析为 IPv4 后逐跳探测、超时星号、停止 | 可行性待真机验证；不将 ICMP ping 成功等同路由追踪成功 | [F05](../features/trace.md) | T02、T09 / V08 |
| 8 dig 与清 DNS 缓存 | 普通模式为 dig、应用缓存/绕过；高级模式增加当前网络系统缓存清理，见 R14 | 不承诺清整机缓存 | [F06](../features/dns.md)、[F11](../features/system-dns.md) | T10、T18 / V09、V16 |
| 9 简洁科技感 GUI | 参考 Device Lab 的分组卡片、清晰数据层级；浅/深色、清楚的状态 | 共享视觉组件，遵守平台交互习惯 | [P03](ux.md) | T04、T12 / V10 |
| 10 Goal / subagent 文档 | 分层索引、任务卡、依赖、目录所有权、验收与交接模板 | 同一文档体系扩展 iOS | [TASKS](../tasks/INDEX.md)、[D02](../delivery/agent-guide.md) | 本轮交付 / V11 |
| 11 类 ifconfig 概览 | 网卡、IPv4/前缀、网关、DNS、MTU、连接状态、出口、Wi-Fi 细节 | 公开 API 能返回的字段；缺失带原因 | [F07](../features/overview.md) | T05、T06 / V12 |

普通模式系统 DNS 清理与 iOS 周边 Wi-Fi 扫描仍受平台限制。用户已明确增加下面的 Android 高级范围，系统缓存不再只有“应用缓存替代”这一条规划路径；但文档完成不等于手机已实现。iOS 架构与后续任务仍保留。

## 已增加的首批高级需求

统一前提：KernelSU Next 提供 root，Shizuku 以 Root 模式运行并授权本 App，UserService 与各项能力检查通过。ADB 模式不能视为已满足前提。完整契约见 [A05](../architecture/privileged-service.md)。

| 编号 | 功能与边界 | 规格 | 任务 / 验收 |
| --- | --- | --- | --- |
| R12 | 指定网卡抓 IPv4 + ARP、协议摘要、限额、导出；支持观察本 App 的诊断，不声称 HTTPS 解密或全网可见 | [F09](../features/capture.md) | T14–T16、T20 / V13、V14 |
| R13 | 单目标 ARP 占用/网关检查；本设备 DHCP 观察和有限 Discover；无 IPv4 也能检查 | [F10](../features/link-diagnostics.md) | T14、T15、T17、T20 / V13、V15 |
| R14 | 清理当前网络的 Android 系统解析器缓存；应用/上游缓存分别说明 | [F11](../features/system-dns.md) | T14、T15、T18、T20 / V13、V16 |
| R15 | USB 有线临时 DHCP/静态地址、路由、MTU、重新连接；预览、30 秒确认、会话到期恢复与失败处理 | [F12](../features/ethernet-config.md)、[A06](../architecture/network-recovery.md) | T14、T15、T19、T20 / V13、V17 |

新增部分仅 Android；iOS 显示平台不支持，不引入 Shizuku 或越狱方案。完整 Android 首批高级交付需 T20 放行，T12 仅证明普通功能基线。
