# DNS / dig 检查与缓存

doc_id: F06 · 原需求 8

本页为普通 DNS 查询。用户已增加 Android 高级入口 [F11 系统 DNS 缓存](system-dns.md)，两种缓存操作分别显示；高级入口走 KernelSU Next + Shizuku Root，不把本应用清理改名为系统清理。

提供域名、记录类型、DNS 服务器、传输方式和超时表单。默认 A、所选网络提供的 IPv4 DNS、UDP、3 秒、1 次重试；指定服务器使用 IPv4，端口默认 53，可设 1–65535。记录类型一期含 A、CNAME、MX、TXT、NS、SOA、PTR；AAAA 不属于一期界面选项。PTR 可从 IPv4 自动转为反向域名。

“dig 查询”是向明确服务器发送 DNS 报文；“系统解析”作为独立对照，走当前网络的系统解析策略。开启 Private DNS 时两者可能不同，必须标注；若网络仅给出 IPv6 DNS，严格 IPv4 的显式查询需用户填写 IPv4 DNS，不能静默改用公共服务。

结果展示服务器与端口、耗时、rcode、flags、QUESTION / ANSWER / AUTHORITY / ADDITIONAL、TTL、记录值、报文大小和实际 UDP/TCP。NXDOMAIN 是有效 DNS 答复，不是连接失败；NOERROR 无答案、SERVFAIL、REFUSED、超时分别呈现。TC 截断自动转 TCP 后标明；校验来源、事务 ID 与问题匹配。DNSSEC 可显示返回标记，不承诺验证信任链。

内置 c-ares 作为候选，明确支持以上 dig 风格检查；不承诺 BIND dig 全部选项、`+trace` 或原版输出一致。传输限定 IPv4，即使某些返回记录包含 IPv6 文本，也不能拿它连接。

“绕过缓存查询”默认开：显式查询不读应用缓存，但上游 DNS 仍可能缓存。若关闭可使用应用缓存，按 network generation + DNS server + qname + qtype + class 分键，按 TTL 过期、网络变更清理；系统对照的绕过能力按平台返回。按钮明确为“清除本应用 DNS 缓存”，返回清理条数和作用范围；“系统 DNS 缓存”入口显示高级授权状态，未授权可引导，iOS 标不支持。[R03](../research/tools.md)

验收：受控权威/递归测试服务的 A/CNAME/MX/TXT/PTR、NXDOMAIN、空答案、TC 转 TCP、超时、TTL 到期；有缓存条目清理前后证明重新发包；零条目仍正确。验证 Wi-Fi/Ethernet 分别使用各自 DNS。不得用清 WebView 缓存证明 DNS 清理成功。
