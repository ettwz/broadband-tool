# 平台来源索引

doc_id: S01 · 核查日期：2026-10-22

以下为一手资料。链接内容可能更新，正式开发锁版本时复查。每项只支持所述结论，不证明某台手机已经可用。

| 编号 | 来源 | 支持的结论 |
| --- | --- | --- |
| S01-01 | [Android Wi-Fi scanning](https://developer.android.com/develop/connectivity/wifi/wifi-scan) | 扫描权限、定位开关、前台 2 分钟 4 次和后台限频 |
| S01-02 | [Android Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions) | Android 13+ 的附近设备权限不替代 startScan/getScanResults 的精确定位要求 |
| S01-03 | [Apple TN3111](https://developer.apple.com/documentation/technotes/tn3111-ios-wifi-api-overview) | 无通用 Wi-Fi 扫描 API；HotspotHelper 有特定用途限制；当前连接信息有独立接口 |
| S01-04 | [Android Network](https://developer.android.com/reference/android/net/Network) | bindSocket、socketFactory、getAllByName 可指定网络 |
| S01-05 | [Android NDK networking](https://developer.android.com/ndk/reference/group/networking) | android_setsocknetwork、android_getaddrinfofornetwork |
| S01-06 | [Android LinkProperties](https://developer.android.com/reference/android/net/LinkProperties) | 接口名、地址、DNS、路由、MTU 等字段；部分可能缺失 |
| S01-07 | [Apple NWParameters](https://developer.apple.com/documentation/network/nwparameters)、[requiredInterface](https://developer.apple.com/documentation/network/nwparameters/requiredinterface) | 连接和监听器可指定接口；不能推导 WKWebView 也有同等能力 |
| S01-08 | [Android 10 执行限制](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission) | 不能从应用可写主目录直接 execve；二进制应随 APK 提供 |
| S01-09 | [Android DnsResolver](https://developer.android.com/reference/android/net/DnsResolver) | 指定网络查询、NO_CACHE_LOOKUP / NO_CACHE_STORE，API 29+ |
| S01-10 | [AOSP DnsResolverService](https://android.googlesource.com/platform/packages/modules/DnsResolver/+/master/DnsResolverService.cpp) | flushNetworkCache 受 NETWORK_STACK 权限检查保护 |
| S01-11 | [Apple URLSession.flush](https://developer.apple.com/documentation/foundation/urlsession/flush(completionhandler:)) | 会话清理和连接重建，不是全系统 DNS 清理承诺 |
| S01-12 | [Apple SimplePing](https://developer.apple.com/library/archive/samplecode/SimplePing/Introduction/Intro.html) | ICMP 发送接收的归档示例；2016 年资料，不能证明当前 iOS traceroute |
| S01-13 | [Linux ip(7)](https://man7.org/linux/man-pages/man7/ip.7.html) | IP_TTL、IP_RECVERR 等 Linux socket 机制；不能代替 Android 沙箱验证 |
| S01-14 | [Android local network permission](https://developer.android.com/privacy-and-security/local-network-permission) | Android 16 的 opt-in 与 Android 17 / target 37 的 ACCESS_LOCAL_NETWORK 要求 |
| S01-15 | [Apple TN3179](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy) | 本地网络隐私、用途说明、不同流量类型的权限差异 |
| S01-16 | [Android 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) | 原生库打包和运行要验证 16 KB 页支持 |
| S01-17 | [Apple App Review 2.5](https://developer.apple.com/app-store/review/guidelines/#software-requirements) | 公开 API、随包代码及 IPv6-only 网络要求；发布前需复核 |
| S01-18 | [AndroidX ProxyController](https://developer.android.com/reference/androidx/webkit/ProxyController) | WebView 代理设置入口，需运行时检查特性支持；不是已证明的测速方案 |
| S01-19 | [ConnectivityManager](https://developer.android.com/reference/android/net/ConnectivityManager) | 网络回调和进程绑定；推荐优先逐 socket 绑定 |
| S01-20 | [Android ScanResult](https://developer.android.com/reference/android/net/wifi/ScanResult) | RSSI、采样时间、主频率、中心频率与信道带宽；80+80 为分离频段 |

Apple 部分页面正文通过同路径 `.md` 获取；仅是官方文档的阅读方式。来源未能证明的能力，在 R01–R03 中标成待验证或不承诺。
