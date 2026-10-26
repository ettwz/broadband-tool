# T05 网络基础与权限

doc_id: T05
status: planned
depends_on: [T04]

必读：[A02](../architecture/contracts.md)、[A03](../architecture/network-context.md)、[R01](../research/platform.md)、T01 交接证据。所有权：`packages/net_platform/android/network/`、`android/permissions/`、共享网络选择组件及对应测试；共享 manifest 由集成人合入。

实现网络枚举/回调、Wi-Fi 默认、有线主动选择、权限按需请求、IPv4 策略、socket/DNS 工厂、句柄 generation。系统默认网络、用户选择与 Internet validated 状态分别暴露。无公网的 Wi-Fi 仍可用 LAN；无 IPv4 不回退 IPv6。

实现运行中切换的停止/清理顺序、断网错误、VPN/代理可见状态。所有引擎接入同一接口，不允许功能模块创建不绑定的 Dart/Java HTTP 或 DNS 客户端。

产出：稳定 NetworkContext API、受控网络集成测试、真实双网卡证据、权限版本矩阵。验收 V05：TCP/UDP/DNS/HTTPS/监听均验证出口；有线拔线时没有蜂窝流量；旧句柄不复用；系统权限撤销可恢复。

典型边界：只有 LAN、有线 DHCP 等待、多 IPv4、两接口同子网、门户网络。T01 的实验结果必须在正式插件路径复验，不能直接把 spike 日志当成此任务实现证据。交接给 T06–T11 网络接口的调用时机与失败含义。
