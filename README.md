# 开源大学（OpeningSchool）
功能说明

> 当前测试版：`1.0.0-beta16`（versionCode 17） · [下载与更新](https://gitee.com/PanSomeone/OpeningSchool/releases)

## 运行环境
- **平台**：Android（minSdk 26）
- **构建要求**：JDK 17+、Android SDK（compileSdk 37）

## 核心功能

### 主页
- 两列组件画布，默认 4 行（8 格），最多可扩展至 12 行（24 格）
- 组件支持 1×1、2×1、1×2、2×2 四种尺寸，可添加、移除、长按右上角拖动、留空
- 字体随组件尺寸缩放；拖动时卡片跟随手指并显示落点
- 内置组件：天气、今日课程、日期、文件夹；插件提供热水和 GPA 快捷组件
- 天气支持保存多个地区；地区搜索内置省/市/区县中心坐标（可输入拼音，如 `hongan`），Open-Meteo 地理数据作为兜底；今日课程把上下课时间放在左侧
- 文件夹组件可收纳多个功能并自定义名称

### 课表
- 支持多张独立课表，可切换、新建、重命名、删除
- 节次列同时显示上下课时间
- 一门课可添加多个「星期 + 起止节次」时间段，支持设置周次、颜色、备注和跨节合并
- 周次切换（‹ 第 N 周 ›），点击可跳转；课表页左右滑动也可切换周次，双击周次回到本周；点击周次条右侧的日期可直接设置开学第一天，设置后第一周第一天就是该日期
- 学期开始日期、总周数、一周起始日、周末显示均可调；日期范围会显示在周次条上并随周切换
- 作息可调：节次数量、起止时间、午休分割线；修改某节时长可自动顺延后续节次
- 课表页右上角菜单可直接进入课表设置，不必绕到插件页
- “设置”导航固定保留、不可隐藏；旧配置里被隐藏的设置会在启动时自动恢复
- 插件页右上角也保留设置入口
- 课程字号可在「课表设置 → 外观」中调整；两节连上的课程会利用整块高度显示课程名、教师和教室，教室用斜体区分
- 课程颜色提供 20 种高区分度预设，每门课还可自定义色相或输入十六进制颜色
- 设置开学日期后，每一天的表头会显示实际日期（如第一周周一 8/31）

### 课表导入
- 应用内打开学校教务网页，用户自行登录并进入课表页，点击「导入」抓取
- 解析支持：正方 kbList JSON、#kbtable HTML、华农 `#kbgrid_table` 课程卡片、通用表格（横排/纵排、rowspan、单双周）
- 华农课表按「日-节」单元格读取教师、地点、周次，跳过待筛选课程；同一门课的不同星期可保留各自的周次
- 成绩导入支持华农 jqGrid（`aria-describedby` 列）与普通成绩表，自动组合学年/学期并跳过等级制成绩
- 华农成绩入口：`https://jyjx.hzau.edu.cn/personal-center`（智慧狮山统一认证）；登录后进入成绩查询页，再点应用内「导入」
- 导入前可预览，支持替换当前课表或新建
- 不接管账号密码，不保存网页登录信息；抓取仅在用户点击「导入」时发生

### 插件系统
- 独立「插件市场」负责添加插件；已安装列表显示在导航栏及插件管理页，支持移除和设置
- 在线插件支持搜索、分类、单项下载、全选批量下载；最多 3 个任务并行，下载后校验 SHA-256
- 安装使用系统安装器，安装后刷新即可发现外部适配器
- 第三方适配器以独立 APK 形式提供，通过 Messenger JSON 通信，宿主不加载外部代码
- 目录格式与发布流程见 [docs/PLUGIN_MARKET.md](docs/PLUGIN_MARKET.md)

### 成绩插件（GPA）
- 逐门成绩按学期分组展示，每门课可单独勾选是否计入 GPA
- 计算后显示总体 GPA、加权平均分、计入学分/门数，以及每学期统计
- 支持从教务成绩页登录后抓取成绩，导入前可预览（追加或替换）
- 另含体测标准分、加分、等级与毕业总评计算

### 热水插件
- 手机号 + 验证码登录，设备列表、开关水、设备绑定
- 主页提供热水快捷开关组件
- 启动时若设备已在运行，按钮会自动切换为“关闭热水”，不会卡在启动状态

### 设置
- 主题、导航栏、透明度等基础配置
- 关于页（作者、版本号、检查更新、赞助）
- 检查更新会显示是否有新版本；有更新时直接在应用内下载 APK，校验 SHA-256 后调起系统安装
- 反馈直达 GitHub Issues；可导出包含版本、机型与最近日志的文本文件
- 赞助页内置微信、支付宝收款二维码
- 公告：主页右上角铃铛显示未读公告；设置 →「公告」可查看历史并一键全部已读
- 开源许可建议与备份/回滚说明见 [docs/LICENSING.md](docs/LICENSING.md)、[docs/BACKUP.md](docs/BACKUP.md)

### 壁纸
- 支持全局壁纸和按页面覆盖（主页、课表、热水、成绩、插件、设置）
- 页面壁纸优先于全局壁纸
- 选择图片后进入预览页，可拖动、双指或滑杆缩放调整裁切范围，确认后保存裁切结果

### 悬浮导航
- 半透明外观，透明度可调
- 固定宽度图标 + 名称，高亮条平滑滑动
- 支持排序、隐藏、改名、28 个可选图标，最多 5 项

## 数据存储
- 多课表：`filesDir/timetables.json`（原子替换写入）
- 设置、主页布局、壁纸映射、悬浮导航：SharedPreferences
- GPA 课程与计入开关：SharedPreferences（openschedule_scores）
- 壁纸图片：`filesDir/wallpaper_*.img`
- 运行日志：`filesDir/logs/app.log`
- 公告缓存与已读记录：`filesDir/announcements.json`、SharedPreferences；公告源 [announcements.json](announcements.json)
- 更新清单：[update.json](update.json)

## 更新与反馈
- 应用内更新：设置 →「检查更新」→ 显示是否有新版本 → 应用内下载 → SHA-256 校验 → 系统安装
- 默认更新源（Gitee）：<https://gitee.com/PanSomeone/OpeningSchool/raw/main/update.json>
- 备用更新源（GitHub）：<https://raw.githubusercontent.com/PanSomeone/OpeningSchool/main/update.json>
- Gitee 只发布安装包与更新清单，源代码仍在 GitHub；APK 下载失败时会自动改用清单中的 GitHub 备用地址
- 问题反馈：<https://github.com/PanSomeone/OpeningSchool/issues>
- 反馈群：`1126108304`（设置 → 反馈与日志可一键复制）
- 适配器目录：[market/catalog.json](market/catalog.json)；说明见 [docs/PLUGIN_MARKET.md](docs/PLUGIN_MARKET.md)
- 版本与回滚记录：[docs/BACKUP.md](docs/BACKUP.md)

## 开源许可

- 应用主体（`app`、`core`、`feature`、内置插件）：**AGPL-3.0**，全文见 [LICENSE](LICENSE)。
- 外部适配器协议与示例（`core/plugin-api`、`adapter/sample-external`）：**Apache-2.0**，全文见 [core/plugin-api/LICENSE](core/plugin-api/LICENSE)。
- 名称、Logo、应用图标、赞助二维码、截图：**保留所有权利**，不随代码许可授权。
- release 签名密钥、插件签名/审核、未来的云端服务：不公开或单独授权。
- 第三方依赖与数据署名（Open-Meteo、AreaCity 行政区划等）见 [NOTICE](NOTICE)。

完整分析与源码保护建议见 [docs/LICENSING.md](docs/LICENSING.md)。