# 二维码输入流程

## 输入协议

```text
weight:712.4
spool:PLA-BLK-001
part:M3-INSERT
location:RACK-A01
```

当前解析规则在 `core/inventory.js` 的 `parsePayload()`。

当前扫码状态机在 `core/workflow.js`。它只关心三件事：

- 当前模式：`lookup` / `stocktake` / `move`
- 待处理输入：重量、物品、库位
- 扫码 payload 字符串

页面入口、测试按钮、未来 Android 原生扫码都应该把字符串交给同一个工作流。

## 扫码模块边界

核心不绑定任何扫码库。

正确边界是：

```text
任意扫码模块 / 扫码枪 / Android 原生 / 网页测试按钮
  ↓
得到字符串或扫码结果对象
  ↓
core/scanner-port.js 归一化
  ↓
core/workflow.js 处理 payload
  ↓
core/inventory.js 更新库存
```

`core/scanner-port.js` 支持这些输入：

```js
"spool:PLA-BLK-001"
{ payload: "spool:PLA-BLK-001" }
{ text: "spool:PLA-BLK-001" }
{ rawValue: "spool:PLA-BLK-001" }
{ raw: "spool:PLA-BLK-001" }
{ value: "spool:PLA-BLK-001" }
{ data: "spool:PLA-BLK-001" }
```

后续如果换扫码库，只需要适配它输出的字段，不改库存核心。

## 正式 app 的三个扫码上下文

### 查库存

目的：最快知道一个物品还剩多少、在哪里。

流程：

```text
选择「查库存」
扫 spool: 或 part:
显示库存、低库存状态、库位
```

不写数据，不产生库存修改。

### 盘点称重

目的：称完后快速写入库存。

流程：

```text
选择「盘点称重」
扫 weight:
扫 spool: 或 part:
自动计算并写入库存流水
```

顺序可以反过来：

```text
选择「盘点称重」
扫物品码
扫重量码
自动计算并写入库存流水
```

耗材卷计算：

```text
可用重量 = 当前毛重 - 空卷重量
```

零件计算：

```text
估算数量 = floor((当前毛重 - 容器重量) / 单件重量)
```

### 绑定库位

目的：移动物品后快速更新位置。

流程：

```text
选择「绑定库位」
扫 location:
扫 spool: 或 part:
写入物品库位并记录流水
```

顺序也可以反过来。

## 扫码速度判断

当前网页检测版是手动输入模拟扫码。未来 Android APK 里不要长期依赖网页 JS 解码摄像头画面。

推荐做法：

- APK 壳使用 Android 原生扫码插件。
- 原生层识别出字符串后，把 `weight:...` / `spool:...` 交给 Web Core。
- Web Core 只处理字符串和库存逻辑，不负责摄像头解码。

这样速度瓶颈在原生扫码库，不在浏览器 JS。

## 外挂扫码回调入口

当前正式页面在 `app/app.js` 里暴露了：

```js
window.StudioInventoryScanner.push("spool:PLA-BLK-001");
window.StudioInventoryScanner.push({ rawValue: "weight:712.4" });
```

后续 Capacitor 原生插件、网页扫码库、USB 扫码枪桥接脚本拿到结果后，调用 `push(result)` 即可。这样任何扫码来源都不会绕开网页检测版已经验证过的库存流程。

为了兼容早期测试入口，也保留：

```js
window.StudioInventory.handleScanPayload("spool:PLA-BLK-001");
```
