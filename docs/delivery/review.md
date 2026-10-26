# 本轮规划交付核查

doc_id: D05 · 2026-10-22

本页保留最初普通模式规划交付的核查快照。用户之后新增的四项 Android 高级功能、KernelSU Next + Shizuku 路径、任务与 GUI v2 以 [D06](root-review.md) 为准，不能用本页的历史范围排除新要求。

**交付性质：产品规划、功能拆解、可行性调研；没有应用开发。** 仓库原先只有项目名 README，本轮更新 README 并新增 docs 文档体系。没有改动用户已有环境/IDE 文件，没有提交或推送。

## 原目标逐项核查

| 原需求 | 本轮已交付的依据 | 当前结论 |
| --- | --- | --- |
| 1 Wi-Fi 图表 | [F01](../features/wifi.md)，Android 官方扫描和 ScanResult、NetSpot 手册 | 两类图、数据与限制已定义；真机未验 |
| 2 测速 | [R02](../research/speedtest.md)、[F02](../features/lan-speed.md)、[F03](../features/internet-speed.md) | s/c 均保留；两站 API/二进制/网页均调查；网页严格出口待验证 |
| 3 内置工具 | [R03](../research/tools.md)、[F04](../features/diagnostics.md) | 随包引擎方案及表单/交互/统计已定义；不依赖外部终端 |
| 4 网卡切换 | [A03](../architecture/network-context.md)、T01/T05 | 默认 Wi-Fi、有线选择、DNS/连接一致、拔线行为和证明方法已定义 |
| 5 双端 | [R04](../research/stack.md)、[A01](../architecture/overview.md)、T13 | 有框架比较、推荐组件、共享边界和早期 iOS 验证任务 |
| 6 公网 IP | [F08](../features/public-ip.md)、[R05](../research/observations.md) | 实际查询验证首页 UA 差异和 IPv4 API；手机路径未验 |
| 7 traceroute | [F05](../features/trace.md)、T02/T09 | 单 IPv4 输入、默认参数、逐跳语义与非特权风险已定义 |
| 8 DNS 与清理 | [F06](../features/dns.md)、[R03](../research/tools.md) | 查询语义完整；全系统清理受限明确记录，未假定用户接受替代 |
| 9 GUI | [P03](../product/ux.md) | 核对 Device Lab 官方截图，给出页面/流程/视觉/状态规则 |
| 10 agent 文档 | [INDEX](../INDEX.md)、[TASKS](../tasks/INDEX.md)、[D02](agent-guide.md) | 分层索引、14 张任务卡、依赖、目录所有权、验收和交接 |
| 11 网络概览 | [F07](../features/overview.md)、T06 | 字段、不可获取状态、多网卡/多地址与对照验收已定义 |

## 本轮验证

使用 Python 标准库检查 README 与 docs 下全部 Markdown：本地文件链接存在、doc_id 唯一、每份文档进入索引、任务依赖目标存在且无环、14 张任务卡均含所有权/产出/验收、原需求编号恰好为 1–11。另检查行尾空白及 `git diff --check -- README.md docs`。

规划交付时的检查快照：44 份 Markdown（README + 43 份文档），43 个唯一 doc_id，14 个任务节点；本地链接、索引、依赖与空白检查通过。来源包括 20 项平台资料和 17 项服务/组件资料组，另有 9 项只读观察；来源数量不代表移动端验证数量。后续增加的 GUI 视觉稿见总索引 VIS01。

实际外部验证只覆盖 R05 列出的有限 HTTP 请求、公开代码阅读和参考截图查看。没有公网带宽测量、没有 Android/iOS 安装运行、没有 USB 网卡或 Wi-Fi 射频实测。文档未把这些未执行的项目写成通过。

## 尚未解决的是产品开发门槛，不是遗漏的文档

普通 iOS 无通用周边 Wi-Fi 扫描；普通应用不能承诺清全系统 DNS 缓存；WebView 网卡/IPv4、非特权 traceroute、iperf3 移植与取消需要先验证。相关风险、失败分支、范围决定和负责的开发任务均在 [D04](risks.md)。这些结论保留原需求，不以推荐替代方案冒充已经满足。
