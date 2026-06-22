# 二维码输入流程

## 输入协议

第一版只认 `v1;`，不兼容旧短码和 `msi:v1;`。

```text
v1;type=spool;id=FIL-260617-001;brand=Bambu;material=PLA;color=white;tare_g=200;created_on=260622;note=备注
v1;type=part;id=PART-260617-001;name=M3x8黑色圆头螺丝;unit_weight_g=0.42;created_on=260622;note=备注
v1;type=other;id=ITEM-260617-001;name=热风枪;created_on=260622;note=备注
v1;type=location;id=LOC-260617-001;name=A架第一层;created_on=260622;note=备注
v1;type=weight;value_g=712.4
```

扫码模块只负责识别二维码字符串；库存逻辑只处理 payload。

## 扫码页上下文

扫码页维护三个待处理上下文：

```text
pending_item
pending_weight 或 pending_qty
pending_location
```

入库顺序自由：

```text
物品 -> 重量/数量 -> 库位 -> 点入库
重量/数量 -> 库位 -> 物品 -> 点入库
库位 -> 物品 -> 重量/数量 -> 点入库
```

但每类上下文只能有一个。未完成入库时扫到第二个物品、重量或库位，默认不替换，必须确认后才替换。

## 入库

扫到物品码时，如果本地没有该 `id`：

```text
只展示标签固定信息
不写本地 JSON
```

点入库后才写本地 `items`，并保存固定字段副本和当前变量。

耗材入库必须满足：

```text
完整 spool 标签
当前毛重 current_g
库位码
current_g > tare_g
```

零件入库必须满足：

```text
完整 part 标签
数量 current_qty > 0
库位码
```

数量通过总重量和 `unit_weight_g` 换算。

## 出库和盘点

扫到已在库物品：

```text
显示库存详情
可点出库
可补重量/数量后点盘点
```

所有普通写操作都需要按钮确认：

```text
入库 / 出库 / 盘点 / 移库 / 归档
```

## 库位整理

单独扫库位码时，提示是否整理该库位。

进入整理模式后：

```text
连续扫已在本地库存里的物品
每扫一个自动更新 location
写 move 流水
震动/声音提示
```

整理模式是第一版唯一允许扫码后自动写入的模式。扫到未入库物品时只提示，不写本地。

## 手动兜底

正常流程不提供手动输入兜底。标签缺必填字段时，应重新生成标签并换新标签。
