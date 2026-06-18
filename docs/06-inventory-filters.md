# 库存筛选

## 当前结论

库存页只展示本地 `items` 中已经入库或曾经入库/出库/归档过的物品。

纯标签草稿不进入库存页。

## 默认视图

默认只显示：

```text
status = in_stock
```

这样已出库和归档记录不会干扰日常找东西。

## 筛选项

第一版支持：

```text
搜索: id / name / brand / material / color / category / spec / location
类型: 全部 / 耗材 / 零件 / 其他
状态: 在库 / 已出库 / 已归档 / 全部
库位: 按 location_id 或 location_name 查找
低库存: 耗材可用重量低、零件数量低
```

低库存阈值第一版可以先写死：

```text
耗材 usable_g < 100
零件 current_qty < 10
```

## 耗材统计

耗材余量：

```text
usable_g = current_g - tare_g
```

同类统计：

```text
严格同色: brand + material + color
宽松找色: material + color
```

## 零件统计

零件按数量为主：

```text
current_qty
```

同类统计：

```text
category + spec + color
```

`color` 为空时按 `category + spec` 统计。
