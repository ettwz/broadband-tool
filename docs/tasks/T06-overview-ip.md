# T06 网络概览与公网 IP

doc_id: T06
status: planned
depends_on: [T05]

必读：[F07](../features/overview.md)、[F08](../features/public-ip.md)、[P03](../product/ux.md)、[A04](../architecture/lifecycle.md)。所有权：`lib/features/overview/`、`lib/features/public_ip/`、原生 `public_ip/` 适配及对应测试；复用 T04 历史服务。

将 NetworkSnapshot 显示为接口与 IPv4、路由、DNS、链路卡片。未知值、旧值、无默认路由、多地址都需正确显示。网关/DNS 可带参数进入工具，路由注册由集成人合入。

通过绑定网络的 HTTPS 请求专用 IP.SB IPv4 端点，按 F08 限制响应长度、超时和解析；切换网络立即使旧缓存失效。允许手工刷新、复制和受控历史摘要。

验收 V07/V12：受控合法/HTML/IPv6/429/超时响应；无网络不伪造 0.0.0.0；实际 API 一次手工对照；双网卡抓取源地址；概览与系统/实验记录一致。缺失 MAC、MTU 或链路速率不显示假数据。

产出：真机主要状态截图、解析测试、实际出口证据。已显示卡片不代表字段正确，必须记录至少一套真实设备对照结果；未经许可不向第三方增加位置或设备信息请求。
