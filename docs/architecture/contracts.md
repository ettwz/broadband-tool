# 公共契约

doc_id: A02 · v2 草案；T04 冻结公共字段，T15 扩展高级边界

所有接口异步，时间同时包含墙钟时间和单调时钟耗时；IP 字符串规范化，计数不使用格式化文本。未知字段为 null，并附 reason。UI 不解析命令行文本判断成功。

| 模型 | 必须字段 / 语义 |
| --- | --- |
| Capability | feature、supported、reasonCode、requiredPermissions、constraints；每个平台独立返回 |
| NetworkRef | opaqueId、generation；仅当前进程有效，重连后不能复用旧句柄 |
| NetworkSnapshot | ref、transport、interfaceName、IPv4/前缀列表、routes、dnsServers、mtu、isSystemDefault、internetValidated、observedAt、字段缺失原因 |
| NetworkContext | selectedRef、family=ipv4、allowCellular=false、bindingPolicy=strict、proxy/VPN 信息及可见性；一次任务固定快照 |
| TaskRequest | schemaVersion、requestId、kind、networkContext、typedParams；校验失败不能启动网络活动 |
| TaskEvent | taskId、sequence、kind、elapsedMs、payload；终态后不再发进度 |
| TaskResult | taskId、kind、status、startedAt、durationMs、networkSnapshot、bindingEvidence、metrics、warnings、error；保存 schemaVersion |
| Error | code、message、retryable、osCode 可选、detail 脱敏；系统原文仅在展开详情显示 |

高级模式的 PrivilegeSnapshot、InterfaceRef、CaptureSession、ConfigTransaction 与追加操作定义在 [A05](privileged-service.md)，配置状态定义在 [A06](network-recovery.md)。Capability 增加 accessMode（standard / shizuku_root）及验证范围。普通 NetworkContext 的 IPv4 约束不适用于尚未取得地址的链路观察，后者必须明确使用 InterfaceRef，不伪造 NetworkRef。

`bindingEvidence` 分别记录 requested / bound / observed 网络、sourceIPv4、family、verificationLevel。运行时观测与开发期抓包验证是两种证据，不把“API 调用成功”命名为“网络侧已验证”。网页会话必须有相同字段，未知就是 unknown。

## 操作边界

`listNetworks / watchNetworks / getCapabilities / requestPermission / selectNetwork / startTask / cancelTask / watchTask / clearAppDnsCache`。`startTask` 立即返回 taskId；结果通过事件流返回；重复 requestId 返回同一任务，不重复发流量。telnet 增加 `sendInput(taskId, bytes)` 和 `resizeTerminal`；iperf server 通过同一任务流报告监听与会话。

## 错误码与状态

状态：created → validating → running（server 可 listening）→ stopping → completed / cancelled / failed / interrupted。终态只能一次。网络改变为 interrupted，不标作正常测速完成。

错误至少包括：INVALID_INPUT、PERMISSION_DENIED、LOCATION_DISABLED、SCAN_THROTTLED、UNSUPPORTED、NO_IPV4、NETWORK_LOST、NETWORK_CHANGED、BIND_FAILED、BUSY、DNS_FAILED、TIMEOUT、CONNECTION_REFUSED、PORT_IN_USE、PROVIDER_BLOCKED、INVALID_RESPONSE、ENGINE_FAILED。

输入层面统一拒绝 NUL、控制字符、超长主机名、非法端口及非允许地址族；高级参数是白名单字段，不提供附加 shell 参数。具体默认值与结果指标由 F01–F12 定义。授权撤销、网络/接口 generation 变化与重复请求都必须有幂等处理；不能把配置应用重试当成新的无状态命令。
