# Android 构建与设备支持记录

更新时间：2026-07-15

本文保存 Android 工程的构建参数、安装包输出、标签打印机兼容边界和设备验收状态。项目介绍见仓库根目录 `README.md`，版本功能进度见 `14-current-progress.md`。

## 工程配置

```text
applicationId: studio.inventory.android
versionName: 0.5.2
minSdk: 31
targetSdk: 36
compileSdk: 36
```

主要技术栈：

```text
Kotlin
Jetpack Compose
CameraX
ML Kit Barcode Scanning
Android SQLite
Gson JSON migration/export format
```

## 构建命令

```bash
cd mobile_android
./gradlew testDebugUnitTest assembleDebug
./gradlew clean assembleRelease
./gradlew clean assembleRelease -PtargetAbi=arm64-v8a
```

Gradle 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

仓库根目录发布包：

```text
studio-inventory-native-0.5.2-universal.apk
studio-inventory-native-0.5.2-arm64.apk
```

Release 构建开启 R8 和资源压缩。通用 APK 保留全部 ABI，arm64 APK 用于常见 64 位实体 Android 手机。

## 标签打印机接口

当前 Android 工程集成德佟官方打印 SDK：

```text
SDK 文件：LPAPI-2026-01-08-R.jar
接口包：com.dothantech.lpapi.LPAPI
连接方式：Android 蓝牙
标签模板：40 x 30 mm
设计分辨率：200/203 DPI
```

当前实现包括打印机搜索、停止搜索、20 秒搜索超时、手动连接、上次设备记录和启动自动重连。

## 品牌和型号边界

| 品牌或范围 | 当前结论 |
|---|---|
| 德佟 DeTong / DothanTech 的 LPAPI 标签机 | 已接入；App 内搜索和连接已验证 |
| 德佟 DP23、DP30 系列 | 官方手册明确标注 LPAPI，列为优先真机打印验证型号 |
| 德佟其他型号 | 必须逐型号确认支持 Android LPAPI，不能只根据品牌判断 |
| 其他品牌蓝牙标签机 | 当前不保证支持 |
| ESC/POS 小票机、CPCL 打印机 | 当前没有接入对应打印协议 |

系统蓝牙能够发现或配对设备，不代表 LPAPI 能够向它提交打印任务。购买或接入新机型时，应向厂商确认设备能否使用德佟 Android LPAPI，或者确认其 SDK 是否提供 `com.dothantech.lpapi.LPAPI`。

官方资料：

- [德佟 Android SDK](https://detonger.com/#/sdk/detail?sdkID=16BC0F46-ACC4-4EBB-9340-47328936E779)
- [德佟产品中心](https://www.detonger.com/)
- [DP23/DP30 官方手册](https://en.detonger.com/userManual/User%27s%20Manual%20for%20DP23%2C%20DP30%20Thermal%20Label%20Printer.pdf)

## 当前验收状态

| 项目 | 状态 |
|---|---|
| Android 蓝牙搜索和连接 | 已验证 |
| 启动自动连接上次打印机 | 已实现 |
| App 内真实二维码标签预览 | MuMu 已验证 |
| 按屏幕 DPI 模拟 40 x 30 mm 标签尺寸 | MuMu 已验证，实体手机报告值待复核 |
| 40 x 30 mm 实体出纸排版 | 待标签机实测 |
| 实体标签二维码可扫性 | 待标签机实测 |
| 打印浓度、走纸和边距 | 待标签机实测 |

设备验收不能只检查“连接成功”。最终需要用实际标签纸确认文本边距、二维码尺寸、纠错冗余、打印浓度、走纸偏移和手机扫码成功率。
