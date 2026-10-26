# T02 内置引擎验证

doc_id: T02
status: planned
depends_on: [T00]

必读：[R03](../research/tools.md)、[F02](../features/lan-speed.md)、[F05](../features/trace.md)、[A04](../architecture/lifecycle.md)。所有权：`spikes/native/`、`docs/evidence/T02/`；上游源码/补丁只放验证目录。

以普通应用 UID、无 root 验证 ICMP ping 和多跳 traceroute；记录 socket 创建权限、TTL 错误返回、探测与响应匹配。用已知至少两跳拓扑证明中间跳和末跳，不能只测 127.0.0.1。

从上游源码构建 libiperf，测试手机 c/s、TCP/UDP、反向、开始/停止、对端异常退出。检查所有 socket 的绑定入口及进程级 signal、exit、全局变量影响；评估小补丁或独立工作进程哪种更可靠。重复启动取消 20 次，确认端口可重用且 App 不退出。

确认 c-ares socket 回调可以接入 A03，libtelnet 可与 UI 分离。验证 Android arm64 和 16 KB 页构建；iOS 通过 macOS CI 做静态链接与最低运行验证，资源缺失需明确记录。

产出：源码版本/哈希/许可、移植补丁范围、生命周期策略、成功与失败设备、原生适配契约建议。验收：V02 / V04 / V08 的底层关键路径有实证；没有将桌面 Linux 成功升级成 Android 成功。

门槛：某引擎失败则提出具体原因、替代技术和范围影响。T09 不得隐藏 traceroute 不可行；T04 只冻结已有证据支持的接口，能力缺口必须保留。
