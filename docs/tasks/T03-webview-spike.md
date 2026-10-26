# T03 公网测速方案验证

doc_id: T03
status: planned
depends_on: [T00]

必读：[R02](../research/speedtest.md)、[R05](../research/observations.md)、[F03](../features/internet-speed.md)、[A03](../architecture/network-context.md)。所有权：`spikes/webspeed/`、`docs/evidence/T03/`。

先复核两服务是否新增正式 API/SDK、二进制或集成说明。若仍采用网页，用受控 HTTPS 页模拟 Worker、多域名、多并发上下行、重定向和错误。验证 Android WebView 绑定覆盖所有连接；另验证 IPv4，不用“绑定 Wi-Fi 成功”代替地址族证据。

如需 WebView 代理 + 本机 CONNECT，检查 feature 支持、IPv4 DNS、TLS 不解密、QUIC/UDP、直连回退、关闭后残留、代理资源和吞吐偏差。记录适用 WebView 版本、失败条件与网络侧证据。iOS WKWebView 的对应能力仅能独立评估，不抄 Android 结论。

受控验证后，对中科大与 FAST 各做有限手工真机检查：页面可操作、PoW、开始/停止、上传/下载结果可查看；保留服务说明，不直接调用内部测速端点绕过验证，不做重复压力测试。

产出：原生/API/网页三种候选结论、选定方案、出口和地址族证明、站点兼容记录、不能保证的行为。验收：V03 通过且流量关闭可证；没有把静态页面加载视为测速完成。

失败分支：明确记录是站点限制、WebView 能力或绑定问题。提出系统网络网页模式等例外给主 agent；未经用户接受，T11 和完整一期发布仍不能放行。调查报告完成不等于功能目标达成。
