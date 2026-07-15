# 原生 Android App

这是个人工作室耗材与五金库存管理项目的手机端主线。

App 使用 Kotlin、Jetpack Compose、CameraX、ML Kit 和 Android SQLite 实现，主要负责二维码扫描、库存操作、标签生成、打印机连接以及本地优先的 WebDAV 同步。

当前支持 Android 12 及以上版本。测试安装包和使用介绍见仓库根目录的 [README](../README.md)。

开发记录：

- [Android 构建与设备支持](../docs/17-android-build-and-device-support.md)
- [当前进度](../docs/14-current-progress.md)
- [原生 Android 总计划](../docs/13-native-android-v1-plan.md)
- [扫码交互重写边界](./INTERACTION_REWRITE.md)
- [人工验收矩阵](./MANUAL_TEST_MATRIX.md)
