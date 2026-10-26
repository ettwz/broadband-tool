# T19 有线临时配置与恢复

doc_id: T19
status: planned
depends_on: [T15, T17]

必读：[F12](../features/ethernet-config.md)、[A06](../architecture/network-recovery.md)、T14 恢复证据。所有权：`native/advanced/ethernet/`、高级 `ethernet/` UI、事务记录和恢复测试；共享调度由集成人合入。

实现 DHCP/临时静态、目标路由、MTU、接口重连；复用 T17 地址占用结果但不把无回应当安全保证。系统 Ethernet 管理与内核一致性必须验证。预览/快照落盘后才写入；30 秒未确认恢复；确认只保留有限会话，默认 5 分钟。

完成接口身份、网络/权限 generation、事务幂等、正常退出恢复及异常待恢复流程。只回退本任务改动，系统/用户的新配置不能被旧快照覆盖。不同设备复用同一接口名时阻止误恢复。

产出：原/现配置对照、正常和失败状态截图、DHCP 恢复日志、崩溃/失权/拔线实验、文件损坏与重启核查。验收 V17：直连空网关、地址/路由/MTU 读回、未确认/到期恢复、原模式恢复且可联网、冲突检测、Shizuku 退出、Root 撤回和 App 杀进程。

不能验证有界恢复时保持只读；权限丢失无法恢复时显示 recovery_pending。不可改用直接 su 绕过指定授权方案，不宣称 KernelSU Next / Shizuku 自动提供网络配置回滚。
