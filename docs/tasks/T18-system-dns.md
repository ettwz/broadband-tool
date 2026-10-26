# T18 当前网络系统 DNS 清理

doc_id: T18
status: planned
depends_on: [T15, T10]

必读：[F11](../features/system-dns.md)、[F06](../features/dns.md)、T14 证据。所有权：`native/advanced/dns/`、高级 `system_dns/` UI 与 Android 系统适配；普通 DNS 页的跳转由 T10 owner/集成人配合。

通过已获授权的 Shizuku Root UserService 执行版本化系统清理，固定所选 Network/netId/generation；界面显示作用范围，应用缓存单独可选。无稳定接口或系统拒绝时明确不可用，禁止自动退成应用缓存或绕过查询。

产出：支持 OS/ROM/调用方式清单、系统返回结果、受控查询验证日志、页面与拒绝分支。验收 V16：先证明系统缓存命中，再清理，再以不绕过系统缓存的同网络查询证明重新请求上游；另验其他网络缓存、应用私有缓存边界、Private DNS、网络切换与失权。

不能硬编码跨版本 Binder 编号；没有条数信息不显示清理数量。UI 的“系统已接受清理请求”与开发证据里的“清理效果已验证”分别记录，不把返回码当作全缓存效果证明。
