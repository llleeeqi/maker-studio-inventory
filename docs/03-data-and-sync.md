# 本地数据和同步

## 当前结论

下一版直接起 SQLite/Room 数据库，不再继续扩展 JSON snapshot。

第一版数据库先保持轻量：

```text
items
locations
transactions
scan_logs
```

二维码是固定信息来源，数据库里的 `items` 是查库存和保存当前状态的依据。不保存纯标签草稿；只有入库、出库、盘点、移库、归档等写操作会改变库存事实。

## items 表

`items` 同时保存标签固定字段副本和当前变量。

公共字段：

```text
id
type                  spool / part / other
status                in_stock / checked_out / archived
location_id
label_created_on
note
stocked_at
updated_at
checked_out_at
archived_at
```

`location_id` 是物品当前所在位置，属于库存变量，存在 `items` 表里，不进入物品二维码。

库位中文名称不直接重复塞进每条 item，统一通过 `locations` 字典表映射。

## locations 表

`locations` 是轻量名称映射表，不是库存事实表。

用途：

```text
location_id -> 中文名称
```

建议字段：

```text
location_id
name                  中文名称，可以很长
note
updated_at
```

库位二维码只用于识别“这个库位是谁”。建议二维码尽量短：

```text
type=location
id=LOC-A-01
```

如果库位二维码里带了 `name`，App 可用它创建或更新 `locations` 映射；如果没带，扫到未知 `location_id` 时弹窗让用户填写名称。

扫库位码并确认后，App 把库位 ID 写入对应 item 的 `location_id`。显示库存时用 `items.location_id` 查 `locations.name`，查不到时显示 ID。

耗材 `spool` 固定字段：

```text
brand
material
color
tare_g                空盘/皮重，核心计算参数
```

耗材变量字段：

```text
current_g             当前毛重
```

不存 `usable_g`，查询时用 `current_g - tare_g` 实时计算。

零件 `part` 固定字段：

```text
name
unit_weight_g         单件重量，核心计算参数
```

零件变量字段：

```text
current_g             当前总重量/称重值
current_qty           当前估算或确认数量
```

零件 `current_qty` 存数据库。它通常由 `current_g / unit_weight_g` 计算，但后续可能需要人工校正。

其他 `other` 固定字段：

```text
name
```

其他第一版不保存重量和数量变量；后续如某类其他物品需要数量，再扩展。

## transactions 表

`transactions` 是库存事实变化流水。

写入动作：

```text
stock_in
checkout
stocktake
move
archive
undo
edit_fixed
```

建议字段：

```text
tx_id
action
item_id
item_type
before_json
after_json
created_at
```

扫码但未确认，不进入 `transactions`。

## scan_logs 表

`scan_logs` 是扫码输入事件日志，不是系统运行日志。

用途：

```text
判断相机是否识别到了二维码
判断 payload 是否解析成功
判断是否因为缺字段、冲突、取消确认而没有写库存
现场回看最近扫过什么
```

建议字段：

```text
scan_id
raw_payload
parsed_type           spool / part / other / location / weight / unknown
parsed_id             物品 ID 或库位 ID，可为空
result                accepted / rejected / ignored / conflict / cancelled
message               当时 App 展示的提示
created_at
```

扫码但没点确认，不进入 `transactions`，但可以进入 `scan_logs`。

## 容量策略

第一版总量控制在约 1000 条：

```text
items: 600
locations: 100
transactions: 250
scan_logs: 50
```

裁剪规则：

```text
in_stock 不自动删
items 超过上限时优先裁 archived，再裁 checked_out
locations 不主动裁剪，除非用户后续明确删除库位映射
transactions 裁最旧流水
scan_logs 裁最旧扫码记录
```

## 撤销

第一版支持撤销上一笔写操作。

```text
每次写操作保存 before/after
撤销时恢复 before
撤销本身写 transaction=undo
```

只保证本次运行期最近一笔可撤销；重启后不强制保留撤销能力。

## JSON 迁移

现有测试版如已有 `inventory_snapshot.json`，数据库上线时可以作为一次性迁移源：

```text
读取 inventory_snapshot.json
逐条导入 items
逐条导入 transactions
scan_log 导入 scan_logs 或丢弃
保留 snapshot 备份
```

## 同步

WebDAV 或其他同步放到数据库/导出能力稳定后再做。

同步不进入日常扫码主链路：

```text
扫码 -> 写本地 -> 完成
后台或手动同步 -> 合并/上传
```
