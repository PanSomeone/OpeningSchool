> 📘 本文是专题附录。**主开发文档：[开发文档.md](开发文档.md)**

# 原生插件 SDK

本页写给第三方开发者：如何给开源大学写**原生插件**——用 Kotlin/Java 真正构建 `android.view.View`，宿主用 `AndroidView` 嵌进 Compose 界面，和内置的热水、成绩模块同一层级、同一套交互。没有 WebView，没有 HTML，也没有 JS 沙箱。

原生插件有两种交付形态，**优先选 dex 模块**：

| 形态 | 用户需要装 APK？ | 桌面/应用列表出现？ | 适合 |
| --- | --- | --- | --- |
| **dex 模块**（`.osplugin` + `classes.dex`，`type: "native"`） | 不需要，应用内下载 | 不会 | 绝大多数插件（**推荐**） |
| **APK 插件** | 需要手动安装 | 不在桌面（无 LAUNCHER activity），但会出现在系统应用列表 | 需要自带资源、Activity、权限等完整 App 能力 |

两种形态共用同一套 `HostPlugin` 契约，代码可直接切换；dex 模块的体积也小得多（示例：dex 模块 6.4 KB vs APK 0.9 MB）。

## 0. dex 模块（推荐）

包结构：

```
demo.native.osplugin          # 普通 zip
├── plugin.json
└── classes.dex               # d8 产物
```

```json
{
  "id": "demo.native",
  "name": "示例原生插件",
  "version": "1.0.0",
  "apiVersion": 2,
  "type": "native",
  "entry": "classes.dex",
  "entryClass": "com.example.myplugin.MyPlugin",
  "iconKey": "science"
}
```

打包（把插件的 `classes.jar` 编译成 dex）：

```bash
# 1. 用 Android 库模块产出 classes.jar（compileOnly 依赖插件 SDK）
# 2. 转 dex，min-api 不能低于 26
$ANDROID_HOME/build-tools/<ver>/d8 --lib $ANDROID_HOME/platforms/android-37/android.jar \
    --min-api 26 --output out/ classes.jar
# 3. 与 plugin.json 一起压缩，扩展名改为 .osplugin
```

约束：

- `entry` 必须是 `.dex`；`entryClass` 必须是全限定类名且实现 `HostPlugin`。
- **运行时只保证两点**：Android 框架类（`android.*`、`java.*`）与 **Kotlin 标准库**由宿主提供（宿主已用 `-keep class kotlin.**` 保留，release 包可用）。插件若用其他库（如 kotlinx.coroutines、OkHttp），必须随 dex 一起打进包——否则 release 包会报 `NoClassDefFoundError`。
- **没有自己的 `Context` 资源**：`createView` 拿到的是宿主 Context，插件应纯代码构建视图；需要图片就随包放资源文件并用 `dataDir()` 读取。
- 需要自有资源/Activity 时才改用下面的 APK 形态。

## 1. 架构与信任模型

| 项 | dex 模块 | APK 插件 |
| --- | --- | --- |
| 交付形态 | 应用内 `.osplugin` | 独立 APK，手动安装 |
| 发现方式 | 模块商店安装后由模块存储发现 | `<queries>` 查询 `com.openschedule.plugin.NATIVE`（不需要 `QUERY_ALL_PACKAGES`） |
| 加载方式 | `InMemoryDexClassLoader(classes.dex, 宿主 CL 为父)` | `DexClassLoader(插件 APK, 宿主 CL 为父)` |
| UI 嵌入 | 插件返回 `View`，宿主经 `AndroidView` 渲染 | 同左 |
| 进程 | 与宿主同进程、同 UID | 同左 |
| 信任级别 | **等同于宿主自身代码** | 同左 |

因为是同进程加载，安全边界只有两条：

1. 宿主在加载前校验清单（入口类名、API 版本、dex 入口），插件代码本身不受沙箱限制；
2. 插件需用户显式启用；APK 形态还需用户手动安装，宿主不会自动下载或执行 APK。

请只安装来源可信的插件；插件作者只能拿到自己声明的 ID 命名空间存储。

## 2. 插件 APK 清单

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application android:label="@string/plugin_name">

        <!-- 入口类：必须实现 com.openschedule.plugin.sdk.HostPlugin -->
        <meta-data
            android:name="com.openschedule.plugin.ENTRY"
            android:value="com.example.myplugin.MyPlugin" />

        <!-- SDK 版本，宿主据此判断兼容性 -->
        <meta-data
            android:name="com.openschedule.plugin.API"
            android:value="1" />

        <!-- 可选：列表里的图标键（见宿主 NavIcons 目录），默认 extension -->
        <meta-data
            android:name="com.openschedule.plugin.ICON"
            android:value="science" />

        <!-- 可选：显示名覆盖，默认用 APK 的 android:label -->
        <meta-data
            android:name="com.openschedule.plugin.NAME"
            android:value="我的插件" />

        <!-- 发现标记：只需存在且 exported，宿主不会启动它 -->
        <service android:name=".MarkerService" android:exported="true">
            <intent-filter>
                <action android:name="com.openschedule.plugin.NATIVE" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

`MarkerService` 可以是空实现：

```kotlin
class MarkerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
```

## 3. 依赖 SDK

SDK 是仓库内的 `:core:plugin-sdk` 模块，只依赖 Android 框架类型（无 Compose、无 app 内部模型），因此插件可以用任意 Android 工具链编译，也不受宿主 R8 混淆影响。

```bash
./gradlew :core:plugin-sdk:assembleRelease
# 产物：core/plugin-sdk/build/outputs/aar/plugin-sdk-release.aar
```

插件工程里引用：

```kotlin
dependencies {
    // 建议 compileOnly：契约类由宿主提供，插件 APK 不必内置
    compileOnly(files("libs/plugin-sdk-release.aar"))
}
```

如果插件需要在自己的 APK 里带一份 SDK（例如也想独立运行），用 `implementation` 也可以：运行时类加载器会优先命中宿主的那份，接口身份保持一致。

## 4. 实现 HostPlugin

```kotlin
package com.example.myplugin

import android.content.Context
import android.view.View
import android.widget.TextView
import com.openschedule.plugin.sdk.HostPlugin
import com.openschedule.plugin.sdk.NativeScreen
import com.openschedule.plugin.sdk.NativeWidget
import com.openschedule.plugin.sdk.PluginHost

class MyPlugin : HostPlugin {

    private lateinit var host: PluginHost

    override fun onCreate(host: PluginHost) {
        this.host = host
        // 读取上次保存的值；每个插件有独立的存储命名空间
        val count = host.getString("count", "0")
        host.log("插件已启动，count=$count")
    }

    override fun onDestroy() {
        host.log("插件已停止")
    }

    override fun screens(): List<NativeScreen> = listOf(
        NativeScreen(id = "main", title = "我的页面", iconKey = "star") { context ->
            buildView(context)
        },
    )

    override fun widgets(): List<NativeWidget> = listOf(
        NativeWidget(id = "card", title = "我的组件", iconKey = "star") { context ->
            buildView(context)
        },
    )

    private fun buildView(context: Context): View =
        TextView(context).apply {
            text = "原生控件"
            textSize = 16f
            setOnClickListener { host.putString("tapped", "1") }
        }
}
```

约定：

- `onCreate` / `onDestroy` / 每个 `createView` 都在主线程调用；`onCreate` 里不要做耗时 IO。
- `createView` 每次调用都返回**新的** View 实例（宿主可能在切页、重组时重建）。
- 每个插件最多 8 个页面、8 个组件；`id` 与 `title` 不能为空。
- 组件按 1×1 ~ 2×2 的单格网格渲染，布局请自行适配较小宽度。

## 5. PluginHost 能力

| 成员 | 说明 |
| --- | --- |
| `pluginId` | 插件 id（即包名） |
| `hostVersion` | 宿主版本号，用于兼容性判断 |
| `dataDir()` | 插件私有目录 |
| `log(message)` | 写入应用日志（设置 → 反馈与日志 → 导出日志） |
| `getString(key, fallback)` / `putString` / `remove` | 插件独立 KV 存储（键 ≤64、值 ≤16KB、单插件 ≤256KB） |
| `onEvent(channel, handler)` | 订阅事件，主线程回调，payload 为 JSON 字符串 |
| `emit(channel, payloadJson)` | 广播给其他运行中插件（不会回投自己） |

宿主事件：`timetable.updated`、`plugin.loaded`、`plugin.unloaded`。插件自定义频道建议加自己的前缀，例如 `myplugin/refresh`。

## 6. 宿主侧行为

- 插件安装后出现在「插件 → 原生插件（APK）」；默认启用，可随时关闭。
- 关闭后立即 `onDestroy` 并停止投递事件，存储保留。
- 有页面的插件整行可点；多个页面显示标签栏。
- 组件并入主页画布，和内置组件共用拖拽、吸附、尺寸交换。
- 插件升级（`versionCode`/`versionName` 变化）后宿主重新加载。
- API 版本高于宿主时显示「需要插件 API ≤ N」并不加载。

## 7. 调试

```bash
# 构建示例插件
./gradlew :plugin:sample-native:assembleDebug
adb install -r plugin/sample-native/build/outputs/apk/debug/sample-native-debug.apk

# 看完日志
adb logcat | Select-String "NativePlugin"
```

在应用内「插件」页点「重新扫描已安装插件」即可看到新装的 APK。示例插件的源码在 `plugin/sample-native/`，包含页面、组件、存储与事件订阅，可直接抄改。

## 8. 发布检查表

1. 清单里 `ENTRY` 指向真实类，`API` 与编译时 SDK 一致。
2. 入口类有 public 无参构造，且实现 `HostPlugin`。
3. 插件包名唯一，且不与宿主/其他插件冲突；不要冒用学校或官方名义。
4. 页面/组件 id 稳定（主页布局按 `native:<包名>:<组件 id>` 持久化）。
5. 说明清楚插件会读写什么数据、是否需要网络权限。
6. 提供源码地址与构建方式；宿主目录审核时只接受可审计的实现。

## 9. 已知限制

- 插件与宿主同进程：插件崩溃会导致宿主崩溃，请自行捕获异常。
- 插件不能直接调用宿主的内部 API（`core:model` 等未导出）；需要新能力请在 SDK 里加接口。
- 插件 UI 用 Android View；Compose 不作为公开契约（宿主 R8 会重命名 Compose 内部符号）。
- 目前不支持插件向导航栏添加独立入口，页面从插件行进入。
