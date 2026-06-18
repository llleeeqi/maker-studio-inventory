# msi:v1 协议和本地记录边界

## 结论

第一版只支持 `msi:v1`。

二维码是固定信息载体，本地 JSON 是库存事实载体。

```text
标签 payload 不含变量
本地 item 保存固定字段副本 + 当前变量
```

## 基本格式

```text
msi:v1;key=value;key=value
```

规则：

- 字段名小写。
- 重量统一克，字段名保留 `_g`。
- 第一版不兼容旧短码。
- 字段名后续只追加，不改变旧字段含义。

## 不进物品二维码的字段

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

## 耗材 spool

示例：

```text
msi:v1;type=spool;id=FIL-260617-001;brand=Bambu;material=PLA;color=white;tare_g=200
```

必填：

| 字段 | 含义 |
|---|---|
| `type=spool` | 耗材 |
| `id` | 每张实体标签唯一 ID |
| `brand` | 品牌 |
| `material` | 材料 |
| `color` | 颜色 |
| `tare_g` | 空盘重量 |

可选：

```text
name
full_g
net_g
note
```

如果 `name` 为空，显示名自动用：

```text
brand + material + color
```

## 零件 part

示例：

```text
msi:v1;type=part;id=PART-260617-001;name=M3x8黑色圆头螺丝;category=screw;spec=M3x8;unit_weight_g=0.42
```

必填：

| 字段 | 含义 |
|---|---|
| `type=part` | 零件 |
| `id` | 每张实体标签唯一 ID |
| `name` | 名称 |
| `category` | 类别 |
| `spec` | 规格 |

可选：

```text
color
unit_weight_g
note
```

零件不关心容器是盒子还是包。标签贴在实际容器上，系统只认“这一批复数零件”。

## 其他 other

示例：

```text
msi:v1;type=other;id=ITEM-260617-001;name=热风枪
```

必填：

```text
type=other
id
name
```

## 库位 location

示例：

```text
msi:v1;type=location;id=LOC-260617-001;name=A架第一层
```

库位可以是货架一层、箱子、抽屉、托盘或任意容器。

必填：

```text
type=location
id
name
```

## 重量 weight

示例：

```text
msi:v1;type=weight;value_g=712.4
```

必填：

```text
type=weight
value_g
```

## 本地 item

本地 item 只在入库后创建。

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
    "location_id": "LOC-260617-001"
  }
}
```

没有“只建档未入库”的本地记录。

## 状态

```text
in_stock
checked_out
archived
```

出库不删除 item。归档用于隐藏废弃记录，超出上限时优先裁剪。

## 流水

关键动作写 transaction：

```text
stock_in
checkout
stocktake
move
archive
undo
```

流水保留：

```text
tx_id
action
item_id
created_at
before
after
```
