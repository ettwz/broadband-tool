# 本轮只读观察记录

doc_id: R05 · 日期：2026-10-22 · 环境：WSL，非手机

没有开发应用、执行公网大流量测速或操作真实路由器。以下是实际命令和 HTTP 观察；公网 IP 原值未记录，避免不必要暴露。

| 编号 | 操作 | 观察 | 能证明 / 不能证明 |
| --- | --- | --- | --- |
| O01 | `curl -4 --connect-timeout 10 --max-time 20 https://ip.sb/`，默认 curl UA | 退出码 0；14 字节；解析为合法 IPv4 | 该终端环境能获取 IPv4；不证明手机出口 |
| O02 | 同 URL，UA 改为 `broadband-tool-research/0.1` | 退出码 0；19625 字节；非纯 IPv4，是网页 | 首页根据客户端表现不同；不能只看 HTTP 成功 |
| O03 | 同自定义 UA 请求 `https://api-ipv4.ip.sb/ip` | 退出码 0；14 字节；合法 IPv4 | 专用 API 是更明确的集成入口 |
| O04 | GET 中科大首页 | HTTP 200；正文约 15.6 KB；含 PoW，调用 `backend/pow.php` 和 `backend/pow_verify.php` | 线上验证存在；未执行验证或测速 |
| O05 | GET 中科大 `speedtest_worker.js` | HTTP 200；配置引用 `backend/garbage.php`、`empty.php`、`getIP.php` | 存在内部测速端点；未直接请求这些测速端点 |
| O06 | GET FAST 首页及其 `app-0bffe1.js` | HTTP 200；脚本引用 `api.fast.com` 和 `/netflix/speedtest/v2` | 有前端内部服务调用；不证明官方开放 API |
| O07 | 读取 fast-cli package.json、LibreSpeed CLI README | 前者 Node / Puppeteer；后者 Go CLI 与自定义站点 | 两种集成候选确实存在；未编译或测兼容 |
| O08 | 查看 Device Lab 官方商店两张截图 | 一张桌面组件、一张应用概览；概览有卡片和列表 | 确认视觉参考；未操作该应用 |
| O09 | 查询 Flutter 组件官方发布页 / pub 元数据 | WebView、图表、状态、导航、终端有跨端候选 | 仅证明候选和声明；不是性能或维护质量保证 |

请求正文只作有限观察，没有保存完整第三方网站副本。网页脚本文件名可能变化，复查时从首页寻找引用；不要长期固定它。静态 GET 不执行 JavaScript，因此没有测得速度，也没有验证 PoW 在 WebView 中通过。

IP 的 `-4` 只约束本地 curl 连接；若运行环境存在代理，不能据此认定目标链路所有环节的地址族。移动端网卡和 IPv4 证明必须来自 T01 / T03 的实际连接与网络侧证据。

**仍需开发阶段验证**：Android/iOS 真机权限、双网卡、USB 网卡兼容、iperf3 s/c、ICMP/逐跳响应、网页的真实出口/地址族/停止行为、性能和电量。本轮这些项目全部未执行，未记为通过。
