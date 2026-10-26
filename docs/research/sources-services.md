# 服务、组件与参考产品来源

doc_id: S02 · 核查日期：2026-10-22

| 编号 | 来源 | 支持的结论 |
| --- | --- | --- |
| S02-01 | [中科大测速](https://test.ustc.edu.cn/) | 页面、IPv4 入口、PoW、限流与结果记录说明 |
| S02-02 | [站点指向的源代码](https://github.com/bg6cq/speedtest) | LibreSpeed 衍生网页、LGPL-3.0；不等同线上全部版本 |
| S02-03 | [LibreSpeed CLI](https://github.com/librespeed/speedtest-cli) | Go 命令行客户端、自定义服务器、发布二进制；不证明兼容中科大 PoW |
| S02-04 | [FAST.com](https://fast.com/) | Netflix 测速、上下行及空载/负载延迟；服务器全球分布 |
| S02-05 | [fast-cli](https://github.com/sindresorhus/fast-cli)、[依赖文件](https://github.com/sindresorhus/fast-cli/blob/main/package.json) | 非 Netflix 官方；依赖 Node / Puppeteer，不是直接可嵌入的移动端 SDK |
| S02-06 | [IP.SB API](https://ip.sb/api/) | 纯文本 IPv4 专用端点、User-Agent 注意事项 |
| S02-07 | [iperf3 官方](https://software.es.net/iperf/)、[获取源码](https://software.es.net/iperf/obtaining.html) | BSD-3-Clause；主要支持桌面 Unix；移动端需自行移植；ESnet 不发官方二进制包 |
| S02-08 | [iperf3 参数](https://software.es.net/iperf/invoking.html)、[libiperf 构建](https://software.es.net/iperf/building.html)、[API 源文件](https://github.com/esnet/iperf/blob/master/src/iperf_api.h) | s/c、TCP/UDP、JSON、库形态；生命周期需要包装 |
| S02-09 | [c-ares](https://c-ares.org/)、[socket 回调](https://c-ares.org/docs/ares_set_socket_functions.html)、[MIT 许可](https://c-ares.org/license.html) | 异步 DNS 库和 socket 接入点 |
| S02-10 | [libtelnet](https://github.com/seanmiddleditch/libtelnet)、[COPYING](https://github.com/seanmiddleditch/libtelnet/blob/master/COPYING) | Telnet 协议解析库；该仓库为 public domain 声明，须核对实际采用版本 |
| S02-11 | [NetSpot Android 手册](https://www.netspotapp.com/help/netspot-android-manual/) | 时间信号曲线、频段信道图及 AP 筛选的参考行为 |
| S02-12 | [Device Lab 商店页](https://apps.apple.com/us/app/device-lab/id6757624445) | 参考产品身份、官方截图中的信息布局 |
| S02-13 | [Flutter 平台通道](https://docs.flutter.dev/platform-integration/platform-channels)、[平台支持](https://docs.flutter.dev/reference/supported-platforms) | Kotlin / Swift 桥接与跨端平台范围 |
| S02-14 | [Compose Multiplatform iOS stable](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/) | KMP + Compose 是有效候选，不因过期资料而排除 |
| S02-15 | [webview_flutter](https://pub.dev/packages/webview_flutter) | Flutter 官方插件，Android WebView / iOS WKWebView，BSD-3-Clause |
| S02-16 | [fl_chart](https://pub.dev/packages/fl_chart)、[Riverpod](https://pub.dev/packages/flutter_riverpod)、[go_router](https://pub.dev/packages/go_router) | 图表、状态、导航候选；采用前锁定版本和核对许可证文件 |
| S02-17 | [xterm.dart](https://pub.dev/packages/xterm) | Flutter 终端模拟器候选；只负责终端渲染，不提供 Telnet 连接 |

R05 另记录本轮直接访问线上 HTML / JavaScript 的有限观察。没有运行公网大流量测速，没有获取或保存认证材料，也没有联系任何服务运营方。本轮不以搜索未找到来证明世界上不存在 API；结论是“在列出的官方资料中未发现适合承诺稳定集成的公共 API / SDK”。
