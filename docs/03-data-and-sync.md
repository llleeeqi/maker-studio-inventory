# 本地数据和同步

## 当前状态

当前检测版使用：

```text
app/storage.js -> localStorage
core/snapshot.js -> 手动导入/导出 JSON 快照
core/merge.js -> 本地/远程快照合并
```

这样做只是为了零依赖、马上能跑。它不是最终数据方案。

## 为什么存储层单独拆出来

库存规则在 `core/inventory.js`，存储实现在 `app/storage.js`。
快照协议在 `core/snapshot.js`。
合并策略在 `core/merge.js`。

原因：

- Web 检测版可以用 localStorage。
- Android APK 可以换成 SQL.js + Capacitor Filesystem。
- Docker/Web 版可以换成服务端 SQLite。
- WebDAV 同步只需要读写快照，不应该改库存算法。
- 手动导入/导出和 WebDAV 同步应该复用同一份快照格式。
- 同步合并应该独立于 WebDAV 传输层，手动合并导入和自动同步使用同一套规则。

## 推荐数据演进

### 当前

```text
localStorage JSON
```

优点：零依赖、方便调试。

缺点：不适合长期保存，不适合大数据量，不适合文件级同步。

### Android APK

```text
SQL.js SQLite 数据库
Capacitor Filesystem 保存 db 文件或快照
```

优点：离线稳定，数据库可导出。

### WebDAV 同步

```text
本地 SQLite/JSON 快照
  ↓
core/snapshot.js 导出 snapshot
  ↓
PUT 到 WebDAV
  ↓
GET 远程 snapshot
  ↓
core/snapshot.js 校验格式
  ↓
core/merge.js 按 updated_at 合并
```

单用户优先，冲突规则先保持简单：

```text
同一条记录，updated_at 新的覆盖旧的
```

当前已经实现手动合并导入：

```text
导入远程快照 -> 校验格式 -> 和本地状态合并 -> 写回本地
```

合并规则：

- `spools` / `parts`：同 ID 比 `updated_at`，新的保留。
- 远程有、本地没有：加入本地。
- 本地有、远程没有：保留本地。
- `transactions`：按物品、字段、时间、前后值生成 key 去重，再重新编号。

## 同步不进入用户操作主链路

用户操作应该是：

```text
扫码 -> 算库存 -> 写本地 -> 完成
```

同步应该是：

```text
后台定时 / 手动触发 -> 和 WebDAV 合并
```

这样即使网盘断开，扫码库存仍然可用。

## 当前快照格式

新格式：

```json
{
  "schema": "studio-inventory-snapshot",
  "version": 1,
  "exported_at": "2026-06-09T00:00:00.000Z",
  "state": {
    "spools": [],
    "parts": [],
    "transactions": []
  }
}
```

为了兼容早期检测版，也支持旧格式：

```json
{
  "spools": [],
  "parts": [],
  "transactions": []
}
```

导入时会做基础校验，避免把明显错误的 JSON 写进本地存储。

## 覆盖导入与合并导入

正式 app 里保留两个入口：

- 覆盖导入：用于恢复备份，直接用快照替换本地。
- 合并导入：用于同步场景，把远程快照和本地数据合并；导入前会先显示预览，包括远程新增、远程覆盖、本地保留和流水变化。

日常同步应优先用合并导入。
