# 高级模式来源

doc_id: S03 · 核查日期：2026-10-22

| 编号 | 一手资料 | 支持的结论 |
| --- | --- | --- |
| S03-01 | [KernelSU Next 官方项目](https://github.com/KernelSU-Next/KernelSU-Next)、[官网](https://kernelsu-next.github.io/webpage/) | Android 内核 root 方案；不以官网的一段支持范围推定所有设备可用 |
| S03-02 | [Shizuku 入门](https://shizuku.rikka.app/guide/setup/)、[介绍](https://shizuku.rikka.app/introduction/) | root / ADB 两种启动路径；用户需授权应用 |
| S03-03 | [Shizuku API](https://github.com/RikkaApps/Shizuku-API) | getUid 身份、授权、Binder 生死、UserService 与 JNI；UserService 不等同 Android 应用进程 |
| S03-04 | [Linux capabilities](https://man7.org/linux/man-pages/man7/capabilities.7.html)、[Android SELinux](https://source.android.com/docs/security/features/selinux) | 原始报文和网络管理的权限；root 仍受强制访问控制 |
| S03-05 | [tcpdump](https://www.tcpdump.org/manpages/tcpdump.1.html)、[pcap 过滤器](https://www.tcpdump.org/manpages/pcap-filter.7.html) | 指定接口捕获、长度/过滤/输出控制；IPv4 过滤需另外保留 ARP |
| S03-06 | [PCAPdroid root capture](https://emanuele-f.github.io/PCAPdroid/advanced_features#44-root-capture)、[FAQ](https://emanuele-f.github.io/PCAPdroid/faq.html) | 已有直接接口抓包与 VPN 共存案例；不能推定任意其他设备流量可见 |
| S03-07 | [arping](https://man7.org/linux/man-pages/man8/arping.8.html)、[DHCP RFC 2131](https://www.rfc-editor.org/rfc/rfc2131) | IPv4 重复地址检查与 DHCP 报文语义；无回应不能直接判设备不存在 |
| S03-08 | [AOSP DnsResolverService](https://android.googlesource.com/platform/packages/modules/DnsResolver/+/refs/heads/main/DnsResolverService.cpp) | flushNetworkCache(netId) 有系统网络权限检查；不是稳定普通 SDK 接口 |
| S03-09 | [ip-address](https://man7.org/linux/man-pages/man8/ip-address.8.html)、[ip-route](https://man7.org/linux/man-pages/man8/ip-route.8.html)、[ip-link](https://man7.org/linux/man-pages/man8/ip-link.8.html) | 地址、路由、MTU、链路操作的基础；Android 系统管理层仍需同步验证 |
| S03-10 | [AOSP DNS resolver v10 冻结契约](https://android.googlesource.com/platform/packages/modules/DnsResolver/+/c76ebe9a73f6f0d4eaf3a5264c3dd7afbd547b76/aidl_api/dnsresolver_aidl_interface/10/)、[AIDL Descriptor 注解](https://source.android.com/docs/core/architecture/aidl/aidl-annotations) | 完整接口生成、版本协商、保留原 Binder descriptor；不猜 transaction 编号 |
| S03-11 | [AOSP 隔离网络测试](https://android.googlesource.com/platform/packages/modules/DnsResolver/+/1ecf8871a362736641822c5f3e64e327897d3ea5/tests/dns_responder/dns_responder_client_ndk.cpp)、[回环 DNS 响应测试](https://android.googlesource.com/platform/packages/modules/DnsResolver/+/refs/heads/android10-mainline-resolv-release/dnsresolver_binder_test.cpp) | 使用无接口的临时网络对象和回环响应器验证系统缓存，无需修改现有网络 DNS |

资料证明存在技术路径，不证明机型适配、异常恢复或某个版本的调用方式已经可用。不硬编码 Binder transaction 编号，不从旧版本教程复制一个清 DNS 命令就当通用实现。
