# 内置工具与 DNS 可行性

doc_id: R03

“内置二进制”建议落实为随安装包提供的编译库和原生引擎：无外部工具依赖，GUI 驱动结构化参数。用户没有要求开放 shell；不拼接命令执行任意输入。Android 可写目录执行受限制，iOS 不能把桌面式进程执行当基础设计。[S01-08、17](sources-platform.md)

| 工具 | 建议集成 | 关键验证 |
| --- | --- | --- |
| iperf3 | 上游 libiperf 源码编译；Android JNI，iOS 静态库 / XCFramework 适配 | 双角色、控制与数据 socket 同出口、取消、无全局 signal/exit 副作用 |
| ping | 随包的非特权 ICMP 引擎，Android 优先 datagram ICMP；系统 ping 仅作调试对照 | 无 root 的真实 UID 权限、ICMP 被过滤与无权限区分 |
| tcping | 原生 TCP connect 计时 | 成功、RST 拒绝、超时分别统计；不是 ICMP RTT |
| telnet | 原生 TCP + libtelnet 协议解析；终端单独渲染 | IAC 协商、回显、CRLF、UTF-8/可选编码、断线和取消 |
| traceroute | Android UDP + 逐次 TTL + IP_RECVERR 为候选 | 收到中间路由 ICMP 错误并匹配探测；只发包不算完成 |
| dig 风格 | c-ares 查询 / 解析 + 原生绑定 socket；结构化展示 DNS 报文语义 | UDP 截断后 TCP、rcode、TTL、指定 DNS、缓存行为 |
| curl ip.sb 模式 | 绑定网络的 HTTPS 请求，纯文本 IPv4 解析 | 保持 TLS 校验、DNS 同出口、HTML/错误拒绝 |

不声称提供 BIND dig 的全部命令行兼容性。若用户要求原版 dig/curl/telnet 可执行文件本体，需 T00 再确认打包范围；当前方案保证对应功能内置，以可移植编译库满足使用需求。[S02-07–10](sources-services.md)

Linux IP_RECVERR 与 Apple 归档 SimplePing 只证明存在技术方向；不能替代现代 Android/iOS 真机上的权限和逐跳接收验证。T02 先行，失败后不得把 TCP 连通性改名 traceroute。[S01-12、13](sources-platform.md)

## DNS 缓存的四个层次

| 层次 | 本应用可做什么 |
| --- | --- |
| 本应用的查询缓存 | 清空、报告条数、下次查询重新发出 |
| 本进程 / 系统解析器 | Android 可按次绕过缓存；不能宣称清整机 |
| 路由器 / 上游递归 DNS | 查询可以到达它，但不能清理其缓存 |
| 权威 DNS 数据 | 本应用无权修改或刷新 |

AOSP 的系统 flushNetworkCache 有特权检查；Apple URLSession.flush 不提供全系统 DNS 清理保证。普通模式按钮叫“清除本应用 DNS 缓存”，同时提供“绕过缓存查询”。Android 高级模式已增加独立的“清理当前网络的系统 DNS 缓存”，经 Shizuku Root 与系统适配后执行，详见 [F11](../features/system-dns.md)。两者均不能承诺清理上游或其他应用私有缓存。[S01-09–11](sources-platform.md)
