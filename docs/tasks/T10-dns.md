# T10 DNS 查询与缓存管理

doc_id: T10
status: planned
depends_on: [T05]

必读：[F06](../features/dns.md)、[R03](../research/tools.md)、[A03](../architecture/network-context.md)、[F11](../features/system-dns.md)。所有权：`native/dns/`、`lib/features/dns/`、平台 `dns/` 适配及对应测试；系统清理交 T18，不混入普通适配。

集成 DNS 库，绑定其 UDP/TCP socket，提供显式服务器的 dig 风格查询与系统解析对照。实现记录类型、rcode/flags、报文各区段、TTL、TC 转 TCP、重试和取消；严格区分查询无答案与网络失败。

应用缓存按 F06 分键和过期；绕过查询为默认。清除按钮只报告应用缓存条数；Private DNS、上游缓存和系统缓存的影响在页面中清楚表达。拒绝权限不能被统计成 NXDOMAIN。

验收 V09：用可控 DNS 服务生成成功、CNAME、PTR、NXDOMAIN、NOERROR 空、SERVFAIL、超时、截断；服务端日志证明 UDP→TCP 和选定出口；缓存命中、清空、TTL 到期、网卡切换后重新发包；空缓存清理。

产出：协议测试、服务端证据、错误页面与缓存范围截图。不得把重建 HTTP 客户端或清理网页缓存标作系统 DNS 清理；用户已加入的当前网络系统清理由 T18 验收，本任务提供导航与查询对照接口。
