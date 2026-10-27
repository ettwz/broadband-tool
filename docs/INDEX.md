# 文档总索引

doc_id: INDEX · 2026-10-27 · 状态：0.1.5 开发测试 APK 已交付

先读索引，再读相关文件。规划规格与实际验证记录分别维护；当前实现见 D07，逐项验证状态及 OnePlus 13 人工反馈见 D09。`建议`不等于已验证，未单独记录的专项场景继续保留。

## 产品与调研

| doc_id | 文档 | 解决的问题 |
| --- | --- | --- |
| P01 | [产品范围](product/brief.md) | 第一期做什么、什么算完成 |
| P02 | [需求对照](product/requirements.md) | 用户 11 项要求逐项落点 |
| P03 | [界面与流程](product/ux.md) | Device Lab 风格、页面结构、交互状态 |
| VIS01 | [GUI 渲染图](design/README.md) | v2 高级模式、授权和恢复状态；v1 基础页与提示词 |
| R01 | [平台可行性](research/platform.md) | 权限、Wi-Fi、网卡、系统限制 |
| R02 | [公网测速集成](research/speedtest.md) | 中科大与 FAST 的 API / 二进制 / 网页取舍 |
| R03 | [工具与 DNS](research/tools.md) | 内置引擎、ping、traceroute、DNS 缓存 |
| R04 | [技术选型](research/stack.md) | 跨端框架、组件、许可与替代方案 |
| R05 | [实测观察](research/observations.md) | 本轮真的访问了什么、证明了什么 |
| R06 | [高级权限方案](research/root-access.md) | KernelSU Next → Shizuku Root → 应用授权 |
| S01 | [平台来源](research/sources-platform.md) | Android、Apple 的一手依据 |
| S02 | [服务和组件来源](research/sources-services.md) | 测速、IP、工具、框架与参考产品 |
| S03 | [高级模式来源](research/sources-root.md) | Root、Shizuku、抓包、链路与系统配置依据 |

## 架构与功能

| doc_id | 文档 | 解决的问题 |
| --- | --- | --- |
| A01 | [模块与目录](architecture/overview.md) | 共享层、原生层、未来目录所有权 |
| A02 | [公共契约](architecture/contracts.md) | 请求、事件、结果、错误、能力模型 |
| A03 | [网络选择](architecture/network-context.md) | Wi-Fi 默认、有线切换、DNS 与实际出口 |
| A04 | [运行与恢复](architecture/lifecycle.md) | 并发、取消、后台、历史、异常退出 |
| A05 | [高级执行边界](architecture/privileged-service.md) | 权限状态、UserService、接口与白名单操作 |
| A06 | [临时配置与恢复](architecture/network-recovery.md) | 30 秒确认、会话期限、失权与恢复失败 |
| F01 | [Wi-Fi 检查](features/wifi.md) | 信号对比、信道图、扫描入口 |
| F02 | [局域网测速](features/lan-speed.md) | iperf3 client / server、TCP / UDP |
| F03 | [公网测速](features/internet-speed.md) | 国内 / 国际入口与网页边界 |
| F04 | [连通性工具](features/diagnostics.md) | ping、tcping、交互式 telnet |
| F05 | [路由追踪](features/trace.md) | 域名或 IPv4 输入、同网络解析、逐跳结果 |
| F06 | [DNS 检查](features/dns.md) | dig 风格、缓存分层、查询结果 |
| F07 | [网络概览](features/overview.md) | 类似 ifconfig 的集中展示 |
| F08 | [公网 IPv4](features/public-ip.md) | ip.sb、超时、出口标记 |
| F09 | [按网卡抓包](features/capture.md) | IPv4 + ARP、并发观察、文件与导出 |
| F10 | [ARP / DHCP 排障](features/link-diagnostics.md) | 地址占用、获取地址过程、无 IPv4 诊断 |
| F11 | [系统 DNS 缓存](features/system-dns.md) | 选定网络清理、系统接受与验证边界 |
| F12 | [有线临时配置](features/ethernet-config.md) | DHCP/静态、路由、MTU、期限与恢复 |

## 交付与任务

| doc_id | 文档 | 解决的问题 |
| --- | --- | --- |
| D01 | [路线图](delivery/roadmap.md) | 阶段、顺序、工作量和放行条件 |
| D02 | [Agent 执行规则](delivery/agent-guide.md) | Goal、subagent 分工、交接和防冲突 |
| D03 | [验收计划](delivery/acceptance.md) | 有代表性的输入、边界、真机证据 |
| D04 | [风险与决策](delivery/risks.md) | 不能承诺的能力与需用户决定的差异 |
| D05 | [本轮核查](delivery/review.md) | 文档交付完成依据 |
| D06 | [高级扩展核查](delivery/root-review.md) | 四项扩展、权限链、v2 视觉稿与验证记录 |
| D07 | [初版 APK 开发记录](delivery/implementation.md) | 当前进展、构建结果与未验证边界 |
| D08 | [APK 安装与使用](delivery/apk.md) | 初版安装、主要入口、构建与设备限制 |
| D09 | [功能验证清单](delivery/validation.md) | 自动化结果、OnePlus 13 人工反馈与专项待办 |
| D10 | [公开源码准备](delivery/public-source.md) | 环境信息检查、公开资源与无历史源码导出 |
| TASKS | [任务索引](tasks/INDEX.md) | T00–T20 的依赖和最小阅读集 |

常见入口：研究 Wi-Fi 读 F01 + R01；研究公网测速读 R02 + F03；实现工具先读 A02 + A03 + 对应 F 文档；开始分配开发任务读 TASKS + D02。来源通过 S01 / S02 中的稳定编号引用，不把整份资料塞入 agent 上下文。

高级模式先读 R06 + A05；涉及配置写入必须再读 A06。四项功能分别读 F09–F12；最新范围以 P02 的 R12–R15 为准。D05 保留普通版规划交付快照，D06 记录这次扩展，不把旧结论当作撤销用户的新要求。
