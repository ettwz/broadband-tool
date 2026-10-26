# T20 普通与高级模式联合验收

doc_id: T20
status: planned
depends_on: [T12, T16, T17, T18, T19]

必读：[P02](../product/requirements.md)、[D03](../delivery/acceptance.md)、[D04](../delivery/risks.md)、[A06](../architecture/network-recovery.md)、[VIS01](../design/README.md)。所有权：`tests/integration/advanced/`、`docs/evidence/T20/`；跨功能修复交各 owner 或集成人。

以 KernelSU Next + Shizuku Root 真机完成 V13–V17，并回归 V01–V12 的普通功能。无 root、未授权和 ADB 模式仍能使用原有功能；高级界面不能因一个绿色总开关误报全部可用。

完整链路：授权 → 检查能力 → 无 IPv4 接口观察 DHCP → ARP 检查 → 指定接口抓包并运行 DNS/ping → 导出 → 清当前网络系统 DNS → 预览临时有线配置 → 30 秒确认 → 临时诊断 → 结束恢复。补测关闭 Shizuku 后的恢复未完成页面及重新授权核对，不能只测正常路径。

产出：普通/高级分别标明的测试包和能力矩阵、验证设备/版本、原始网络证据、恢复证据、v2 GUI 对照。验收：四项功能均有实际证明；恢复未完成时无假成功；没有直接 su 隐藏后门或权限失联后重放操作；已接受的设备限制清楚标识。

T12 通过不能替代本任务。缺失真实 Root 设备/恢复证据时，完整高级交付未完成；不得重新缩小目标。提交、推送、发布仍按用户授权分别处理。
