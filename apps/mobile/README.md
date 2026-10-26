# broadband-tool

Flutter 界面、Android 网络实现和可选 Shizuku 高级功能。Android 最低版本为 9；iOS 工程保留接入边界，尚未完成构建和设备验证。

先从[文档索引](../../docs/INDEX.md)选择相关功能规格。[安装与构建说明](../../docs/delivery/apk.md)列出 WSL 工具链、首次 SDK 安装和检查命令，命令均从仓库根目录执行。

`lib/` 是共享界面与业务状态，`android/` 是 Android 实现，`pigeons/` 定义平台契约，`test/` 与 `integration_test/` 分别验证本地逻辑和设备行为。已验证范围及尚未开放的系统写入能力见[开发记录](../../docs/delivery/implementation.md)。
