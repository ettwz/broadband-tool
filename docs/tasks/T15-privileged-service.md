# T15 高级授权与执行边界

doc_id: T15
status: planned
depends_on: [T04, T05, T14]

必读：[A05](../architecture/privileged-service.md)、[A04](../architecture/lifecycle.md)、[P03](../product/ux.md)、T14 证据。所有权：`android/privileged/`、`lib/features/advanced/access/`、高级入口和共享高级测试；契约/生成代码由集成人串行更新。

实现 Shizuku 服务发现、按需授权、Root/ADB 区分、UserService 绑定、能力探测、generation 和断连/撤权处理。普通功能无需该服务；iOS 明确 unsupported。工具页基础/高级切换，授权页三层分工与 v2 图一致。

接口只包含 A05 白名单操作，参数/调用者/输出文件范围受控；UI 不持有通用 root shell。不因某一项能力成功就开放全部工具。UserService 的不完整 Android Context 由实际适配处理，不误用普通应用组件。

产出：类型化高级桥接、能力模型、授权状态 UI、只读与可执行分支、失联/撤权测试。验收 V13：未装、未启动、未授权、ADB、Root、被系统拒绝、服务重启全部状态可分辨；旧 generation 请求拒绝；权限恢复后不自动重放旧操作。T14 能力结果须在正式插件路径复验。
