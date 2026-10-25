# 外部适配器协议 v1（旧版兼容）

> **新插件请使用应用内 JS 模块。** 格式、JS API、审核和发布见 [PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md)。本文档仅用于兼容已经存在的 APK 适配器；插件市场目录不再发布新的 APK 插件。

旧版第三方适配器是一个**独立 APK 里的普通 Service**，通过 Android `Messenger` 与本应用交换 JSON 字符串。本应用只在用户点击「导入」时绑定并发送凭证。

## 1. 适配器 APK 需要声明的内容

`AndroidManifest.xml`：

```xml
<permission
    android:name="com.openschedule.permission.USE_ADAPTER"
    android:protectionLevel="normal" />

<uses-permission android:name="com.openschedule.permission.USE_ADAPTER" />

<application ...>
    <service
        android:name=".MyAdapterService"
        android:exported="true"
        android:permission="com.openschedule.permission.USE_ADAPTER">
        <intent-filter>
            <action android:name="com.openschedule.plugin.ADAPTER" />
        </intent-filter>
    </service>
</application>
```

- `exported="true"` 必须开启，否则宿主无法发现。
- `protectionLevel` 必须是 `normal`（本应用已声明同名权限，因此会被自动授予）。
- 若适配器需要联网，自行声明 `android.permission.INTERNET`。

## 2. 通信

宿主 `bindService` 后：

| 方向 | 消息 | 内容 |
| --- | --- | --- |
| 宿主 → 适配器 | `what = 1` (`MSG_DESCRIBE`) | `data.getString("request")` = `{}` |
| 宿主 → 适配器 | `what = 2` (`MSG_IMPORT`) | `data.getString("request")` = `AdapterRequest` JSON |
| 适配器 → 宿主 | 任意 `what` | `data.getString("reply")` = `AdapterReply` JSON，通过 `msg.replyTo` 发送 |

超时 15 秒；适配器每个请求只需回复一次，随后宿主会解绑。

## 3. 数据结构

### AdapterRequest

```json
{
  "apiVersion": 1,
  "config": { "username": "20260001", "password": "..." }
}
```

`config` 的键来自 `AdapterDescriptor.fields`，值由用户填写。

### AdapterDescriptor（`MSG_DESCRIBE` 的回复）

```json
{
  "code": 0,
  "descriptor": {
    "id": "my-school",
    "name": "某某教务",
    "description": "账号密码登录",
    "apiVersion": 1,
    "fields": [
      { "key": "username", "label": "学号" },
      { "key": "password", "label": "密码", "secret": true },
      { "key": "startDate", "label": "开学日期", "required": false, "placeholder": "2026-09-01" }
    ]
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 适配器内唯一；宿主展示为 `external:<包名>/<id>` |
| `name` | 显示名称 |
| `description` | 可选 |
| `apiVersion` | 必须是 `1`，否则宿主忽略该适配器 |
| `fields` | 导入表单；`secret` 仅影响输入回显，`required` 默认 `true`，`defaultValue` 可预填 |

### AdapterReply（`MSG_IMPORT` 的回复）

成功：

```json
{
  "code": 0,
  "timetable": { "...": "见下" },
  "warnings": ["3 条课程缺少教室"]
}
```

失败：

```json
{ "code": 3, "message": "登录失败：密码错误" }
```

| `code` | 含义 |
| --- | --- |
| `0` | 成功，必须带 `timetable` |
| `2` | 请求有误（缺少配置等） |
| `3` | 内部错误 / 解析失败 |
| 其他整数 | 宿主原样向用户展示 `message` |

### Timetable

```json
{
  "name": "2026-2027 第 1 学期",
  "semesterStart": "2026-09-01",
  "totalWeeks": 20,
  "periods": [
    { "index": 1, "start": "08:00", "end": "08:45" },
    { "index": 2, "start": "08:55", "end": "09:40" }
  ],
  "courses": [
    {
      "id": "c1",
      "name": "高等数学",
      "teacher": "张老师",
      "location": "A101",
      "day": 1,
      "startPeriod": 1,
      "endPeriod": 2,
      "weeks": [1, 2, 3, 4, 5],
      "color": 0,
      "note": ""
    }
  ]
}
```

约定：

- `semesterStart` 是第 1 周第一天的 ISO 日期（`yyyy-MM-dd`），可以为空。
- `day` 为 ISO 星期：周一 = 1 … 周日 = 7。
- `startPeriod` / `endPeriod` 指向 `periods` 中的 `index`；宿主按节次区间绘制跨节课程。
- `weeks` 为空表示每周都上；否则为具体周次。
- `color` 取 0…9，越界会被归零。
- 导入会整体替换当前课表，包括 `periods`、`semesterStart` 与 `totalWeeks`；`periods` 缺省时使用默认作息。

## 4. Kotlin 骨架

不依赖本仓库即可实现，只有 4 个小数据类需要对应：

```kotlin
class MyAdapterService : Service() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val request = msg.data?.getString("request").orEmpty()
            val reply = when (msg.what) {
                1 -> handleDescribe()
                2 -> handleImport(request)
                else -> """{"code":2,"message":"未知请求"}"""
            }
            runCatching {
                msg.replyTo?.send(Message.obtain(null, 0).apply {
                    data = Bundle().apply { putString("reply", reply) }
                })
            }
        }
    })

    override fun onBind(intent: Intent?): IBinder? = replyMessenger.binder

    private fun handleDescribe(): String = """
        {
          "code": 0,
          "descriptor": {
            "id": "my-school",
            "name": "某某教务",
            "apiVersion": 1,
            "fields": [
              {"key": "username", "label": "学号"},
              {"key": "password", "label": "密码", "secret": true}
            ]
          },
          "apiVersion": 1
        }
    """.trimIndent()

    private fun handleImport(request: String): String {
        // 解析 AdapterRequest.config，自行联网抓取并转换为 Timetable JSON。
        // 网络与解析请放在工作线程，15 秒内回复。
        return """{"code": 3, "message": "未实现"}"""
    }
}
```

## 5. 安全与隐私约定

- 凭证只在本应用的导入表单内存中停留，不会写入磁盘；适配器如需保存，自行负责并在 `description` 中说明。
- 宿主不做任何证书绕过、不注入、不加载 DEX。
- 适配器应使用官方或公开接口，不绕过付费与鉴权；发布前请确认目标学校的使用条款。
- 适配器回包内容完全由第三方生成，宿主仅做 JSON 解析，不执行其中的代码。
