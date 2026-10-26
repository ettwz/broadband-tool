# 技术选型建议

doc_id: R04 · 状态：推荐，开发前经 T00 / T04 锁定

已增加的 Android 高级模式采用 Shizuku API / UserService + JNI，前置环境为 KernelSU Next 提供 root，见 [R06](root-access.md)。不改变 Flutter 共享界面和普通权限基础功能；iOS 不接入此高级执行层。

| 方案 | 对本项目的优点 | 成本 | 选择 |
| --- | --- | --- | --- |
| Flutter + Kotlin/Swift + C | 表单、图表和状态共享；原生网络权限与 socket 可独立适配 | 需要维护平台桥接，不是全 Dart 项目 | 推荐 |
| KMP + Compose Multiplatform | Kotlin 接近 Android 网络开发，iOS 共享 UI 已有稳定支持 | C/Swift 互操作和跨端组件仍需专项适配 | 合理备选；团队以 Kotlin 为主时重评 |
| Android/iOS 各自原生 | 最大平台控制力 | 两套界面与业务流程，后续重复较多 | 不符合尽量避免完整重做的偏好 |

选择理由是本项目共享界面较多，同时网络底层不可避免地依赖平台。框架本身不能解决 iOS Wi-Fi 扫描限制。依据：[S02-13、14](sources-services.md)。

## 推荐组成

| 层 | 候选 | 使用边界 |
| --- | --- | --- |
| 界面 | Flutter Material 组件，按 P03 定制 | 不把 Android Network 类型暴露给页面 |
| 状态 / 导航 | Riverpod / go_router | 状态容器不能代替任务生命周期管理 |
| 图表 | fl_chart；信道带宽多边形可用 CustomPainter | MHz 作为计算坐标；不是截图或网页图表 |
| 平台桥接 | Pigeon + 事件通道 | 结构化请求、事件、错误；生成文件只有一位 owner |
| 平台实现 | Android Kotlin + NDK；iOS Swift + C | 所有诊断 socket 和 DNS 都经过网络上下文 |
| 内置引擎 | libiperf、c-ares；libtelnet 候选 | 从源码构建并随包提供，版本与许可证逐项记录 |
| 高级执行 | Shizuku API / UserService、libpcap、受控 ARP/DHCP 引擎、系统网络适配 | 仅 Android，按能力开放；不是通用 root shell，来源见 S03 |
| 终端 | xterm.dart 候选 | 中文输入、ANSI、长输出和移动键盘先验；协议不交给 UI |
| 网页 | webview_flutter + 必需的平台扩展 | 共享插件没有承诺的接口不假定存在 |
| 本机记录 | 有版本的 JSON 文件，原子替换 | 默认最多 100 次 / 5 MB 摘要；不引入服务器 |

组件依据：[S02-07–10、15–17](sources-services.md)。现阶段不锁死“最新”版本；T04 记录 SDK、NDK、依赖版本、源码哈希、锁文件和许可证。libiperf 为 BSD-3-Clause，c-ares 为 MIT，当前 libtelnet 仓库为 public domain；复制、修改或替换后要检查实际分发文件。不开源整个项目的商业模式尚未决定，不引入无法说明分发义务的整套工具箱。

Android 首发 arm64-v8a，x86_64 用于模拟器；armv7 仅在用户设备需要时增加。原生库需验证 16 KB 页兼容。iOS 预留 arm64 真机 / 模拟器构建，使用 macOS CI / Xcode；本轮在 WSL 不进行 iOS 构建。系统最低版本、包体与性能预算在基础工程任务中锁定，不能靠插件版本号推断真机可行。
