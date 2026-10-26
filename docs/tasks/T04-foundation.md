# T04 工程、公共契约与设计基础

doc_id: T04
status: planned
depends_on: [T01, T02]

必读：[R04](../research/stack.md)、[A01](../architecture/overview.md)、[A02](../architecture/contracts.md)、[A04](../architecture/lifecycle.md)、[P03](../product/ux.md)。所有权：Flutter scaffold、`lib/app/`、`lib/design/`、`packages/net_contract/`、`native/core/` 和依赖清单。

接收 T01/T02 结论后建立最小双平台工程。锁定 SDK/NDK、依赖与许可，生成类型化桥接；建立能力模型、任务事件/终态、统一错误、任务调度、取消和本机历史。调度按 A04 预留一个被动抓包及独占配置阶段，具体高级接口由 T15 串行扩展。iOS 对 Root 能力返回 unsupported，不能依赖未实现异常。

设计基础覆盖四主入口、网络选择栏、表单、结果卡、空/错误/权限状态、浅深色。实现 P03 的尺寸和可访问性；只用明确标记的 fixture 做开发预览，生产路径不能显示假结果。

产出：可运行基础工程、公共契约 v1、贡献目录边界、锁文件、源码/许可证清单、测试入口。建议验证：工程建立后的 `flutter analyze`、契约与生命周期定向 `flutter test`、Android debug 构建；iOS macOS CI 构建结果单列。

验收：双平台模型生成无漂移；重复 requestId 不重复运行；终态只发一次；取消/网络丢失可表达；历史损坏可恢复；大字体不遮挡主要操作。无 macOS 证据不得宣称 iOS 构建已过。生成文件与共享依赖只由此 owner 或集成人改动。
