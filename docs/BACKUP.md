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

## 2026-10-25 beta5

- 版本：`1.0.0-beta5`（versionCode 6）
- 本地产物：`app/build/outputs/apk/release/app-release.apk`
- SHA-256：`b8a53b4bb6e722b16fe8bb412cede8b3acb7cadb97281c69f907f5e92e919b94`
- 应用内更新：`update.json` → 显示是否最新 → 应用内下载 → SHA-256 校验 → 系统安装。
- 更新清单增加时间戳并禁用 HTTP 缓存，避免 raw GitHub 的 CDN 缓存导致版本延迟；下载完成或发现新版本后可“重新检查”。
- 更新源：应用优先读取 Gitee 的 `raw/main/update.json`，失败时回退 GitHub raw；清单里的 `apkUrl` 指向 Gitee 发行版附件，`fallbackApkUrl` 指向 GitHub 备用附件。
- Gitee 仓库（`https://gitee.com/PanSomeone/OpeningSchool`）只保留 README、`update.json`、`announcements.json` 和发行版附件，不推送源码；每次发布后同步更新 Gitee 与 GitHub 两份清单。

## 2026-10-25 beta6

- 版本：`1.0.0-beta6`（versionCode 7）
- 本地产物：`app/build/outputs/apk/release/app-release.apk`
- SHA-256：`5b5c4e2d842e4417175e77bc7366abc51d81e52be926ab5142186391eacb1d69`
- 周次条直接显示本周日期范围；点击日期可设置开学第一天，日头显示实际日期。
- 课程字号可调；课程颜色 20 色、支持自定义色相/十六进制。
- 应用内更新继续使用 `update.json`，版本比较、下载、SHA-256 校验和系统安装均已验证。

## 2026-10-25 beta7

- 版本：`1.0.0-beta7`（versionCode 8）
- SHA-256：`9f77970445340a09273540dfcfaf042b5a63edf26bcaf5257a7b5cf429738b62`
- 课程卡片分四行显示：课程名（最多两行）、教师、教室；教室去掉重复的“本部”前缀。
- 字号滑块、20 色/自定义颜色、周次日期设置和应用内更新保持可用。

## 2026-10-25 beta8

- 版本：`1.0.0-beta8`（versionCode 9）
- SHA-256：`e854eaed55292fbb1f7af09d096d4272cd64922c4360f4e2ee0c043de47a705f`
- 课表右上角收为「更多」菜单（添加/导入/设置）；双击周次回到本周。
- 字号设置加入按当前列宽、格子高度实时变化的方格预览。
- 课程卡显示教师与斜体教室，两节课程三行课程名，减少省略号。
- 保留 20 色/自定义颜色、学期日期设置与应用内更新。

## 2026-10-25 beta9

- 版本：`1.0.0-beta9`（versionCode 10）
- SHA-256：`2154f825833625f77829a6c693d966c4fdfbf4d458287797295ea1a3a4a0e108`
- 热水：启动请求收到设备已在运行/占用时，按钮切换为关闭，避免卡在启动态。
- 设置入口：隐藏设置导航后可从插件页右上角齿轮进入；插件页也隐藏时自动保留设置导航。
- 课表：右上角更多菜单、双击周次回到本周、字号预览、教师/斜体教室、20 色/自定义颜色。

## 2026-10-25 beta10

- 版本：`1.0.0-beta10`（versionCode 11）
- SHA-256：`bd89c32a06bdfd84d286b8aa19d8f24d9da0f8fc4fbbc08b6cbadbf23cdd790b`
- 设置导航固定保留，设置页不再提供隐藏开关；旧的 `visible=false` 会在启动时自动修复。
- 如果导航只留下主页，启动后会自动补回设置。
- 热水运行中/占用时的关闭回退、插件页设置入口、课表菜单与预览保持可用。

## 2026-10-25 beta11

- 版本：`1.0.0-beta11`（versionCode 12）
- SHA-256：`8c98e0ce7dc6cd14583d828a51fcb82884d2d4773157a958c2ef4c28f389ef93`
- 华农成绩入口改为 `https://jyjx.hzau.edu.cn/personal-center`（智慧狮山统一认证）。
- 用户登录并进入成绩查询页后，点应用内「导入」抓取；原有成绩表解析与预览保留。

## 2026-10-25 beta12

- 版本：`1.0.0-beta12`（versionCode 13）
- SHA-256：`4be4b748f6ee26d72fa10fb757bb2ac565d2e37063428cf77a6e63b098be8d34`
- 设置 →「反馈与日志」新增「反馈群」`1126108304`，点按复制群号并提示「群号已复制」。
- 同步包含 beta11 的华农成绩入口修复（智慧狮山个人中心）。
- README 与应用内版本兜底文案更新为 beta12。

## 2026-10-25 beta13

- 版本：`1.0.0-beta13`（versionCode 14）
- SHA-256：`f1a00c6bb5d7d2b688173c44364e637b1dde1f8a8ee8003809f8a520011d5fe1`
- 课表页左右滑动切换周次（阈值约屏宽 10%，带轻震动），双击周次回到本周。
- 壁纸选择后进入全屏预览：拖动、双指缩放、滑杆缩放（1–4 倍）、重置；确认后保存裁切结果并应用。
- 新增公告：`announcements.json` 远程清单 + 内置兜底，主页铃铛未读角标，设置 →「公告」历史列表、详情与全部已读。
- 天气地区搜索改为内置省/市/区县中心坐标（AreaCity 数据，含拼音），Open-Meteo 仅作兜底。
- 仓库新增 `LICENSE`（AGPL-3.0）、`NOTICE`、`core/plugin-api/LICENSE` 与 `adapter/sample-external/LICENSE`（Apache-2.0）。

## 2026-10-25 beta14

- 版本：`1.0.0-beta14`（versionCode 15）
- SHA-256：`826c3b3e2d371d1ffda48bd235d8ae59464ca114bc042f72c5adde2ffe96f74a`
- GitHub：`https://github.com/PanSomeone/OpeningSchool/releases/tag/v1.0.0-beta14`
- Gitee（默认更新源，仅安装包与清单）：`https://gitee.com/PanSomeone/OpeningSchool/releases/tag/v1.0.0-beta14`
- Gitee `main` 仅含 README、`update.json`、`announcements.json`；`apkUrl` 指向 Gitee 附件，`fallbackApkUrl` 指向 GitHub。
- 应用内更新默认源切换为 Gitee：优先读取 Gitee 的 `update.json` 与发行版 APK，失败时回退 GitHub。
- 公告源同样改为 Gitee 优先、GitHub 备用。
- Gitee 仓库按 798QuSiBa 的方式只保留下载页与更新清单，不保留源代码；源码与 Issues 仍以 GitHub 为准。

## 2026-10-26 beta15

- 版本：`1.0.0-beta15`（versionCode 16）
- SHA-256：`6364263b7b1c2638c71f8bd8a0e6b5ee1ccb23227c3e8085084413f408acb7d6`
- 修复主页公告角标过大、遮挡喇叭图标的问题：改用图标右上角的 8dp 小圆点，并带表面色描边。
- 保留 Gitee 优先更新、GitHub 备用下载；其余功能与 beta14 相同。

## 当前验证记录

- 全部单元测试通过（新增华农课表/成绩解析、版本比较回归测试）。
- Release APK 在 API 37 模拟器冷启动约 0.9–1.1 秒，beta14 体积约 2.7 MB。
- beta14 已验证：GitHub 与 Gitee 下载的 APK SHA-256 一致；Gitee raw 清单可读取；应用内检查更新显示「已是最新版本」；全屏壁纸裁切与课表页自定义壁纸生效。
- beta15 已验证：公告角标改为右上角小圆点，不再遮挡喇叭图标；GitHub 与 Gitee APK SHA-256 一致；应用内检查更新显示「已是最新版本」。
- `update.json` 是应用内更新的清单（版本号、APK URL、fallbackApkUrl、SHA-256、大小）；发布新版本时同步更新 Gitee 与 GitHub 两份。
- 已验证：多地区天气搜索与持久化、赞助二维码、反馈跳转、日志导出、主页长按拖动换位、今日课程时间左对齐、插件市场搜索/分类/并行批量下载/SHA 校验、外部适配器发现与导入。
- 插件市场安装仍由系统确认；未知来源权限需要用户手动允许。
