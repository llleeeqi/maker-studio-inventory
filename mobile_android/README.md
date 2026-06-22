# 原生 Android app

这是当前手机端主线。

```text
applicationId: studio.inventory.android
versionName: 0.3.6
minSdk: 31
targetSdk: 36
compileSdk: 36
```

技术栈：

```text
Kotlin
Jetpack Compose
CameraX
ML Kit Barcode Scanning
Gson JSON snapshot
```

## 构建

```bash
cd mobile_android
./gradlew assembleDebug
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

仓库根目录也会保留一份便于安装测试：

```text
studio-inventory-native-0.3.6-debug.apk
```

## 当前功能

- 四个入口：扫码 / 库存 / 新增 / 流水。
- 页面内嵌 CameraX 相机预览。
- 相机空闲 20 秒自动暂停，减少耗电。
- ML Kit 只识别二维码。
- 只认 `v1;`。
- 中文字段值写入二维码时会百分号编码，扫码后自动解码。
- 新增页生成固定标签并接入德佟 LPAPI 打印骨架。
- 生成标签后展示 40x30mm 标签预览。
- 打印机搜索可手动停止，20 秒后也会自动停止。
- 可开启启动自动连接打标机，优先连接上次打印机。
- 40x30mm 标签模板：左侧三行文字，右侧 Q 级纠错二维码。
- 扫码页处理物品、重量、库位上下文。
- 入库、出库、盘点、移库、归档、撤销上一笔。
- 库位整理模式。
- 本地 JSON 保存到 app 私有文件目录。
