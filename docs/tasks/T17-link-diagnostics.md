# T17 ARP / DHCP 排障

doc_id: T17
status: planned
depends_on: [T15, T16]

必读：[F10](../features/link-diagnostics.md)、[A05](../architecture/privileged-service.md)、[A04](../architecture/lifecycle.md)。所有权：`native/advanced/link/`、高级 `link/` UI 与对应实验服务；复用 T16 已交付的捕获资源和冻结接口，不抢改其模块或另起重复抓包器。

实现单目标 ARP 网关/占用检查，独立保存 IP/MAC 样本；被动 DHCP 时间线按接口和事务关联；主动 Discover 为独立按钮，有次数上限且不发送 Request/Release。无 IPv4 时仍能选择接口。

产出：报文解析和匹配测试、受控服务日志、ARP/DHCP 代表性页面、返回给 T19 的可复用地址占用结果接口。验收 V15：回应/不回应、多 MAC、两 DHCP 服务器、丢步骤、NAK、旧事务、跨接口噪声、取消/失权；“未观察到”不变成“设备不存在”。

必须证明探测没有修改当前接口配置、发送 Request/Release 或完成额外租约确认；服务端因 Offer 短暂预留地址的可能性需说明，不能承诺零副作用。没有 ACK 不显示已经获得地址；多个 DHCP 服务器不自动标“恶意”。全网扫描与服务器功能不属于本任务。
