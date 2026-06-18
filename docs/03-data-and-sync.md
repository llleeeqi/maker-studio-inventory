# 本地数据和同步

## 当前结论

原生 Android v1 先用 JSON snapshot 文件，不上数据库。

运行方式：

```text
启动时读取 JSON 到内存
扫码和按钮操作修改内存
每次写操作后覆盖保存 JSON 文件
```

后续换 SQLite/Room 时，把 JSON 作为迁移源导入。

## 本地文件

建议文件：

```text
filesDir/inventory_snapshot.json
```

建议结构：

```json
{
  "schema": 1,
  "device_id": "android-phone",
  "items": {},
  "transactions": [],
  "scan_log": []
}
```

`items` 只保存已入库或曾经入库、出库、归档过的物品。不保存纯标签草稿。

## item 记录

一条 item 同时保存固定字段副本和当前变量。

```json
{
  "id": "FIL-260617-001",
  "type": "spool",
  "fixed": {
    "brand": "Bambu",
    "material": "PLA",
    "color": "white",
    "tare_g": 200
  },
  "state": {
    "status": "in_stock",
    "current_g": 712.4,
    "location_id": "LOC-260617-001",
    "location_name": "A架第一层",
    "stocked_on": "260617",
    "updated_at": "2026-06-17T10:00:00+00:00"
  }
}
```

二维码是固定信息来源，本地 item 是查库存和保存当前状态的依据。

## 容量策略

第一版总量控制在约 1000 条：

```text
items: 600
transactions: 350
scan_log: 50
```

裁剪规则：

```text
in_stock 不自动删
items 超过上限时优先裁 archived，再裁 checked_out
transactions 裁最旧流水
scan_log 裁最旧扫码记录
```

## 撤销

第一版支持撤销上一笔写操作。

```text
每次写操作保存 before/after
撤销时恢复 before
撤销本身写 transaction=undo
```

只保证本次运行期最近一笔可撤销；重启后不强制保留撤销能力。

## 后续数据库迁移

流程跑通后再上 SQLite/Room。

迁移方式：

```text
读取 inventory_snapshot.json
逐条导入 items
逐条导入 transactions
保留 snapshot 备份
```

## 同步

WebDAV 或其他同步放到数据库/导出能力稳定后再做。

同步不进入日常扫码主链路：

```text
扫码 -> 写本地 -> 完成
后台或手动同步 -> 合并/上传
```
