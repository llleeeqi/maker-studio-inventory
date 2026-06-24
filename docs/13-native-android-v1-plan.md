# 原生 Android v1 计划

## 当前结论

手机端主线切换为原生 Android：

```text
mobile_android/
```

旧的 Web/Capacitor 和 Flutter 手机路线不再作为继续开发方向。已有代码和 APK 可作为历史参考，但新功能优先落在原生 Android app。

当前工程已创建，debug APK 已构建：

```text
studio-inventory-native-0.3.7-debug.apk
```

## 技术边界

第一版目标是先跑通 Android 手机上的扫码库存闭环，避免为了跨端能力增加实现层数。

```text
Kotlin
Jetpack Compose
CameraX PreviewView
ML Kit Barcode Scanning
Android SQLite 本地数据库
```

Android 配置：

```text
packageName/applicationId: studio.inventory.android
minSdk: 31
targetSdk: 36
compileSdk: 36
签名: 第一版先用 debug 签名
```

扫码只识别二维码，不做条码、OCR 或图片识别。

## 第一版导航

保留四个底部导航：

```text
扫码 / 库存 / 新增 / 流水
```

职责：

| 页面 | 职责 |
|---|---|
| 扫码 | 内嵌相机扫码，处理入库、更新库存、绑定库位和库位整理 |
| 库存 | 搜索和查看本地库存记录，默认显示在库 |
| 新增 | 生成不含变量的 `v1;` 标签并打印 40x30mm 标签 |
| 流水 | 查看出入库和重量/数量更新，支持撤销上一笔主流水 |

## 二维码协议

第一版只认 `v1;`，不兼容旧短码和 `msi:v1;`。

支持类型：

```text
type=spool
type=part
type=other
type=location
type=weight
```

二维码只存固定信息，不存当前变量。

不进物品二维码的变量：

```text
当前重量
当前数量
当前库位
在库/出库/归档状态
入库日期
出库日期
盘点日期
流水记录
```

## 标签和库存的关系

标签本身就是固定档案来源，但本地库存文件不保存“纯建档记录”。

```text
扫物品码
  -> 解析并展示标签固定信息
  -> 不写本地数据库

点入库
  -> 写入本地 item 记录
  -> item 内保存标签固定字段副本
  -> 同时保存当前变量
  -> stock_in 写主流水
```

这样后续查库存不依赖再次扫描实体标签；本地记录里会保留查询所需的固定字段。

如果扫到同一个 `id`，但二维码固定信息和本地记录不一致：

```text
提示冲突
用户自行选择保留本地记录或更新本地固定信息
不自动覆盖
```

## 必填字段

通用：

```text
type
id
```

耗材 `spool`：

```text
id
brand
material
color
tare_g
```

耗材 `name` 自动生成：

```text
brand + material + color
```

耗材 ID 默认自动生成，允许手改：

```text
FIL-YYMMDD-###
```

零件 `part`：

```text
id
name
unit_weight_g
```

零件主变量是数量。`unit_weight_g` 是核心计算参数，标签里有值就预填；缺失时扫码弹窗必须补齐，才能用重量换算数量。

其他 `other`：

```text
id
name
```

库位 `location`：

```text
id
name
```

重量 `weight`：

```text
value_g
```

非耗材默认 ID：

```text
PART-YYMMDD-###
ITEM-YYMMDD-###
LOC-YYMMDD-###
```

## 新增页

新增页是标签生成器，不是入库入口。

```text
填写固定信息
生成 v1 payload
搜索/连接德佟蓝牙打印机
打印 40x30mm 标签
```

不做：

```text
保存纯档案
保存并入库
二维码图片导出
```

如果标签缺必填字段，扫码页不允许入库；应回到新增页重新生成标签并换新标签。

## 入库规则

入库顺序自由，但未完成上下文只能各保留一个：

```text
pending_item
pending_weight 或 pending_qty
pending_location
```

允许顺序：

```text
物品 -> 重量/数量 -> 库位 -> 入库
重量/数量 -> 库位 -> 物品 -> 入库
库位 -> 物品 -> 重量/数量 -> 入库
```

冲突处理：

```text
未完成入库时又扫到第二个物品/重量/库位
  -> 默认不替换
  -> 必须确认后才替换并清理冲突上下文
```

耗材入库必须齐全：

```text
完整 spool 物品码
当前毛重 current_g
库位码
current_g > tare_g
```

可用重量自动计算：

```text
usable_g = current_g - tare_g
```

零件入库必须齐全：

```text
完整 part 物品码
库位码
数量 > 0
```

数量来源：

```text
总重量 / unit_weight_g 向下取整
```

所有普通写操作都要按钮确认：

```text
入库
出库
盘点
绑定库位
归档
```

这些动作是否需要确认，和是否进入 `transactions` 是两件事。主流水只记录 `stock_in / checkout / stocktake / undo`。绑定库位、库位整理、归档和固定字段修改直接更新 `items`，不进入主流水。

## 库位规则

库位是一个可以放东西的位置，可以是货架一层、箱子、抽屉、托盘或任意容器。

入库必须扫库位码。

单独扫库位码时：

```text
提示是否整理该库位
```

进入整理库位模式后：

```text
连续扫物品码
每扫一个已在本地库存里的物品
  -> 自动更新到当前库位
  -> 更新 items.location_id
  -> 可写 scan_logs 作为整理成功记录
  -> 震动/声音提示
```

整理模式是第一版唯一允许“扫码后自动写入”的模式。进入整理模式前需要确认一次。

整理模式扫到本地不存在的物品：

```text
提示未入库，不能整理到库位
不写本地数据库
```

## 出库和重新入库

出库不是删除。

```text
扫在库物品
  -> 显示当前库存
  -> 点出库
  -> status = checked_out
  -> 写 checkout 流水
```

已出库物品再次扫码：

```text
显示已出库
提示补充重量/数量和库位
点入库后重新入库
```

## 数据存储

0.3.7 已直接使用 Android 内置 SQLite 数据库，不继续扩展 JSON snapshot。

表：

```text
items
locations
transactions
scan_logs
```

现有 0.3.x 测试版 JSON snapshot 只作为一次性迁移源。

`items` 记录已入库或曾经入库/出库/归档过的物品，不保存纯标签草稿。`locations` 保存 `location_id -> 中文名称` 映射，物品当前位置存在 `items.location_id`。

容量软上限：

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

## 归档和撤销

第一版做归档，不做普通永久删除入口。

```text
归档:
  status = archived
  默认库存页不显示
  可通过筛选查看
  超过上限时优先被裁剪
```

第一版支持撤销上一笔写操作。

支持撤销：

```text
入库
出库
盘点更新重量/数量
```

规则：

```text
只保留 lastUndo
app 重启后不保证还能撤销
撤销也写 transaction=undo
```

库位绑定、库位整理、归档和固定字段修改不进入主流水，第一版不纳入撤销主链路。
