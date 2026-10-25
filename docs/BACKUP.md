# 备份与回滚

## 2026-10-25 自动迭代前快照

- 代码快照：`D:\file\code\project\OpeningSchool-backup-2026-10-25\source-c1e409d.zip`
- 可回滚标签：`backup/pre-iteration-2026-10-25`（指向 `c1e409d`）
- 当时的 release APK：`D:\file\code\project\OpeningSchool-backup-2026-10-25\OpeningSchool-1.0.0-beta1.apk`
- 签名备份：同目录下的 `openschool-release.jks` 与 `keystore.properties`
- 已发布版本：`v1.0.0-beta1`，tag 指向 `c02ef45`

## 回滚代码

```bash
git switch --detach backup/pre-iteration-2026-10-25
# 或只查看某个文件
git show backup/pre-iteration-2026-10-25:path/to/file
```

不要强制移动 `main` 或已发布的 tag；用新提交/revert 回滚，保留历史。

## 签名密钥

`keystore/openschool-release.jks` 和 `keystore.properties` 不进 Git。丢失后无法用同一包名发布升级；请把两者放入密码管理器或离线备份。密码不要写进 README、Issue 或日志。

## 发布产物

- 已发布 beta：`https://github.com/PanSomeone/OpeningSchool/releases/tag/v1.0.0-beta1`
- 本地构建：`app/build/outputs/apk/release/app-release.apk`
- Release 包默认启用 R8/资源压缩；Debug 包不启用。

## 2026-10-25 beta2

- 版本：`1.0.0-beta2`（versionCode 3）
- 可回滚标签：`backup/beta2-2026-10-25`（指向本提交）
- 本地产物：`app/build/outputs/apk/release/app-release.apk`
- SHA-256：`280bf065a56f74486e4c288b312ba8a9753c6bdc060c9a34cd8104159151c78e`
- 已发布：`https://github.com/PanSomeone/OpeningSchool/releases/tag/v1.0.0-beta2`
- Release 包启用 R8 默认混淆与资源压缩；签名密钥不公开。
- 适配器包：`adapter-v1` 预发布（`https://github.com/PanSomeone/OpeningSchool/releases/tag/adapter-v1`），包含正方与示例两个外部适配器；目录见 `market/catalog.json`。

## 2026-10-25 beta4

- 版本：`1.0.0-beta4`（versionCode 5）
- 本地产物：`app/build/outputs/apk/release/app-release.apk`
- SHA-256：`330d21400952dfc6cf2dfa02bd7411ab34f81a325f99fc57a1f7a6d44f344d08`
- 应用内更新：`update.json` → 显示是否最新 → 应用内下载 → SHA-256 校验 → 系统安装。
- 更新清单增加时间戳并禁用 HTTP 缓存，避免 raw GitHub 的 CDN 缓存导致版本延迟。
- 更新清单地址：`https://raw.githubusercontent.com/PanSomeone/OpeningSchool/main/update.json`；以后换 Gitee 只改 `UpdateViewModel.MANIFEST_URL`。

## 当前验证记录

- 全部单元测试通过（新增华农课表/成绩解析、版本比较回归测试）。
- Release APK 在 API 37 模拟器冷启动约 0.9–1.1 秒，体积约 2.5 MB。
- `update.json` 是应用内更新的清单（版本号、APK URL、SHA-256、大小）；发布新版本时同步更新。
- 已验证：多地区天气搜索与持久化、赞助二维码、反馈跳转、日志导出、主页长按拖动换位、今日课程时间左对齐、插件市场搜索/分类/并行批量下载/SHA 校验、外部适配器发现与导入。
- 插件市场安装仍由系统确认；未知来源权限需要用户手动允许。
