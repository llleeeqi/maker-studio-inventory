# 工作室物品管理 Flutter 手机 app

这是项目的 Flutter / Dart / Material 3 Android 手机 app。

当前版本：

```text
0.2.4+24
```

当前可安装 release 包在仓库根目录：

```text
studio-inventory-flutter-0.2.4-arm64-release.apk
```

## 当前功能

- Material 3 四入口：扫码、库存、新增、流水。
- Android 原生 ZXing 扫码作为主扫码入口，避开部分机型的 CameraX / ML Kit 初始化异常。
- `mobile_scanner` 页面内预览扫码保留为实验入口。
- 扫码、预览扫码、停止、手电筒按钮；相机启动异常会保留手动补录入口。
- 扫码后震动和系统点击声提示。
- 手动 payload 补录底部弹窗。
- 本地 demo 数据和 `SharedPreferences` 持久化。

## 构建

建议从 ASCII 路径构建：

```bash
cd /data/studio_inventory_repo/mobile_flutter
/data/flutter/bin/flutter analyze
/data/flutter/bin/flutter test
/data/flutter/bin/flutter build apk --release --target-platform android-arm64
```

输出：

```text
build/app/outputs/flutter-apk/app-release.apk
```
