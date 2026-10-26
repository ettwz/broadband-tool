# 公开源码准备

doc_id: D10 · 2026-10-26

公开文件使用示例数据：回环地址用于本机夹具，文档示例网段用于网络数据；少量私网地址和本地管理 MAC 是明确的测试/设计样例，不是实际部署信息。设备型号、系统大版本、工具版本和 Root 前提属于兼容性信息，保留在验证清单中。

实际 ADB 地址通过 `BROADBAND_DEVICE` 配置；iperf 对端通过 `PEER_IPV4` 或测试控制器的 `--device` 参数提供。可选本机缓存和原始证据位于被忽略的 `.cache/`，不进入源码包。测试的常规成功日志不输出真实网络地址或句柄。

## 检查与导出

在仓库根目录运行：

```bash
python3 tools/check_public_source.py
python3 tools/export_public_source.py
```

输出 `dist/broadband-tool-public-source.zip` 及其 SHA-256 文件。导出当前工作区的应用、文档、原生源码、测试和脚本，包含未提交但属于这些目录的文件；不包含 `.git`、本地开发配置、环境文件、密钥文件、工具链、缓存、日志、抓包和 APK。它不会发布到任何远端。

检查工具只报告文件位置和问题类别，不回显匹配值。它检查本机账号路径、非示例地址/MAC、账号 URL、邮箱等文本，并核对 `docs/public-assets.json` 中已审查图片的摘要。第三方源码和许可保留原始归属信息；图片摘要检查不能替代首次人工审查，新增截图必须先处理真实环境信息再登记。

## Git 历史

开发仓库的旧提交包含原始设备截图。**清理工作区不会删除旧提交中的内容，不能把现有完整历史直接当作已脱敏历史公开。** 本项目的 GitHub 发布方式是独立的初始提交，只包含当前脱敏源码快照；开发仓库的历史仍单独保留。

首次公开建议解压上述源码包，在新的目录初始化 Git 仓库，再配置自己的 GitHub 远端。源码包已移除历史；原仓库和原始证据继续留在本地。新仓库的提交者名称和邮箱也应使用准备公开的身份，不沿用不希望公开的工作邮箱。若必须保留原历史，需要另行审查并重写涉及内容后再公开。

```bash
PUBLIC_SOURCE_DIR="$(mktemp -d)"
unzip dist/broadband-tool-public-source.zip -d "$PUBLIC_SOURCE_DIR"
cd "$PUBLIC_SOURCE_DIR/broadband-tool"
python3 tools/check_public_source.py
git init -b main
: "${PUBLIC_GIT_NAME:?请先设置准备公开的提交者名称}"
: "${PUBLIC_GIT_EMAIL:?请先设置准备公开的提交者邮箱}"
git config --local user.name "$PUBLIC_GIT_NAME"
git config --local user.email "$PUBLIC_GIT_EMAIL"
git add -- .gitattributes .gitignore README.md LICENSE apps docs native tests tools
git commit -m "Initial public source"
# 再设置自己的 GitHub 远端并推送。
```

用户已确认的 OnePlus 13 验证结果见 [D09](validation.md)；未单独记录的专项不因脱敏或公开而自动变成已通过。
