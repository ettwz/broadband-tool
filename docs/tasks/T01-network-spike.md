# T01 双网卡与跨端边界验证

doc_id: T01
status: planned
depends_on: [T00]

必读：[R01](../research/platform.md)、[A03](../architecture/network-context.md)、[D03](../delivery/acceptance.md)。所有权：`spikes/network/`、`docs/evidence/T01/`；不写正式共享契约。

创建最小验证工程，枚举 Wi-Fi/Ethernet，通过相应网络执行 TCP、UDP、DNS、HTTPS 与 IPv4 监听；保留蜂窝开启。Wi-Fi 和有线使用不同实验网段，服务端及网关记录实际源地址/流量。先验证逐 socket 绑定，再判断是否需要进程隔离。

典型边界：Wi-Fi 只有 LAN、无 IPv4、有线拔插、接口 IP 更新、同子网双网卡、VPN/代理、Android 局域网权限拒绝。没有驱动的网卡应显示系统未识别，不能误报 App 切换成功。

iOS 早期验证：查验 NWParameters 与 C socket 的接口边界；有 macOS CI / 真机时完成最小 Swift 网络绑定与共享 C 库链接验证。无资源则明确留下外部证据缺口，Android 结果不能证明 iOS 编译成功。

产出：设备/系统/网卡清单、每种流量的源地址和网络侧证据、失败记录、推荐绑定适配接口、iOS 契约缺口。验收：不同目标可分别从所选网络访问；断网绝不改走蜂窝；DNS 与连接同出口；V05 关键项有真实证明。

门槛：Android 绑定不成立则先调整底层方案；不能让 T05 继续用 UI 选择代替真实绑定。交接给 T04 / T05 具体 socket 创建时机和句柄失效规则。
