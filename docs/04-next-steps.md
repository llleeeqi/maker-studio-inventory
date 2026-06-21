# 下一阶段开发清单

## 当前状态

原生 Android 工程已创建：

```text
mobile_android/
```

已接入：

- Compose 四页：扫码 / 库存 / 新增 / 流水。
- CameraX + ML Kit 页面内二维码扫码。
- `msi:v1` parser 和 payload builder。
- JSON 本地保存。
- 入库、出库、盘点、移库、归档、撤销上一笔。
- 库位整理模式。

当前 debug APK：

```text
studio-inventory-native-0.3.3-debug.apk
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
JSON snapshot
```

## 下一步

1. 真机安装 `studio-inventory-native-0.3.3-debug.apk`。
2. 测新增页生成 payload、扫码页识别、入库、出库、库位整理。
3. 修真机相机预览、识别速度和 UI 卡点。
4. 增加 JSON 导出/导入入口。
5. 流程稳定后再迁 SQLite/Room。

## 已完成的初版实现项

1. 创建 `mobile_android/` 原生 Android 工程。
2. 建立四个底部导航：扫码 / 库存 / 新增 / 流水。
3. 实现 `msi:v1` parser 和 payload builder。
4. 实现本地 JSON store：`items / transactions / scan_log`。
5. 实现新增页标签生成器：自动 ID、复制 payload、打印占位。
6. 实现扫码页内嵌 CameraX 预览和 ML Kit 二维码识别。
7. 实现入库上下文：物品、重量/数量、库位，冲突时默认不替换。
8. 实现入库、出库、盘点、移库、归档和撤销上一笔。
9. 实现库位整理模式。
10. 实现库存搜索、状态筛选、库位筛选。
11. 实现流水页和最近扫码记录。
12. 构建 debug APK。

## 暂不做

```text
SQLite/Room
WebDAV 同步
正式签名
蓝牙打印
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
扫重量码或手动输重量
扫库位码
点入库
库存页能查到
扫物品后能出库
扫库位进入整理模式
连续扫已有库存物品自动更新库位
撤销上一笔写操作
```
