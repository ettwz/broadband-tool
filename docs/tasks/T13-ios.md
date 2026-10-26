# T13 iOS 完整适配

doc_id: T13
status: planned
depends_on: [T12]

必读：[R01](../research/platform.md)、[R04](../research/stack.md)、[A01](../architecture/overview.md)、[A03](../architecture/network-context.md)、T01/T02 早期 iOS 结论。所有权：`packages/net_platform/ios/`、iOS 工程与对应测试；共享层变更需主 agent 审核。

复用 Flutter 页面/模型、C 引擎和测试 fixture，完成 Swift 权限、接口选择、网络信息、TCP/UDP/HTTPS、iperf3、DNS、ping/trace 适配。C socket 的接口约束独立实现，不能将 NWParameters 设置等同全部 C socket 被绑定。

周边 Wi-Fi 扫描明确不支持；已连接信息按公开 API 和权限展示。系统 DNS 清理仍不承诺。iOS 逐跳追踪与 WKWebView 选卡/IPv4 均需独立真机验证，不能继承 Android 通过状态。

R12–R15 的 KernelSU Next / Shizuku Root 模式仅 Android；共享入口在 iOS 返回平台不支持，不把越狱作为交付前提，不等待 T20 才可适配普通功能。

使用 macOS CI / Xcode 构建并在 iPhone/iPad 真机验证 USB-C 网卡和本地网络权限。评估 App Review 的 IPv6-only 要求：基础服务与界面在该网络可用，IPv4 专项诊断适当说明限制；必要地址族能力通过预留契约扩展，不让 Android 一期产生隐式 IPv6 回退。

验收：列出共享模块复用情况与新增平台代码；逐项能力真实，权限拒绝可恢复，前后台符合 A04，受支持工具的网卡出口和结果有证据。发布前核查当前规则；未通过的关键功能或规则不能宣称已具备 App Store 发布条件。

产出：iOS 测试包、支持差异表、设备/网络矩阵、复用与限制报告。此任务是后续阶段，本轮只完成任务设计。
