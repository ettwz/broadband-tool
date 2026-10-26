# T16 按网卡抓包

doc_id: T16
status: planned
depends_on: [T15]

必读：[F09](../features/capture.md)、[A04](../architecture/lifecycle.md)、[A05](../architecture/privileged-service.md)。所有权：`native/advanced/capture/`、高级 `capture/` UI、平台适配和对应 fixture；依赖锁由集成人更新。

集成经 T14 验证的 libpcap / 工具后端，生成结构化预设过滤条件，保留实际链路类型；IPv4 模式包含 ARP。实现接口选择、采样长度、时间/容量上限、摘要、丢包计数、结束文件与导出。

允许与一个普通诊断任务并行，显式记录对测速的影响；DHCP 被动观察复用捕获资源。配置写入、失权、拔线、切后台均清理会话，未完整关闭的文件有标记。文件不经公开路径或自动上传流出。

产出：随包引擎/许可、pcap/pcapng 样本、Wireshark 打开证据、内存/空间/丢包/停止检查。验收 V14：真实选卡、DNS/ARP/DHCP/TCP 数据、无 IPv4 接口、VPN 共存、容量限制、与普通诊断并发、授权撤回；停止后无遗留进程/文件写入。不得用 VPN 转发的重组数据冒充原始接口捕获。
