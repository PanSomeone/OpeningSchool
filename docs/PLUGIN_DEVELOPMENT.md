> 📘 本文是专题附录。**主开发文档：[开发文档.md](开发文档.md)**

# 插件模块开发与发布

开源大学支持**两类**插件，按能力需求选择：

| 类型 | 形态 | 适合场景 | 文档 |
| --- | --- | --- | --- |
| 原生插件 | 独立 APK 或应用内 dex 模块，原生 Android View | 真实控件、复杂布局、后台逻辑 | [NATIVE_PLUGIN_SDK.md](NATIVE_PLUGIN_SDK.md) |
| JS 模块 | `.osplugin`（plugin.json + main.js） | 网页解析导入、轻量声明式卡片 | 本文 |

JS 模块在应用内下载、校验、启用和删除，不安装 APK，也不需要系统安装器或额外权限；原生插件由用户手动安装 APK。两者可以共存。

本文后续章节均针对 JS 模块，当前支持两种 `apiVersion`：

| 类型 | apiVersion | 用途 |
| --- | --- | --- |
| `web-importer` | 1 | 网页导入：抓取页面 HTML 后调 `parse()` 返回课表 JSON（见 §2–§3） |
| `app` | 2 | 应用模块：`apply(ctx)` 常驻生命周期，可注册主页组件、订阅事件、提供跨插件服务（见 §4） |

`app` 模块是全插件架构（Plugin API v2）的一部分，完整规格（生命周期、事件、服务、插件树、安全模型与路线图）见 [PLUGIN_ARCHITECTURE.md](PLUGIN_ARCHITECTURE.md)。

旧版外部 APK 适配器仍保留兼容，但市场不再发布新的 APK 插件；新插件请使用本文的模块格式。

## 1. 模块包结构

```
my-module.osplugin           # 普通 zip，扩展名仅用于识别
├── plugin.json              # 必需，位于压缩包根目录
├── main.js                  # 入口脚本，默认名 main.js
└── assets/                  # 可选，随模块保存的静态资源
```

限制：

| 项目 | 限制 |
| --- | --- |
| 压缩包解压后总大小 | ≤ 8 MB |
| 文件数 | ≤ 200 |
| `plugin.json` | ≤ 64 KB |
| 脚本执行时间 | ≤ 6 秒 |
| 单次抓取页面数 | ≤ 40 |
| `apiVersion` | 本文的导入模块为 `1`；应用/原生模块为 `2` |
| 脚本超时/内存 | 由宿主 Rhino 沙箱控制；超时会中止 |

## 2. plugin.json

```json
{
  "id": "demo.web-importer",
  "name": "示例网页导入模块",
  "description": "应用内 JS 模块示例",
  "version": "1.0.0",
  "apiVersion": 1,
  "type": "web-importer",
  "entry": "main.js",
  "startUrl": "https://example.com",
  "iconKey": "extension",
  "author": "OpeningSchool"
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 全局唯一，小写字母/数字开头，只允许 `a-z0-9._-`，2–64 字符 |
| `name` | 是 | 导入列表中显示的名称 |
| `description` | 否 | 一句话说明 |
| `version` | 否 | 模块版本，建议语义化版本 |
| `apiVersion` | 是 | `1`（web-importer）或 `2`（app / native） |
| `type` | 是 | `web-importer`、`app` 或 `native` |
| `entry` | 否 | 入口脚本，默认 `main.js`，必须是包内相对路径 |
| `startUrl` | web-importer 必填 | 点击模块时打开的 http(s) 地址，通常是学校教务入口 |
| `iconKey` | 否 | `extension`、`school`、`water`、`calendar` 等内置图标键，默认 `extension` |
| `author` | 否 | 作者/组织名 |
| `requires` | 否 | 仅 app 模块：依赖的其他模块 id 列表（插件树），安装后会先加载依赖 |

## 3. main.js

模块必须导出 `parse()`。它不接收参数，用 `host` 读取被抓取的页面。

```js
function parse() {
  var title = host.text(0, "#kbgrid_table .title") || "我的课表";
  var names = JSON.parse(host.texts(0, ".course .name"));

  return {
    name: title,
    semesterStart: "2026-09-07", // 可留空，用户之后在课表设置补充
    totalWeeks: 20,
    courses: [
      {
        name: "高等数学",
        teacher: "张三",
        location: "三教 A101",
        color: 3, // 可选，0–19
        slots: [
          { day: 1, start: 1, end: 2, weeks: [1, 2, 3, 4] },
          { day: 3, start: 3, end: 4, weeks: [] }
        ]
      }
    ]
  };
}
```

`parse()` 可以返回 JS 对象，也可以返回 JSON 字符串。

### host API

Rhino 沙箱不暴露 Android、Java 包、网络或文件系统；模块只能使用以下只读 API：

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `host.pageCount()` | number | 抓取到的页面数量 |
| `host.page(index)` | string | 原始 HTML |
| `host.text(index, selector)` | string | 第一个匹配元素的文本 |
| `host.texts(index, selector)` | string | 所有匹配元素文本的 JSON 数组字符串 |
| `host.attr(index, selector, name)` | string | 第一个匹配元素的属性 |
| `host.attrs(index, selector, name)` | string | 所有匹配元素属性的 JSON 数组字符串 |
| `host.html(index, selector)` | string | 第一个匹配元素的 outerHTML |
| `host.count(index, selector)` | number | 匹配数量 |
| `host.log(message)` | void | 写入应用日志，截断到 500 字符 |

页面索引从 0 开始。`selector` 使用 Jsoup CSS 选择器；`texts`/`attrs` 返回字符串，需要 `JSON.parse(...)`。

沙箱限制：

- 不能访问 `java`、`Packages`、Android API、网络、文件或凭据。
- 不能调用系统命令、读取应用数据或修改宿主状态。
- 脚本只应依赖自带的纯 JS 和上述 host API；不要使用 `eval` 加载远程代码。
- 模块只能看到用户主动点击「导入」时抓取的页面 HTML，不接触密码或 Cookie。

### 返回结构

```json
{
  "name": "课表名称",
  "semesterStart": "2026-09-07",
  "totalWeeks": 20,
  "courses": [
    {
      "name": "课程名",
      "teacher": "教师",
      "location": "教室",
      "color": 3,
      "slots": [
        { "day": 1, "start": 1, "end": 2, "weeks": [1, 2, 3] }
      ]
    }
  ]
}
```

- `day`：1=周一 … 7=周日。
- `start`/`end`：节次，当前内置作息为 1–12 节；超出部分会被截断。
- `weeks`：空数组表示该时间段每周都上；非空时自动与课表总周数求交集。
- `semesterStart` 无效时留空，应用会提示用户补充。
- `courses` 为空或没有有效时间段时导入失败；模块应尽量返回清晰的错误或完整数据。

## 4. 应用模块（API v2）

`app` 模块的 `main.js` 导出 `apply(ctx)`，在模块启用时常驻运行，停用时调用 `ctx.dispose` 注册的钩子。沙箱限制与 v1 相同（无 Java/Android/网络/文件系统），新增以下 ctx 能力：

| 能力 | API | 说明 |
| --- | --- | --- |
| 日志 | `ctx.log(msg)` | 写应用日志 |
| 存储 | `ctx.storage.get/set/remove/clear/keys` | 命名空间 KV（单模块 ≤256KB，键值 ≤64/≤16KB） |
| 事件 | `ctx.events.on(channel, fn)` / `emit(channel, data)` | 异步总线；emit 不回投自身；宿主事件 `timetable.updated`、`plugin.loaded/unloaded` |
| 组件 | `ctx.ui.homeWidget(spec)` | 注册主页组件（声明式视图，每模块 ≤4 个） |
| 页面 | `ctx.ui.screen(spec)` | 注册独立页面，插件页点按模块进入；再次调用即刷新 |
| 服务 | `ctx.services.provide(name, api)` / `call(id, name, method, ...)` | 跨插件同步调用 |
| 卸载 | `ctx.dispose(fn)` | 停用/移除时回调 |

声明式视图是白名单 JSON 树，节点类型 `column/row/card/text/icon/button/spacer/divider`，属性 `text/icon/emphasis/size/action/spacing/children`；带 `action` 的节点被点击时向模块投递 `action:<name>` 事件。字段越界会被宿主降级而非报错（详见架构文档 §6）。

最小示例（计数器主页组件 + 独立页面）：

```js
function apply(ctx) {
  var count = parseInt(ctx.storage.get("count", "0"), 10);

  function body() {
    return { type: "column", spacing: 6, children: [
      { type: "text", text: String(count), size: "title", emphasis: "primary" },
      { type: "button", text: "＋1", action: "inc" }
    ]};
  }

  function render() {
    ctx.ui.homeWidget({
      id: "counter", title: "计数器", iconKey: "star",
      columns: 1, rows: 1,
      view: body()
    });
    ctx.ui.screen({
      title: "计数器", iconKey: "star",
      view: { type: "column", spacing: 12, children: [
        { type: "card", children: body().children },
        { type: "text", text: "详情页由宿主原生渲染", size: "small" }
      ]}
    });
  }

  ctx.events.on("action:inc", function () {
    count += 1;
    ctx.storage.set("count", String(count));
    render();
  });

  render();
}
```

注意：`card` 等容器只能通过 `children` 嵌套，没有 `view` 字段；嵌套时需要展开子节点（如上例）。

完整可安装示例：`demo-app.osplugin`（打包产物在 `feature/plugins/src/main/assets/`，源码在 `market/samples/demo-app/`），市场条目 `demo.app`。重新打包：

```bash
cd market/samples/demo-app && zip -r ../../../feature/plugins/src/main/assets/demo-app.osplugin .
sha256sum feature/plugins/src/main/assets/demo-app.osplugin
```

改完示例后请同步 `market/catalog.json`、`feature/plugins/src/main/assets/market_catalog.json` 里的 `version`/`sha256`/`sizeBytes`；`core:data` 的 `DemoModulePackageTest` 会直接加载这个包，用不上或结构失效时测试会报错。

调试提示：`apply` 超时 6 秒、事件处理器 1.5 秒；插件页可查看模块状态（运行中/已停用/缺少依赖/出错），`ctx.log` 输出可用「设置 → 反馈与日志 → 导出日志」查看。依赖（`requires`）缺失时模块显示阻塞原因，安装依赖后自动恢复。

## 5. 开发与调试

1. 新建目录，写入 `plugin.json`、`main.js` 和需要的资源。
2. 压缩为 zip，再改名为 `<id>-<version>.osplugin`。
3. 计算 SHA-256 和文件大小：

```bash
sha256sum demo.web-importer-1.0.0.osplugin
stat -c %s demo.web-importer-1.0.0.osplugin
```

4. 调试入口：
   - 当前市场没有「从文件安装」按钮。开发阶段可以把模块放进 `feature/plugins/src/main/assets/`，在 `market/catalog.json` 用 `asset://<文件名>` 作为 `url`，重新构建应用测试。
   - 正式测试应把 `.osplugin` 上传到 HTTPS 地址，在目录里填写真实 URL、SHA 和大小，然后在应用内下载安装。
   - 使用 `host.log()` 和应用内「设置 → 反馈与日志 → 导出日志」排查解析问题。
5. 测试页面至少覆盖：正常课表、空页面、单双周、多个时间段、缺少教师/教室、页面结构变化。

## 6. 应用内安装与存储

- 下载先写入缓存；校验 `sha256` 通过后才解压。SHA 不匹配会拒绝安装。
- 解压到应用私有目录：`filesDir/modules/<id>/`。
- 启用状态保存在 SharedPreferences `openschedule_modules` 的 `enabled_ids`。
- 模块不会出现在系统应用列表，不申请 Android 权限，不会安装 APK。
- 在「插件市场」移除模块会删除其私有目录；重新添加即可恢复。
- 当前更新模块需要先「移除」旧版本，再下载新版本；原地升级会在后续版本补充。

## 7. 审核与发布流程（维护者）

### 7.1 接收提交

第三方开发者通过 GitHub Issue 提交，至少包含：

- 模块名、`id`、版本、作者/联系方式、许可证。
- `.osplugin` 下载地址、SHA-256、文件大小。
- 完整源码地址（GitHub/Gitee 仓库或 gist），以及构建/打包方式。
- `plugin.json` 和 `main.js` 的说明。
- 测试页面/测试步骤；不要提交真实账号、密码或含个人信息的页面。
- 模块访问的网址、解析的字段、已知限制。

### 7.2 审核清单

1. **格式**：manifest 字段完整、id 唯一、apiVersion 与 type 匹配（1=web-importer，2=app/native）、entry/startUrl/requires 合法。
2. **源码**：JS 可读；无混淆/压缩后不可审计的代码；无远程加载代码；无 `eval` 远程内容。
3. **安全**：不尝试访问 Java/Android/网络/文件；不包含凭据、Cookie、学号或个人信息；不误导用户；不冒充官方。
4. **功能**：用保存的 HTML 或测试页面实际导入成功；处理空页面/结构变化；不无限循环。
5. **体积**：解压后 ≤ 8 MB、文件 ≤ 200、manifest ≤ 64 KB；脚本 6 秒内完成。
6. **合规**：许可证清晰；模块名称/图标不侵犯学校或第三方品牌；不包含付费绕过、攻击或批量抓取逻辑。
7. **完整性**：发布者给出的 SHA-256 与文件一致；下载地址为 HTTPS。

审核不通过时给出具体原因；不把用户真实页面提交到公开仓库。

### 7.3 存储与上架

推荐把审核通过的 zip 重新托管到官方 release，避免第三方删除或替换文件：

1. 在 GitHub 与 Gitee 创建/复用 release 标签 `modules-v1`，上传 `<id>-<version>.osplugin`。
2. 记录 Gitee 附件 URL（优先）与 GitHub 附件 URL（备份）。
3. 更新两份目录：
   - GitHub：`market/catalog.json`
   - Gitee：`main` 分支的 `market/catalog.json`（Gitee 仓库不存放应用源码）
   - 同步 `feature/plugins/src/main/assets/market_catalog.json` 作为离线兜底。
4. 目录条目：

```json
{
  "id": "demo.web-importer",
  "name": "示例网页导入模块",
  "description": "应用内 JS 模块示例",
  "category": "教务适配",
  "version": "1.0.0",
  "url": "https://gitee.com/PanSomeone/OpeningSchool/releases/download/modules-v1/demo.web-importer-1.0.0.osplugin",
  "sha256": "<64 位小写 SHA-256>",
  "sizeBytes": 815,
  "iconKey": "extension",
  "type": "module"
}
```

- `type` 必须是 `module`；`url` 必须是 HTTPS 或内置 `asset://`。
- 不要把 `packageName` 填给模块；它是旧 APK 适配器字段。
- 应用目录优先读取 Gitee 的 `market/catalog.json`，失败时回退 GitHub raw。
- 目录更新后无需发布新版 APK；用户在插件市场点刷新即可看到。模块本身仍需用户手动下载并点「启用模块」。

### 7.4 更新、回滚与下架

- **版本更新**：保持 `id` 不变，提升 manifest `version`，上传新 zip，更新目录中的 `version`、`url`、`sha256`、`sizeBytes`；当前客户端需要用户先移除旧模块再安装新版本。
- **回滚**：把目录条目改回旧版本的 URL/SHA/版本；保留历史附件，不要覆盖同名文件。
- **下架**：从两份目录中删除条目；已安装用户仍保留本地模块，需要在插件市场手动移除。后续版本会增加远程禁用列表。
- **安全事件**：立即下架并发布公告；保留审核记录和原始 zip 以便追溯。

### 7.5 审核记录与责任

建议在公开仓库维护一份审核记录（模块 id、版本、SHA、审核人、日期、结论、来源），不要公开测试账号或个人数据。官方目录只代表完成格式与安全审查，不代替开发者对内容、学校规定和许可证的责任。

## 8. 最小示例

仓库内置两个可安装的示例模块，源文件都在 [`feature/plugins/src/main/assets/`](https://github.com/PanSomeone/OpeningSchool/tree/main/feature/plugins/src/main/assets)，目录条目见 [`market/catalog.json`](https://github.com/PanSomeone/OpeningSchool/blob/main/market/catalog.json)：

- `demo.web-importer`（v1）：演示 manifest、`host.text`、返回对象以及多时间段课表。实际学校模块应替换 `startUrl` 和选择器，并在真实页面测试。
- `demo.app`（v2）：演示 `apply(ctx)`、主页组件、事件与存储。

## 9. 相关文档

- 原生插件（APK）开发：[NATIVE_PLUGIN_SDK.md](NATIVE_PLUGIN_SDK.md)
- 全插件架构规格：[PLUGIN_ARCHITECTURE.md](PLUGIN_ARCHITECTURE.md)
- 旧版外部 APK 适配器协议（仅兼容）：[ADAPTER_API.md](ADAPTER_API.md)
- 市场目录与安全边界：[PLUGIN_MARKET.md](PLUGIN_MARKET.md)
- 应用更新与发布：[BACKUP.md](BACKUP.md)
