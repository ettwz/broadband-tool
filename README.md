# broadband-tool

家庭网络调试移动应用。第一期面向 Android、IPv4，架构预留 iOS。

普通功能免 root；Android 可选高级模式新增按网卡抓包、ARP / DHCP 排障、当前网络系统 DNS 缓存清理、USB 有线临时配置。高级环境按 **KernelSU Next 提供 root、Shizuku 以 Root 模式管理本应用访问**设计。

当前已提供 **0.1.4 开发测试 APK**。四个主页面、普通网络工具、iperf3、网页测速、Shizuku 抓包和 ARP/DHCP 已实现。系统 DNS 清理已在安卓 14 通过受控验证，需 Shizuku Root 和兼容的系统解析器接口；有线配置写入仍保持关闭。进展与证明边界见 [开发记录](docs/delivery/implementation.md)，安装与使用见 [APK 说明](docs/delivery/apk.md)。

OnePlus 13 上全部已开放功能已由维护者人工验证通过，具体范围与专项待办见[验证清单](docs/delivery/validation.md)。公开源码前请按[公开准备说明](docs/delivery/public-source.md)检查环境信息并导出不含旧 Git 历史的源码包。

工程采用 [Apache License 2.0](LICENSE)。第三方组件保留各自的许可和来源记录。

WSL 构建入口：`python3 tools/bootstrap_wsl.py` 准备固定工具链，`bash tools/check.sh` 执行本地检查，`bash tools/build_apk.sh` 生成 `dist/broadband-tool-0.1.4.apk` 和 `dist/SHA256SUMS`。工具链、缓存、临时证书均留在 Linux 文件系统；初版使用开发签名，不作为商店发布包。

- [文档总索引](docs/INDEX.md)：按问题找到最小阅读集。
- [产品范围](docs/product/brief.md)：目标、完成标准和关键限制。
- [GUI 渲染图](docs/design/README.md)：v2 重构工具入口，补齐授权、四项高级功能和恢复状态；保留 v1 基础页参考。
- [11 项需求对照](docs/product/requirements.md)：功能、平台差异、验收与任务映射。
- [技术选型](docs/research/stack.md)：Flutter、原生网络层及内置工具的取舍。
- [高级权限方案](docs/research/root-access.md)：KernelSU Next / Shizuku 的分工与能力检查。
- [临时配置与恢复](docs/architecture/network-recovery.md)：确认期限、自动恢复和失权后的处理边界。
- [开发任务索引](docs/tasks/INDEX.md)：依赖、负责目录和完成条件。
- [Goal / subagent 执行说明](docs/delivery/agent-guide.md)：后续开发的任务分配与交接规则。
- [本轮交付核查](docs/delivery/review.md)：调研验证结果与尚需真机验证的边界。

Wi-Fi 周边扫描在普通 iOS 应用中不可等同实现；系统 DNS 清理仅在 Android 高级模式经适配后开放，也不清理其他应用/上游缓存；公网网页测速的网卡绑定和 IPv4 限制必须先通过专项验证。详见上述文档。
