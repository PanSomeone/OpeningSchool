> 📘 本文是专题附录。**主开发文档：[开发文档.md](开发文档.md)**

# 全插件架构（Plugin API v2）

本文是 OpeningSchool 插件体系的架构规格：目标、分层、运行时行为、安全边界与演进路线。开发者上手指南见 [PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md)；市场与审核见 [PLUGIN_MARKET.md](PLUGIN_MARKET.md)。

## 1. 目标与现状

目标是一个 DSH / VS Code 风格的宿主：应用外壳只保留导航、设置与安全边界，一切功能以插件形式挂载，插件之间通过显式契约（服务、事件、插槽）协作，而不是互相 import。

| 能力 | v1（1.0.0 已发布） | v2 JS 模块 | v2 原生插件 |
| --- | --- | --- | --- |
| 分发形态 | `.osplugin` zip | 相同 | 独立 APK |
| 模块类型 | `web-importer` 单一类型 | 增加 `app` 通用应用模块 | `HostPlugin` 实现类 |
| 生命周期 | 每次导入执行一次 `parse()` | 常驻：`apply(ctx)` / `dispose()` | 常驻：`onCreate` / `onDestroy` |
| UI 能力 | 无 | 声明式视图 + 主页组件/页面插槽 | **原生 Android View**（无 WebView） |
| 数据 | 无 | 命名空间 KV 存储 | 命名空间 KV + `dataDir()` |
| 通信 | 无 | 事件总线 + 跨插件服务调用 | 事件总线 |
| 依赖 | 无 | manifest `requires` 插件树 | 随 APK 自行打包 |
| 引擎 | Rhino 沙箱 | 相同（接口按引擎无关设计） | 同进程 DexClassLoader，无沙箱 |

v1 `web-importer` 模块完全兼容、不受影响；三类插件可并存。

## 2. 分层

```
┌────────────────────────────────────────────────┐
│ :app  AppShell / AppGraph                       │
│   组件插槽合并 · 宿主事件桥 · 插件页             │
├────────────────────────────────────────────────┤
│ :feature:plugins  安装/启停/移除 UI             │
├────────────────────────────────────────────────┤
│ :core:data                                        │
│   PluginRuntime（生命周期/事件/服务/依赖图）      │
│   PluginModuleStore（安装/校验/存储）             │
│   PluginDataStore（命名空间 KV）                  │
│   RhinoSandbox（共享 Rhino 沙箱）                 │
├────────────────────────────────────────────────┤
│ :core:plugin-api  契约层（纯模型，无实现）        │
│   PluginViewNode / PluginHomeWidget / PluginScreen │
├────────────────────────────────────────────────┤
│ :core:plugin-sdk  原生插件公共 SDK（仅 Android 类型）│
│   HostPlugin / PluginHost / NativeScreen/Widget  │
├────────────────────────────────────────────────┤
│ :core:ui  PluginViewRenderer（声明式视图原生渲染）│
│           NativePluginView（AndroidView 宿主）    │
└────────────────────────────────────────────────┘
```

设计约束：

- **契约与实现分离**：`core:plugin-api` 只放数据结构与插槽 ID，供宿主与插件运行时共同依赖；渲染器在 `core:ui`，执行引擎在 `core:data`。替换引擎（如 QuickJS）或渲染器不触碰契约层。
- **一切跨边界数据走白名单**：JS 传来的组件树、manifest、存储键值全部经过 `PluginViewValidator` / `ModuleManifestValidator` / `PluginDataStore` 上限约束，宿主从不执行或渲染“没见过的形状”。
- **单线程串行执行**：所有插件 JS 在 IO 调度器上、同一把互斥锁内串行执行；事件投递与动作分发异步排队，处理器不会在宿主持锁时重入。

## 3. 模块清单（plugin.json）

```json
{
  "id": "demo.app",
  "name": "示例应用模块",
  "description": "…",
  "version": "1.0.0",
  "apiVersion": 2,
  "type": "app",
  "entry": "main.js",
  "iconKey": "star",
  "author": "OpeningSchool",
  "requires": []
}
```

| 字段 | 说明 |
| --- | --- |
| `apiVersion` | `1`=web-importer 旧协议；`2`=本架构（`app` / `native`） |
| `type` | v2 为 `app`（JS 常驻）或 `native`（DEX 原生，见 `NATIVE_PLUGIN_SDK.md`） |
| `requires` | 依赖的其他模块 id（插件树） |

校验规则（`ModuleManifestValidator`）：id 匹配 `^[a-z0-9][a-z0-9._-]{1,63}$`；`apiVersion 1` 只接受 `web-importer`，`apiVersion 2` 接受 `app` 或 `native`（`native` 还需合法的 `entryClass` 与 `.dex` 入口）；导入模块必须有 http(s) `startUrl`；`entry` 不能越出包目录；依赖不能重复、不能自依赖。

## 4. 生命周期

```
安装（市场下载 → SHA-256 校验 → 解压到 filesDir/modules/<id>/）
   ↓ 启用
sync：依赖解析 → 拓扑排序 → 逐个加载
   ↓
evaluate main.js（一次）
   ↓ apply(ctx) 导出则调用
运行（常驻：事件、动作、组件注册）
   ↓ 停用/移除/依赖不可用
dispose 处理器（注册于 ctx.dispose）→ 卸载
```

- `apply(ctx)` 可选；不导出 apply 的模块是纯声明模块（合法但通常无用）。
- `apply` 超时 6 秒；事件处理器/动作超时 1.5 秒；超时抛错并计入模块状态。
- 模块状态机：`Loading → Active / Error`；依赖缺失或循环 → `Blocked`（显示原因）；停用后从运行时摘除，数据保留。
- **插件树**：`requires` 经 `PluginDependencyGraph` 解析——依赖先行加载；缺失依赖阻塞自身及其全部下游；循环依赖整体阻塞。宿主移除某模块后，依赖它的模块在下次 sync 自动降级为 Blocked。

## 5. ctx API

```js
function apply(ctx) { /* ctx 只在本模块作用域内有效 */ }
```

### ctx.log(message)
写应用日志（`Plugin/<id>` 标签，截断 500 字符）。

### ctx.storage —— 命名空间 KV
| 方法 | 说明 |
| --- | --- |
| `get(key, fallback?)` | 读取，返回字符串 |
| `set(key, value)` | 写入（键 ≤64、值 ≤16KB、单模块总量 ≤256KB） |
| `remove(key)` / `clear()` | 删除 |
| `keys()` | JSON 数组字符串 |

物理上每个模块一个 `filesDir/plugin-data/<id>.json`，模块间互不可见。复杂数据由模块自行 `JSON.stringify`。

### ctx.events —— 事件总线
| 方法 | 说明 |
| --- | --- |
| `on(channel, handler)` | 订阅；handler 收到的是已解析的 JS 值 |
| `emit(channel, data)` | 广播给**其他**模块（不含自己，防反馈环） |

- 投递异步：emit 立即返回，稍后在 IO 线程串行执行订阅者。
- 频道名自由；约定 `myplugin/xxx` 私有前缀。宿主保留频道：`timetable.updated`、`plugin.loaded`、`plugin.unloaded`。
- 视图动作也是事件：声明式节点带 `action` 时，点击后宿主向该模块投递 `action:<name>`。

### ctx.ui —— 插槽
| 方法 | 说明 |
| --- | --- |
| `homeWidget(spec)` | 注册/替换主页组件（按 `id` 幂等），每模块 ≤4 个 |
| `screen(spec)` | 注册/替换本模块的独立页面，再次调用即刷新内容 |

`homeWidget` spec：`{ id, title, iconKey, columns(1-2), rows(1-2), view }`。
`screen` spec：`{ title, iconKey, view }`。已实现的插槽见 §6；路线图见 §9。

### ctx.services —— 跨插件服务
| 方法 | 说明 |
| --- | --- |
| `provide(name, apiObject)` | 对外暴露服务对象（方法为 JS 函数） |
| `call(pluginId, name, method, ...args)` | 同步调用其他运行中模块的服务 |

同步执行且继承当前时间预算；提供方未运行/未提供时抛错（模块进入 Error 或处理器失败日志）。

### ctx.dispose(handler)
注册卸载钩子，模块停用/移除时调用（超时 1.5 秒，失败仅记日志）。

## 6. 声明式视图与插槽

插件不能触碰 Compose/Android；UI 通过 JSON 树声明，宿主用 `PluginViewRenderer` 原生渲染。

节点类型（`PluginViewTypes` 白名单）：`column`、`row`、`card`、`text`、`icon`、`button`、`spacer`、`divider`。

| 属性 | 取值 | 说明 |
| --- | --- | --- |
| `text` | ≤200 字符 | 文本/按钮文字 |
| `icon` | NavIcons 键 | 图标键，如 `star`、`water` |
| `emphasis` | none/primary/secondary/error | 颜色语义 |
| `size` | small/medium/large/title | 字号/图标尺寸 |
| `action` | ≤64 字符 `[a-zA-Z0-9._-]` | 点击动作名 → `action:<name>` 事件 |
| `spacing` | 0–24 | 容器子项间距（dp） |
| `children` | 容器节点有效 | 深度 ≤8、总数 ≤64、每层 ≤16 |

校验在 `PluginViewValidator.sanitize` 完成并**降级而非拒绝**：未知类型变 text、非法 emphasis/size 回退默认、超出上限截断。组件尺寸强制 1–2 列 × 1–2 行。

已实现插槽：

- `/home/widget` —— 主页画布组件。宿主把 `ActiveWidget` 合并进 `HomeWidgetSpec` 列表，与热水/GPA 组件同一套拖拽/吸附/尺寸逻辑；组件 id 形如 `module:<pluginId>:<widgetId>`，布局持久化稳定。
- `/nav/page` —— 独立页面。`ctx.ui.screen({title, iconKey, view})` 注册后，插件页对应模块行变为可点，进入宿主提供的 `ModuleScreen`（自带标题栏与返回）。动作触发后模块再次调用 `screen()` 即可刷新，页面内容随 `PluginRuntime.screens` 流即时重组；模块停用/移除时页面自动消失。

预留插槽（manifest/规格已保留，未开放）：`/timetable/import`（导入源）、`/settings/section`（设置分节）。

## 7. 原生插件（APK）

除 JS 模块外，第三方还可以用普通 Android APK 写原生插件：插件用自己的 Kotlin/Java 构建 `android.view.View`，宿主通过 `AndroidView` 嵌入。

| 环节 | 实现 |
| --- | --- |
| 发现 | `NativePluginCatalog`：`<queries>` + 标记 action，无需 `QUERY_ALL_PACKAGES` |
| 校验 | 入口类名正则、API 版本区间、包名排除自身 |
| 加载 | `NativePluginLoader` + `DexClassLoader`（父加载器 = 宿主） |
| 生命周期 | `onCreate(host)` / `onDestroy()`，主线程，可随时启停 |
| 插槽 | `screens()` → 页面；`widgets()` → 主页组件（`native:<包名>:<id>`） |
| 存储/事件 | 复用 `PluginDataStore`（包名为命名空间）+ 宿主事件总线 |
| 渲染 | `NativePluginView` / `NativePluginWidgetView`（`AndroidView`） |

安全模型与其他插件不同：原生插件**同进程、同 UID、无沙箱**，属于可信代码。宿主只能保证不自动下载与执行，并要求用户显式启用；开发者文档见 [NATIVE_PLUGIN_SDK.md](NATIVE_PLUGIN_SDK.md)。

契约只使用 Android 框架类型，因此宿主开启 R8 后插件依然可用（`consumer-rules.pro` 保留 SDK 包名，插件类始终从自身 APK 加载）。

## 8. 安全模型

1. **沙箱**：Rhino `ClassShutter` 全拒 + 指令计数器时限；无 Java/Android/网络/文件系统访问。v1 与 v2 共用同一 `RhinoSandbox`（嵌套执行继承外层预算，防止在处理器里刷新时限）。
2. **安装**：市场下载强制 SHA-256；解压校验 zip 路径穿越、体积（≤8MB）、文件数（≤200）、manifest 大小（≤64KB）。
3. **存储**：每模块独立文件 + 三重上限，无法耗尽应用存储。
4. **事件**：异步投递 + 不含自身 + 处理器时限，杜绝同步递归风暴；数据经 `JsonParser` 解析（非 eval）。
5. **视图**：白名单类型 + 全字段降级，无法注入任意布局参数或长文本。
6. **服务**：只能调用“正在运行”的模块，调用链共享时间预算，无法借服务逃逸沙箱。

## 9. 宿主事件桥

`AppShell` 观察课表数据流，同时向 JS 运行时与原生插件运行时广播 `timetable.updated`；`plugin.loaded` / `plugin.unloaded` 在依赖变动或加载/卸载时自动广播。宿主后续接入新事件（天气、设置变更）只需调用 `pluginRuntime.emitHostEvent(...)` 与 `nativePlugins.emitHostEvent(...)`。

## 10. 路线图

| 阶段 | 内容 |
| --- | --- |
| v2.0（本次） | 生命周期、存储、事件、服务、`/home/widget` 与 `/nav/page` 插槽、插件树、示例模块 |
| v2.1 | 原地升级（免移除重装）、远程禁用清单、`/timetable/import` 插槽（app 模块兼任导入源） |
| v2.2 | `/settings/section` 插槽、页面内嵌套滚动容器与列表节点 |
| v3 | 引擎替换评估：QuickJS（更完整的 ES2020+、独立线程）、组件声明式状态绑定 |
| 长期 | 内置功能（热水/成绩）迁移为同契约的内置插件包，宿主只留外壳 |

内置功能迁移原则：先让 JS 模块能力追平（网络受限 http 服务、后台刷新），再逐个搬迁；迁移期间 Kotlin 内置插件与 JS 模块走同一套插槽合并逻辑（`AppShell` 已经把两者放进同一个 `externalWidgets` 列表）。

## 10. 关键实现索引

| 关注点 | 位置 |
| --- | --- |
| 契约模型 | `core/plugin-api/.../PluginView.kt`、`PluginSlots` |
| 原生插件 SD​K | `core/plugin-sdk/.../HostPlugin.kt` |
| 原生插件发现/加载 | `core/data/.../NativePluginCatalog.kt`、`NativePluginRuntime.kt`、`NativePluginLoader.kt` |
| 原生视图宿主 | `core/ui/.../NativePluginView.kt` |
| 示例原生插件 | `plugin/sample-native/` |
| 沙箱 | `core/data/.../RhinoSandbox.kt` |
| 运行时 | `core/data/.../PluginRuntime.kt` |
| 依赖图 | `core/data/.../PluginDependencyGraph.kt` |
| 视图校验 | `core/data/.../PluginViewValidator.kt` |
| 存储 | `core/data/.../PluginDataStore.kt` |
| 清单校验 | `core/data/.../ModuleManifestValidator.kt` |
| 安装/启停 | `core/data/.../PluginModuleStore.kt` |
| 渲染器 | `core/ui/.../PluginViewRenderer.kt` |
| 宿主接线 | `app/.../AppShell.kt`、`OpenScheduleApp.kt` |
| 模块管理 UI | `feature/plugins/.../PluginsScreen.kt` |
| 测试 | `core/data/src/test/.../PluginRuntimeTest.kt` 等（39 例，含示例包端到端） |
