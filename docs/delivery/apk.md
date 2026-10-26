# APK 安装与使用

doc_id: D08 · 版本：0.1.4（开发测试版）

应用显示名称：`broadband-tool`。安装文件：仓库 `dist/broadband-tool-0.1.4.apk`；校验文件：`dist/SHA256SUMS`。支持 Android 9 及以上的 arm64 手机和 x86_64 模拟器，不包含 32 位设备。使用开发签名；不包含账号、广告和云端历史同步。

## 使用入口

1. 首页先选网络，查看 IPv4、网关、DNS 和接口。工具使用选定网络；网络失效会停止任务。
2. Wi-Fi 页允许系统要求的定位权限后扫描。可筛选名称、频段和信号，查看信道分布，最多选择 8 个接入点对比。信道图双指可将横轴放大到 8 倍，放大后左右拖动；纵轴信号刻度固定，上下滑动滚动页面；也可使用加减按钮和“复位”。扫描刷新保留视野，切换频段恢复全图。
3. 测速页进入 iperf3 客户端或本机服务端，另一台设备也需运行 iperf3。公网页分别打开中科大和 FAST，开始前会提示流量消耗，关闭页面停止连接。
4. 工具页提供 Ping、TCPing、Telnet、路由追踪和 DNS。路由追踪可直接输入域名或 IPv4；域名先通过所选网络解析，结果显示实际追踪的 IPv4。历史入口在右上角；应用退出时未结束的任务会标为中断，不自动重跑。
5. 高级页通过 Shizuku 授权。必须是 Root 模式；仅 ADB 模式不满足抓包要求。抓包可与普通诊断同时运行，停止后可通过系统文件选择器导出 PCAP。

Android 9/14 已完成所列模拟器与受控网络检查；维护者确认 OnePlus 13 上全部已开放功能使用正常。系统 DNS 清理需要 Shizuku Root 与兼容接口。**有线配置写入仍未开放**；网卡、频段、故障注入及 Private DNS 组合等专项另行记录。逐项状态见 [D09](validation.md)，开发证据见 [D07](implementation.md)。

## 构建与检查

在 WSL 仓库根目录执行：

```bash
python3 tools/bootstrap_wsl.py
source tools/wsl-env.sh
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-36" "build-tools;36.0.0" "ndk;28.2.13676358" "cmake;3.22.1"
bash tools/check.sh
bash tools/build_apk.sh
```

构建脚本强制使用正常应用入口，生成双架构 APK，检查签名和 ZIP 对齐。它使用项目本地固定工具链与依赖锁，不需要读取 `.env` 或生产凭据。

首次安装 SDK 组件时按 `sdkmanager` 提示处理许可证。WSL 需已有 Python 3、curl、tar、gcc 和 make。Gradle 启动脚本及 wrapper JAR 随源码提交；iperf 二进制由构建脚本生成，不随 Git 提交。源码也不包含上游的 PEM 认证测试文件。

设备测试位于 `apps/mobile/integration_test/`。HTTPS 测试先启动 `tools/https_test_fixture.py`，通过 ADB 反向映射 19443 和 18892；仅生成临时的公开测试证书，私钥保留在测试进程内存。测试后删除它生成的两个 debug 资源再构建交付包，构建脚本会检查残留。

系统 DNS 专项测试：在已具备 Root 的 Android 14 测试设备上运行 `BROADBAND_DEVICE=IP:PORT bash tools/test_system_dns.sh`。它创建两张无接口的临时网络和回环 DNS 响应器，验证系统缓存命中、单网络清理及网络间隔离，并清理自己创建的对象；不修改现有网络的 DNS 配置。AIDL 源码生成/一致性检查使用 `python3 tools/generate_dns_aidl.py` / `--check`。
