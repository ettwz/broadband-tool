# T14 KernelSU Next / Shizuku 能力与恢复验证

doc_id: T14
status: planned
depends_on: [T00, T01]

必读：[R06](../research/root-access.md)、[A05](../architecture/privileged-service.md)、[A06](../architecture/network-recovery.md)、[S03](../research/sources-root.md)。所有权：`spikes/privileged/`、`docs/evidence/T14/`；不修改正式桥接。

使用用户已配置的 KernelSU Next + Shizuku Root 设备，记录设备、OS、内核、两软件版本。验证服务与 UserService 身份、授权撤回、Binder 死亡、ADB 模式错误、原始报文与系统网络服务权限。不能以安装 root 管理器代替执行身份检查。

四个最小实验：选网卡捕获 IPv4/ARP；无 IPv4 时观察 DHCP/ARP；调用指定网络系统 DNS 清理并用受控缓存验证；有线临时地址/MTU 的系统与内核一致性及恢复。配置实验使用可控实验网，原配置先保存。

专测 App 进程死亡、Shizuku 停止/重启/撤权、接口拔插/复用和恢复失败，明确 UserService 的实际存活范围。证明定时清理不能仅依赖页面；权限丢失时无法自动恢复必须返回待恢复状态。不得增加直接 su 备用通道或要求关闭 SELinux。

产出：能力矩阵、调用方式/版本适配边界、配置生命周期证据、支持和不支持条件、T15–T19 接口建议。验收 V13–V17 的关键技术路径有真实证据。调查失败可交报告，但相应功能仍不放行；没有真机则明确等待证据，不能用 WSL Linux 测试替代。
