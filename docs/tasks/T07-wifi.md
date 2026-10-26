# T07 Wi-Fi 列表与两种图表

doc_id: T07
status: planned
depends_on: [T05]

必读：[F01](../features/wifi.md)、[P03](../product/ux.md)、[R01](../research/platform.md)。所有权：`lib/features/wifi/`、原生 `android/wifi/` 和对应 fixture / 测试；manifest 变更提请集成人合入。

实现扫描调度、权限/定位状态、数据时间戳、BSSID 身份、筛选/暂停，以及 Comparison 和 Channels。保留原始频率/带宽，单独维护展示转换，不用平滑绘图伪造扫描采样。iOS 能力分支显示限制。

先以已知数据验证图表数值和频率位置，再用真实 AP 验证更新。图表时间以实际结果时间为准；重复旧扫描不追加为新点。接收到扫描通知也要检查是否有新结果。

验收 V01/V10：同 SSID 双 AP、隐藏 SSID、2.4/5 GHz、可用时的 6 GHz；信道 14、80+80、未知宽度；关闭定位和超额扫描；200 AP 模拟列表；10 分钟运行；深色与大字体。Channels 不出现虚构噪声或占用率。

产出：图表 fixture 说明、真实 AP 对照、权限边界截图、扫描限频记录。任何只在关闭系统扫描限制后才成立的刷新表现，不能作为默认使用验收。
