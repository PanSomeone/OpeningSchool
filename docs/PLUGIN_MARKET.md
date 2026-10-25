# 插件市场目录与下载

插件市场读取 `market/catalog.json`。默认先读 Gitee 的 `main` 分支，失败时回退 GitHub raw；离线时使用 APK 内置的 `feature/plugins/src/main/assets/market_catalog.json`。

新插件是**应用内 JS 模块**（`.osplugin`），不再安装 APK。开发、审核、托管和发布流程见 [PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md)。

## 目录格式

```json
{
  "version": 2,
  "plugins": [
    {
      "id": "demo.web-importer",
      "name": "示例网页导入模块",
      "description": "应用内 JS 模块示例",
      "category": "教务适配",
      "version": "1.0.0",
      "url": "https://gitee.com/PanSomeone/OpeningSchool/releases/download/modules-v1/demo.web-importer-1.0.0.osplugin",
      "sha256": "下载后必须匹配的小写 SHA-256",
      "sizeBytes": 815,
      "iconKey": "extension",
      "type": "module"
    }
  ]
}
```

- `type: "module"`：应用内 JS 模块。下载后解压到 `filesDir/modules/<id>/`，启用后出现在课表导入列表；不会安装 APK。
- `type: "apk"`：旧版外部适配器，仅保留兼容；新目录不应再发布 APK 插件。
- `packageName` 只用于旧 APK 适配器；模块不要填写。
- `category` 含“工具”会进入「工具」分类，其余进入「适配器」。
- `sha256` 留空会显示“未校验”；公开目录必须填写。下载器会在安装/解压前校验 SHA-256。
- `url` 为 HTTPS，或内置测试用的 `asset://`；模块包必须含 `plugin.json`。
- 下载器每批最多并行 3 个；模块安装是应用内操作，不再弹系统安装器。

## 发布模块

1. 按 [PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md) 完成 manifest、`main.js`、打包和审核。
2. 把审核通过的 `.osplugin` 上传到官方 release 标签 `modules-v1`（Gitee 与 GitHub 各一份）。
3. 计算 SHA-256 和文件大小。
4. 更新 GitHub 根目录 `market/catalog.json`，并同步 Gitee `main` 的 `market/catalog.json` 与 APK 内置资产。
5. 在插件市场点刷新；目录更新不需要发布新版 APK。
6. 保留旧版本附件用于回滚；下架时从两份目录删除条目。

## 安全边界

- 目录和模块包都经 HTTPS 获取；解压前校验 SHA-256。
- 模块解压总大小 ≤ 8 MB、文件 ≤ 200、manifest ≤ 64 KB；非法路径、未知 API/类型会被拒绝。
- 模块只解压到应用私有目录，不安装 APK，不申请额外 Android 权限。
- JS 在 Rhino 沙箱中执行：不暴露 Java/Android/网络/文件，只能通过有限的 `host` API 读取用户主动抓取的页面 HTML；脚本 6 秒超时。
- SHA-256 代表完整性，不代表作者身份或安全性；上架前必须人工审核源码。
- 旧 APK 适配器只通过 Messenger JSON 通信，宿主不加载其代码；该机制仅为兼容保留。

## 相关文档

- 模块格式、JS API、审核与发布：[PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md)
- 旧版 APK 协议（仅兼容）：[ADAPTER_API.md](ADAPTER_API.md)
