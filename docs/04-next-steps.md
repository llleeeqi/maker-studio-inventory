# 下一阶段开发清单

## 当前状态

原生 Android 工程已创建：

```text
mobile_android/
```

已接入：

- Compose 四页：扫码 / 库存 / 新增 / 流水。
- CameraX + ML Kit 页面内二维码扫码。
- `v1;` parser 和 payload builder。
- Android SQLite 四表本地保存。
- 入库、出库、盘点、绑定库位、归档、撤销上一笔。
- 库位整理模式。
- 德佟标签打印机搜索、连接和打印骨架。
- tools 虚拟货架测试台。

用户真机反馈:

- 扫码功能正常。
- tools 虚拟货架里的耗材、重量、库位扫码链路已可用。
- 连接打标机功能正常。

当前 debug APK：

```text
studio-inventory-native-0.3.7-debug.apk
```

## 构建配置

搭建原生 Android v1：

```text
mobile_android/
applicationId = studio.inventory.android
minSdk = 31
compileSdk = 36
targetSdk = 36
```

技术栈：

```text
Kotlin
Jetpack Compose
CameraX
ML Kit Barcode Scanning
当前 0.3.7: Android SQLite 四表
旧 0.3.x JSON: 仅作为自动迁移源
```

## 下一步

1. 梳理并确认手机 App 页面按钮、可点击条件和点击后流程。
2. 按确认后的操作逻辑调整扫码页、库存页、新增页和流水页。
3. 补扫码后 App 内确认弹窗的可编辑字段。
4. 增加导出/导入入口。
5. 打印机到货/可测时，验证 40x30mm 纸面打印效果。

## 已完成的初版实现项

1. 创建 `mobile_android/` 原生 Android 工程。
2. 建立四个底部导航：扫码 / 库存 / 新增 / 流水。
3. 实现 `v1` parser 和 payload builder。
4. 实现本地 SQLite store：`items / locations / transactions / scan_logs`。
5. 实现新增页标签生成器：自动 ID、德佟 LPAPI 打印骨架。
6. 实现扫码页内嵌 CameraX 预览和 ML Kit 二维码识别。
7. 实现入库上下文：物品、重量/数量、库位，冲突时默认不替换。
8. 实现入库、出库、盘点、绑定库位、归档和撤销上一笔。
9. 实现库位整理模式。
10. 实现库存搜索、状态筛选、库位筛选。
11. 实现流水页和最近扫码记录。
12. 构建 debug APK。

## 暂不做

```text
WebDAV 同步
正式签名
最终纸面打印效果验收
Docker 服务端
PWA
自然语言助手
多设备协作
```

## 验收重点

第一版能完成：

```text
生成耗材标签
扫耗材码
扫重量码
扫库位码
点入库
库存页能查到
扫物品后能出库
扫库位进入整理模式
连续扫已有库存物品自动更新库位
撤销上一笔写操作
```
