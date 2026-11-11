# 校历插件（hzau.calendar）

华中农业大学 2026—2027 学年第一学期校历，原生 dex 模块，作为第三方插件从市场分发，**不随宿主预装**。

## 数据来源

学校企业微信推送的《校历》图文（2026-06-30，信息技术中心）。原文为长图，已核对后结构化为 `CalendarData.kt`：

- 学期第 1 周周一：2026-08-31，共 20 周（至 2027-01-17）
- 关键日程：报到/军训、9-07 老生上课、9-28 新生上课、10-23 运动会、11-18 期中、1-11 期末、1-16 寒假
- 寒假：2027-01-16 — 02-20（原文：即腊月初九至正月十五），2 月 6 日为 2027 年春节

## 构建与打包

```bash
# 1. 编译（dex 由 AGP 产出，取项目内 dex 产物）
./gradlew :plugin:calendar:assembleDebug

# 2. 打包：plugin.json + classes.dex
cd market/samples/hzau-calendar
cp ../../../plugin/calendar/build/intermediates/dex/debug/mergeProjectDexDebug/*/classes.dex .
zip -r ../../../dist/hzau.calendar-1.0.0.osplugin plugin.json classes.dex

# 3. 计算校验值，更新 market/catalog.json 的 version/sha256/sizeBytes
sha256sum ../../../dist/hzau.calendar-1.0.0.osplugin
```

`mergeProjectDexDebug/<n>/classes.dex` 里是插件类本身；Kotlin 运行库由宿主提供，不要打进包。

## 发布位置

- 插件包：Gitee `main` 分支 `plugins/hzau.calendar-1.0.0.osplugin`
- 目录条目：Gitee `main` 分支 `market/catalog.json`（应用优先读取的目录）
- 本仓库 `market/catalog.json` 保留同一条目，便于 GitHub 侧兜底与追溯

## 界面

- 页面：标题、本学期状态卡（第几周 / 本周日期范围）、关键日程列表、20 周教学周历（高亮当前周）、寒假说明
- 主页组件：「教学周」卡，显示当前周与日期范围
