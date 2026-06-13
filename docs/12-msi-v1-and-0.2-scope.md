# msi:v1 协议与 0.2 范围

## 结论

这个项目近期不是通用 ERP，也不是二维码生成器，而是 **扫码驱动的个人工作室库存系统**。

0.2 只做手机本地闭环：

```text
手机扫码 -> 识别固定档案 -> 入库 / 盘点 / 出库 / 移库 -> 本地保存 -> 导出备份
```

Docker 服务端、PWA、WebDAV 自动同步、自然语言助手、标签打印 SDK 都放到中期，不阻塞 0.2。

## 设备定位

近期主设备是 Android 手机 app：

```text
Flutter / Dart / Material 3
```

手机负责：

- 高频扫码。
- 入库、盘点、出库、移库。
- 本地保存。
- 导出/导入 JSON 快照。

电脑 Web 负责：

- 测试二维码。
- 后续标签打印验证。
- 后续批量管理。

中期再做：

- WebDAV 自动同步。
- Docker 服务端。
- PWA 渐进式客户端。
- 自然语言助手。

## 二维码长期协议

二维码内容使用长期可读协议：

```text
msi:v1;key=value;key=value
```

规则：

- 优先可读，不做极限压缩。
- 二维码存固定档案，不存当前库存变量。
- 字段名稳定，后续只追加，不破坏旧字段含义。
- 继续兼容旧短码 `spool:<id>`、`part:<id>`、`location:<id>`、`weight:<g>`，但长期标签推荐 `msi:v1`。

重量统一使用克，字段名保留 `_g`：

```text
full_g=1200
tare_g=200
net_g=1000
unit_weight_g=0.42
value_g=712.4
```

业务日期统一使用六位数字：

```text
YYMMDD
```

例如：

```text
created_on=260613
stocked_on=260613
```

`created_on` 是固定档案日期，表示首次建档或首次打印二维码标签的日期。默认今天，可手动改，用于旧库存录入和本地数据丢失后的恢复参考。

## 二维码不存的字段

以下都是变量，不进实体二维码：

- 当前重量。
- 当前数量。
- 当前库位。
- 当前在库/出库状态。
- 入库日期。
- 出库日期。
- 最近盘点日期。
- 流水记录。

库位是变量。所有类型都允许无库位入库，后续可以扫码绑定库位。

## 实体类型

### 耗材卷 spool

推荐 payload：

```text
msi:v1;type=spool;id=PLA-BLK-001;name=黑色PLA;brand=Bambu;material=PLA;color=black;full_g=1200;tare_g=200;net_g=1000;created_on=260613
```

字段：

| 字段 | 含义 |
|---|---|
| `type=spool` | 耗材卷 |
| `id` | 这一卷的唯一实体 ID |
| `name` | 人眼显示名 |
| `brand` | 品牌 |
| `material` | 材料 |
| `color` | 颜色 |
| `full_g` | 满卷含盘总重量 |
| `tare_g` | 空盘重量 |
| `net_g` | 标称净重 |
| `created_on` | 建档/贴标日期，YYMMDD |

耗材同类统计先按：

```text
brand + material + color
```

`family` 不进 v1。现在不增加重复分组字段，避免二维码变长和长期维护出错。

### 零件 part

推荐 payload：

```text
msi:v1;type=part;id=M3-SCREW-8-BLK;name=M3x8黑色圆头螺丝;category=screw;spec=M3x8;color=black;unit_weight_g=0.42;package_qty=100;created_on=260613
```

字段：

| 字段 | 含义 |
|---|---|
| `type=part` | 五金/零件 |
| `id` | 这一盒/这一包/这一类零件的 ID |
| `name` | 人眼显示名 |
| `category` | 类别，例如 screw、insert、bearing |
| `spec` | 规格，例如 M3x8 |
| `color` | 颜色或表面处理，按实际需要填 |
| `unit_weight_g` | 单颗重量 |
| `package_qty` | 包装数量或初始数量 |
| `created_on` | 建档/贴标日期，YYMMDD |

零件同类统计先按：

```text
category + spec + color
```

如果后续需要更细，再追加材料或表面处理字段。

### 其他 other

推荐 payload：

```text
msi:v1;type=other;id=TOOL-001;name=热风枪;note=喷嘴套装;created_on=260613
```

字段：

| 字段 | 含义 |
|---|---|
| `type=other` | 其他实体 |
| `id` | 唯一实体 ID |
| `name` | 物品名称 |
| `note` | 短备注 |
| `created_on` | 建档/贴标日期，YYMMDD |

`other` 只做简单登记、查找、库位和状态流转，不做重量/数量余量计算。

备注限制：

```text
note 最多 20 个中文字符或 40 个 ASCII 字符
```

长备注以后放本地数据，不放二维码。

### 库位 location

库位标签本身是固定档案：

```text
msi:v1;type=location;id=RACK-A01;name=A架01格;created_on=260613
```

库位码用于绑定物品状态，不表示物品二维码里的固定字段。

### 重量 weight

重量码用于称重输入：

```text
msi:v1;type=weight;value_g=712.4
```

短码继续兼容：

```text
weight:712.4
```

## 数据模型

逻辑上拆三层：

```text
ItemProfile 固定档案
ItemState   当前库存状态
Transaction 流水
```

### ItemProfile

固定档案来自二维码或新增表单，变化很少。

```text
id
type
name
brand
material
color
full_g
tare_g
net_g
category
spec
unit_weight_g
package_qty
note
created_on
profile_updated_on
profile_updated_at
```

### ItemState

只有真正入库后才创建状态。没有 State 的 Profile 不计入库存。

```text
item_id
status=in_stock|checked_out|archived
location_id
stocked_on
checked_out_on
counted_on
state_updated_on
state_updated_at
```

各类型变量：

```text
spool: current_g
part: current_qty 或 current_g
other: 无余量字段
```

入库必填：

| 类型 | 入库条件 |
|---|---|
| `spool` | 必须有当前重量 `current_g` |
| `part` | 必须有数量 `current_qty` 或总重量 `current_g` |
| `other` | 只需确认入库 |

库位对所有类型都是可选变量，不阻塞入库。

### Transaction

近期以 `states` 快照为主，流水用于记录关键动作和后续同步排查。

关键动作必须写流水：

- 建档。
- 入库。
- 盘点。
- 移库。
- 出库。
- 重新入库。
- 归档/恢复。
- 编辑固定档案。

流水必须有精确时间和设备信息：

```text
tx_id
device_id
action
item_id
created_on=260613
created_at=2026-06-13T22:32:08+08:00
timezone=Asia/Shanghai
before
after
```

`created_at` 必须带时区 offset。业务排序按天，冲突排查和同步排序可以把 `created_at` 转成 UTC。

## 入库与恢复规则

扫到未知 `msi:v1` 标签：

```text
解析固定档案
  -> 保存 ItemProfile
  -> 不创建 ItemState
  -> 不计入库存
  -> 提示完成入库流程
```

不要设置 `unconfirmed` 状态。

判断规则：

```text
有 Profile，无 State = 已建档，未入库
有 Profile，有 State 且 in_stock = 在库
有 Profile，有 State 且 checked_out = 已出库
有 Profile，有 State 且 archived = 已归档
```

真正入库：

```text
满足该类型入库条件
  -> 创建或更新 ItemState
  -> 写 stock_in 流水
```

出库不删除档案，不改二维码：

```text
status=checked_out
checked_out_on=当天
写 checkout 流水
```

重新入库：

```text
status=checked_out -> stock_in
stocked_on=当天或用户自定义
checked_out_on 清空
写 stock_in 流水
```

## 日期排序

业务排序只按天。

FIFO：

```text
stocked_on ASC
created_on ASC
id ASC
```

说明：

- `stocked_on` 是本地状态里的入库日期，默认今天，可手动改。
- `created_on` 是二维码里的建档/贴标日期，默认今天，可手动改。
- 旧库存录入时，可以手动填旧日期。
- 重建库存时，如果只有标签，至少可用 `created_on` 做恢复参考。

## 快照格式方向

0.2 先用 JSON 快照持久化，不急着上 SQLite。

建议结构：

```json
{
  "schema": 2,
  "device_id": "phone-a",
  "profiles": {},
  "states": {},
  "transactions": []
}
```

JSON 同时作为：

- 本地持久化格式。
- 导出备份格式。
- 导入恢复格式。
- 未来 WebDAV 同步基础格式。

Flutter 0.2 可以独立实现 Dart 逻辑，不强行复用现有 JS `core/`。硬约束是：

- `msi:v1` payload 一致。
- JSON snapshot 一致。
- 字段语义一致。
- 扫码状态机结果一致。
- Web 工具生成的二维码，Flutter 必须能解析。
