# 原生 Android app

这是当前手机端主线。

```text
applicationId: studio.inventory.android
versionName: 0.3.1
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
studio-inventory-native-0.3.1-debug.apk
```

## 当前功能

- 四个入口：扫码 / 库存 / 新增 / 流水。
- 页面内嵌 CameraX 相机预览。
- ML Kit 只识别二维码。
- 只认 `msi:v1`。
- 新增页生成固定标签 payload，打印先占位。
- 扫码页处理物品、重量、库位上下文。
- 入库、出库、盘点、移库、归档、撤销上一笔。
- 库位整理模式。
- 本地 JSON 保存到 app 私有文件目录。
