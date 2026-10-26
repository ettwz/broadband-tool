# T09 常用诊断与路由追踪

doc_id: T09
status: planned
depends_on: [T05]

必读：[F04](../features/diagnostics.md)、[F05](../features/trace.md)、[R03](../research/tools.md)、T02 交接。所有权：`native/probes/`、`native/telnet/`、`lib/features/diagnostics/`、`lib/features/trace/` 和平台对应适配。

内部按四个交付小项推进：T09.1 ping、T09.2 tcping、T09.3 telnet、T09.4 traceroute。每项使用同一任务和网络契约，分别有验证结果；若多人并行，主 agent 必须再划分上述子目录与终端组件所有权。

实现结构化表单、限时/限次探测、统计、取消。Telnet 包含协议协商和真实交互终端；traceroute 接受域名或 IPv4，按所选网络解析域名并固定实际 IPv4 目标；完整路径验收仍需受控多跳拓扑的实际响应。

验收 V04/V08：ICMP 有/无响应；TCP 成功/RST/丢弃；telnet IAC、中文、回车、退格、断线、Ctrl-C；路由中间超时仍继续、末端到达、最大跳数和非法输入；逐工具证明选卡与停止。

产出：四小项的结果表、真实设备权限结论、路由拓扑证据、终端交互记录（脱敏）。任一小项不成立，T09 不标功能全完成。不可把设备不支持写成网络目标超时，不能用 tcping 代替 traceroute。
