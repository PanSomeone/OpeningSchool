# 插件市场目录与下载

插件市场读取 `market/catalog.json`（主源为 GitHub raw，离线时使用 APK 内置的同一份资产）。目录中的条目是**独立外部适配器 APK**，不是动态加载的代码。

## 目录格式

```json
{
  "version": 1,
  "plugins": [
    {
      "id": "adapter-id",
      "name": "显示名称",
      "description": "说明",
      "category": "教务适配",
      "version": "1.0.0",
      "url": "https://…/Adapter.apk",
      "sha256": "下载后必须匹配的小写 SHA-256",
      "sizeBytes": 123456,
      "packageName": "com.example.adapter",
      "iconKey": "school"
    }
  ]
}
```

- `category` 含“工具”会进入「工具」分类，其余进入「适配器」。
- `sha256` 留空会显示“未校验”；公开目录必须填写，下载器会在写入安装包前校验。
- `packageName` 用于判断是否已安装。
- 当前下载器每批最多并行 3 个；批量下载不会自动安装，安装由系统确认。

## 发布新适配器

1. 在 `adapter/` 下实现协议，或复制 `adapter/sample-external` 骨架。
2. 用 release 密钥构建 `assembleRelease`，把 APK 作为 GitHub Release 资产上传。
3. 计算 `sha256sum` 并写入目录。
4. 更新 `market/catalog.json`，同时复制到 `feature/plugins/src/main/assets/market_catalog.json`，提交并推送。

## 安全边界

- 目录与 APK 都经 HTTPS 获取；下载后校验 SHA-256。
- 安装使用系统安装器，用户必须手动确认；应用不会静默安装。
- 外部适配器只能通过 `com.openschedule.plugin.ADAPTER` Service 返回 JSON，宿主不加载第三方代码。
- 如果将来加入插件签名校验，应把官方公钥固定在客户端，并拒绝未签名/签名不匹配的包。
