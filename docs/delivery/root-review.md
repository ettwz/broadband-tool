# 高级模式与 GUI v2 交付核查

doc_id: D06 · 2026-10-22 · 文档与静态视觉交付

用户要求把上轮首批四项能力纳入设计，并以 KernelSU Next 提供 root、Shizuku 管理访问为前提；本次已按此更新，不将它们保留为“待决定是否加入”。原文 KerbelSU Next 按官方产品名 KernelSU Next 理解。

| 要求 | 交付依据 | 状态边界 |
| --- | --- | --- |
| 按网卡抓包 | [F09](../features/capture.md)、T16、V14、v2 04 | 规格/任务/图片已具备，原始接口与真实文件待开发验证 |
| ARP / DHCP | [F10](../features/link-diagnostics.md)、T17、V15、v2 05–06 | 无 IPv4 和缺少 ACK 语义已覆盖，不扩展全网扫描 |
| 系统 DNS 缓存 | [F11](../features/system-dns.md)、T18、V16、v2 07 | 当前网络系统缓存与应用/上游分开，无通用成功保证 |
| USB 有线临时配置 | [F12](../features/ethernet-config.md)、[A06](../architecture/network-recovery.md)、T19、V17、v2 08–12 | 预览/确认/期限/恢复/失权均有定义，恢复能力未实测 |
| KernelSU Next + Shizuku | [R06](../research/root-access.md)、[A05](../architecture/privileged-service.md)、T14/T15、V13、v2 01–03 | Root 提供、服务身份、应用授权、逐项能力分别表达 |
| 页面重构 | [VIS01](../design/README.md) | 四张 v2 PNG、十二个界面；原 v1 三图保留为参考 |
| 分层开发文档 | [P02](../product/requirements.md)、[TASKS](../tasks/INDEX.md)、[D03](acceptance.md) | 原 11 项 + R12–R15；T00–T20；V01–V17 |

## 核查范围

检查本地链接、doc_id 与目录索引、21 张任务卡依赖无环、任务卡所有权/产出/验收、原始与新增需求覆盖、四张 v2 PNG 可读取且尺寸正确、四份提示词完整、旧范围与新设计的冲突表述。`git diff --check -- README.md docs` 另检查变更空白。

检查结果：61 份 Markdown（含根 README）、60 个唯一 doc_id、265 个本地链接、21 个任务节点全部通过；任务索引中的依赖与任务卡一致，原 11 项 + R12–R15 和 V01–V17 完整。四张 v2 PNG 均为 1536 × 1024，文件块校验及图像数据解压通过，提示词存在；目视检查已完成。这里只证明文档和静态图片交付。

目视检查：未授权状态不标已获授权；Root 身份和具体功能可用分开；没有添加第五个底部导航；DHCP 无 ACK 不标已获取地址；系统 DNS 没有虚构条数；恢复失败页没有绿色完成提示。所有图均为示例数据。

## 证明边界

本轮查阅 KernelSU Next、Shizuku API、抓包与系统网络接口的一手资料，修改文档并通过内置 image_gen 生成静态 PNG。没有开发 App、安装 root、修改设备配置或执行真实清缓存/抓包。新任务全部 planned；Root 真机、系统适配、配置恢复和 GUI 实际交互均属于未来验收，不标已通过。

本轮不提交、不推送。原有用户环境文件和 v1 图保留。D05 的旧数量是历史快照；当前清单以总索引和本页所引用的文档为准。
