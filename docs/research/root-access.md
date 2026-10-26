# Android 高级权限方案

doc_id: R06 · 2026-10-22 · 用户已指定高级模式，不替代普通模式

产品假设设备已通过 **KernelSU Next** 获取 root（用户消息中的 KerbelSU Next 按此名称理解），由 **Shizuku** 管理本应用的高级访问。App 不负责刷机或安装 root；iOS 不增加越狱路径。方案依据见 [S03](sources-root.md)。

权限链：KernelSU Next 向 Shizuku 提供 root → 用户在 Shizuku 中以 root 方式启动服务 → 用户授权本应用 → 本应用绑定 Shizuku UserService → 执行逐项能力检查。GUI 和普通功能仍在普通应用进程。默认不另走直接 `su` 或 Sui 通道，避免绕开用户选定的管理方式。

Shizuku 可以是 root 或 ADB 身份。必须同时核实服务身份和实际 UserService 执行身份；UID 0 是 root 身份，UID 2000 是 shell。ADB 模式显示“当前为 ADB 模式，需要以 Root 模式启动”，保留普通工具，不靠安装包名或一个 root 图标放行。KernelSU Next 为配置假设；无法可靠识别提供者时不能声称检测到了具体 root 方案。

## 首批加入范围

| 能力 | 路径 | 尚需证明 |
| --- | --- | --- |
| 指定网卡抓包 | UserService 内 JNI + libpcap / 随包工具；IPv4 + ARP | 原始 socket 权限、链路类型、停止、导出、VPN 共存 |
| ARP / DHCP 排障 | 绑定接口的 ARP、观察本设备 DHCP 与有限 Discover | 无 IPv4 接口、报文匹配、无额外租约副作用 |
| 系统 DNS 缓存清理 | 适配 Android 系统解析服务的按网络清理接口 | 特权调用成功，以及受控查询验证；不等于上游或应用私有缓存 |
| USB 有线临时配置 | 系统 Ethernet / netd 适配与必要的内核操作 | 系统配置与内核状态一致、30 秒确认、定时恢复、失权恢复边界 |

root 身份不保证 SELinux、capabilities、驱动、内核和系统服务都允许操作。每个功能分别返回能力和原因；不要求关闭 SELinux。Shizuku UserService 不是普通 Android 应用进程，不能假定 Context、广播、ContentResolver 可正常使用，需按版本适配。

libpcap 为优先抓包候选，tcpdump 可作对照或受控后端；来源与分发许可在任务中锁定。高级模式同样只随包提供所需引擎，不下载命令脚本，不暴露任意 root 终端。

本轮仅核查公开资料，未在 KernelSU Next + Shizuku 真机上执行。新增 T14 专门验证身份、撤权、系统调用和恢复。弱网、防火墙、PPPoE、VLAN、Wi-Fi monitor 均不因本次讨论自动加入首批。
