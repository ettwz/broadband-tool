# T08 iperf3 双角色测速

doc_id: T08
status: planned
depends_on: [T05]

必读：[F02](../features/lan-speed.md)、[A03](../architecture/network-context.md)、[A04](../architecture/lifecycle.md)、T02 交接。所有权：`native/iperf/`、`lib/features/lan_speed/`、平台 `iperf/` 适配及对应测试；第三方锁清单由集成人更新。

按 T02 已验证方案封装 libiperf，接入选定网络的全部 socket。实现 client/server、TCP/UDP、正反向、表单范围、每秒指标和最终结果。服务端只绑定选定 IPv4，等待一场测试，超时及后台停止。

完成时间与字节计量，区分发送/接收侧，TCP 重传和 UDP 丢包/抖动只在引擎提供时展示。不能把单位格式化字符串当作数据模型。应用取消不允许调用会结束整个进程的上游退出路径。

验收 V02：PC 与手机双角色、TCP/UDP、反向、不同网络；与对端摘要核对；端口冲突、对端中断、拔线、锁屏、重复启动停止；停止后端口释放且网络无流量。测试 16 KB 页设备/环境上的原生加载。

产出：引擎版本/补丁、设备记录、双方日志、吞吐基线和误差解释。源码可以编译或只通过 client 不算双角色完成；服务端“监听成功”还需 PC 实际连接测试。
