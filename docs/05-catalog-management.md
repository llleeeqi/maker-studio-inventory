# 标签生成和字段规则

## 当前结论

新增页是标签生成器，不是建档页。

```text
填写固定信息
生成 v1 标签
打印 40x30mm 标签
```

新增页不写本地库存记录。真正写入发生在扫码页点击入库之后。

## 自动 ID

默认自动生成 ID，允许手改。

```text
耗材: FIL-YYMMDD-###
零件: PART-YYMMDD-###
其他: ITEM-YYMMDD-###
库位: LOC-YYMMDD-###
```

每张实体标签唯一 ID。

## 耗材 spool

必填：

```text
type=spool
id
brand
material
color
tare_g
```

`name` 自动生成：

```text
brand + material + color
```

可选：

```text
created_on
note
```

同类统计：

```text
严格同色: brand + material + color
宽松找色: material + color
```

## 零件 part

必填：

```text
type=part
id
name
unit_weight_g
```

可选：

```text
created_on
note
```

零件主变量是数量。`unit_weight_g` 只用于用总重量换算数量。

## 其他 other

必填：

```text
type=other
id
name
```

## 库位 location

库位是任何可以放东西的位置，可以是货架一层、箱子、抽屉、托盘或其他容器。

必填：

```text
type=location
id
name
```

## 重量 weight

重量码用于扫码录入当前毛重或总重量。

必填：

```text
type=weight
value_g
```

## 40x30mm 打印模板

标签左侧只放三行人眼可读文字，不带字段名前缀：

```text
名称或 material color brand
YYYY-MM-DD
备注
```

二维码在右侧，内容仍是 `v1;key=value`，日期字段用 `YYMMDD`。

## 缺字段处理

标签缺必填字段时：

```text
扫码页拒绝入库
不写本地数据库
提示回新增页重新生成标签并换新标签
```
