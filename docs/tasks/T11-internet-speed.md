# T11 两家公网测速集成

doc_id: T11
status: planned
depends_on: [T05, T03]

必读：[F03](../features/internet-speed.md)、[R02](../research/speedtest.md)、[A03](../architecture/network-context.md)、T03 结论与 T00 范围决定。所有权：`lib/features/internet_speed/`、原生 `android/webspeed/` 及会话测试。

只有 T03 的出口/IPv4 门槛成立，或用户明确接受范围例外后，才进入对应实现。接入中科大和 FAST 官方网页，保留站点验证和结果，进入前提示流量，设置正确的服务名称。

实现网页加载/失败/重试/退出、网络改变终止、运行状态与历史摘要。禁止预加载 FAST 导致未操作就测速；不将网页数值抓取作为唯一完成判据。代理/进程资源与当前 NetworkContext 生命周期一致。

验收 V03：正式插件路径重新执行 T03 的受控多连接和 IPv4 实验；关闭后无继续传输；PoW/429/异常跳转；两站有限真机操作与可见结果。DOM 变化时可保留网页结果，不显示假历史数字。

产出：网站/WebView 版本记录、网络侧证据、流量停止证据、错误截图及可重复验证步骤。严格模式未通过时不使用“未知出口”标签作为放行理由；用户批准的例外需在产品范围与结果中同时体现。
