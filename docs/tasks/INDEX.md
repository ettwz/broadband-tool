# 开发任务索引

doc_id: TASKS · 所有任务均为 planned，未执行开发

每张卡有依赖、必读、目录所有权、产出、验收和失败处理。共 21 张卡，人日为粗估。T00–T12 是 Android 普通基线；T14–T20 是已加入的 Android 首批高级模式；T13 是 iOS 二期，早期 iOS 验证仍在 T01 / T02。完整 Android 普通 + 高级交付需 T20 放行。

| 任务 | 依赖 | 建议人日 | 主要职责 |
| --- | --- | --- | --- |
| [T00 范围确认](T00-decisions.md) | 无 | 0.5–1 | 冻结目标与不可实现项的处理 |
| [T01 网络验证](T01-network-spike.md) | T00 | 2–4 | 双网卡/IPv4，iOS 接口与构建边界 |
| [T02 引擎验证](T02-engine-spike.md) | T00 | 4–7 | 无 root ping/trace、iperf 双角色与取消、iOS 编译方向 |
| [T03 网页测速验证](T03-webview-spike.md) | T00 | 3–6 | API 候选复核，WebView 选卡/IPv4/停止 |
| [T04 工程与契约](T04-foundation.md) | T01、T02 | 3–5 | 共享模型、桥接、设计系统、依赖锁定 |
| [T05 网络基础](T05-network.md) | T04 | 3–5 | 网络上下文、权限、socket/DNS 绑定 |
| [T06 概览与公网 IP](T06-overview-ip.md) | T05 | 2–3 | ifconfig 式展示、IP.SB、本机结果 |
| [T07 Wi-Fi](T07-wifi.md) | T05 | 3–5 | 扫描、Comparison、Channels |
| [T08 iperf3](T08-iperf.md) | T05 | 4–6 | 客户端/服务端、协议与统计 |
| [T09 诊断工具](T09-diagnostics.md) | T05 | 5–8 | ping/tcping/telnet/traceroute |
| [T10 DNS](T10-dns.md) | T05 | 3–5 | dig 风格查询、缓存语义 |
| [T11 公网测速](T11-internet-speed.md) | T05、T03 | 2–4 | 两站网页会话、路由和错误 |
| [T12 集成验收](T12-integration.md) | T06、T07、T08、T09、T10、T11 | 5–8 | 真机矩阵、端到端与发布检查 |
| [T13 iOS 交付](T13-ios.md) | T12 | 15–25 | 原生适配、系统差异、IPv6-only 发布评估 |
| [T14 Root 方案验证](T14-root-spike.md) | T00、T01 | 2–4 | KernelSU Next / Shizuku 身份、能力、撤权与恢复 |
| [T15 高级执行边界](T15-privileged-service.md) | T04、T05、T14 | 3–5 | 授权 UI、UserService、能力与幂等请求 |
| [T16 按网卡抓包](T16-capture.md) | T15 | 3–5 | 过滤、上限、诊断并发、文件与导出 |
| [T17 ARP/DHCP](T17-link-diagnostics.md) | T15、T16 | 3–5 | 复用捕获资源、占用检查、观察与有限 Discover |
| [T18 系统 DNS](T18-system-dns.md) | T15、T10 | 2–3 | 当前网络系统缓存清理及效果验证 |
| [T19 有线临时配置](T19-ethernet-config.md) | T15、T17 | 5–8 | 系统/内核配置、期限、恢复与故障处理 |
| [T20 联合验收](T20-root-integration.md) | T12、T16、T17、T18、T19 | 3–5 | 普通/高级共同运行与恢复证据 |

依赖完成指结论已被集成人接收。验证任务调查失败可以交付报告，但相关功能的开发/发布门槛仍未通过。例：T03 证明无法强制 IPv4 后，T11 不能只据“T03 已交接”就采用未获接受的降级。

同理，T14 已产出调查报告不代表所有高级能力可用；尤其 T19 写配置必须取得恢复证明。T13 不依赖 Android 专有的 T20，可在普通基线上独立推进。

所有权冲突规则见 [D02](../delivery/agent-guide.md)；全量验收见 [D03](../delivery/acceptance.md)。公共契约和权限清单只由集成人串行修改。
