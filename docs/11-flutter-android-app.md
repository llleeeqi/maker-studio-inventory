# Flutter Android 手机 app

## 当前结论

项目已经新增 Flutter / Dart / Material 3 手机 app：

```text
mobile_flutter/
```

当前可安装 release 包：

```text
studio-inventory-flutter-0.2.2-arm64-release.apk
```

APK 信息：

```text
packageName: studio.inventory.mobile
versionName: 0.2.2
versionCode: 22
minSdk: 24
targetSdk: 36
native-code: arm64-v8a
签名: debug key, v2 signature verified
文件大小: 24M on disk / 24.2MB build output
SHA-256: 9dc285cfaf57abe5caab7b3fc41b53b55dc31226d557622c166144da9ea4df90
```

## App 结构

当前 Flutter app 使用 Material 3 和底部导航：

```text
扫码 / 库存 / 新增 / 流水
```

扫码页：

- 小相机预览框。
- 预览扫码、原生扫码、停止、手电筒按钮。
- 扫码成功后触发震动和系统点击声。
- 手动补录放到底部弹窗，作为测试和相机异常兜底，不占主流程。
- 扫 `spool:` / `part:` / `weight:` / `location:` 后由状态机自动判断动作。

库存页：

- 搜索。
- 类型筛选。
- 低库存筛选。
- 可进入编辑。

新增页：

- 新增耗材卷或零件。
- 支持扫码填 ID。
- 支持从现有物品克隆。

流水页：

- 展示扫码、盘点、移库、出库等写入记录。

## Android 原生边界

手机 app 的方向不是“浏览器网页套壳”，而是：

```text
Flutter Material 3 UI
  ↓
Android 原生构建链路
  ↓
相机扫码 / 手电筒 / 震动 / 声音等平台能力
  ↓
稳定 payload 协议
  ↓
库存状态机
```

当前页面内预览扫码仍使用 `mobile_scanner`。为了避开部分小米机型在 CameraX / ML Kit 初始化时抛空指针，0.2.2 增加了 Android 原生 ZXing 扫码 Activity 作为兜底入口。

## 0.2.2 相机修复

0.2.2 针对小米手机上 0.2.1 授权后仍提示 `Attempt to invoke virtual method ... on a null object reference` 的问题继续处理：

- Manifest 增加 `android.hardware.camera` 和 `android.hardware.camera.autofocus` 的 optional feature 声明。
- 页面内预览扫码只指定后置摄像头，不再强制选择 `normal` 镜头，避免多摄机型镜头选择异常。
- 新增“原生扫码”按钮，通过 Android MethodChannel 打开 ZXing 扫码 Activity，绕开 `mobile_scanner` 的 CameraX / ML Kit 预览链路。
- 主扫码页和新增页扫码弹窗都改为显式启动，不在弹窗创建时自动抢占相机。
- `start / stop / toggleTorch` 统一捕获异常，失败后继续保留手动补录入口。
- app 进入后台、暂停或隐藏时主动停止相机，避免 CameraX 生命周期残留。

## APK 体积说明

不要把 Flutter debug APK 当作分发包。debug 包会带 Dart 调试内核、多架构 native so、调试验证层等内容，本地曾达到约 158M。

当前分发包使用：

```bash
/data/flutter/bin/flutter build apk --release --target-platform android-arm64
```

并在 Android 配置里限制 `arm64-v8a`，同时排除插件残留的 `armeabi-v7a` 和 `x86_64` JNI 库。当前 release 包约 24.1MB，APK 内只剩：

```text
native-code: arm64-v8a
```

## 构建记录

Flutter SDK：

```text
/data/flutter
Flutter 3.44.2 stable
Dart 3.12.2
```

Android SDK：

```text
/root/android-sdk
compileSdk 36
build-tools 36.0.0
```

这台构建机内存较小，默认 Flutter 模板给的 Gradle 参数是 `-Xmx8G`，第一次构建触发 OOM。已在 `mobile_flutter/android/gradle.properties` 降低构建内存和并发：

```text
org.gradle.jvmargs=-Xmx1536m ...
org.gradle.daemon=false
org.gradle.workers.max=1
```

## 验证命令

从 ASCII 路径运行，避免 Flutter analysis server 处理中文路径时出 URI 编码问题：

```bash
mount --bind /root/工作室物品管理 /data/studio_inventory_repo
cd /data/studio_inventory_repo/mobile_flutter
/data/flutter/bin/dart format lib test
/data/flutter/bin/flutter analyze
/data/flutter/bin/flutter test
/data/flutter/bin/flutter build apk --release --target-platform android-arm64
```

APK 校验：

```bash
/root/android-sdk/build-tools/36.0.0/aapt dump badging build/app/outputs/flutter-apk/app-release.apk
/root/android-sdk/build-tools/36.0.0/apksigner verify --verbose build/app/outputs/flutter-apk/app-release.apk
sha256sum build/app/outputs/flutter-apk/app-release.apk
```

当前验证结果：

```text
flutter analyze: No issues found
flutter test: All tests passed
flutter build apk --release --target-platform android-arm64: Built app-release.apk
apksigner: Verified using v2 scheme true
aapt: native-code 'arm64-v8a'
```

## 后续重点

- 把 Flutter 当前内置状态机继续对齐 `docs/10-scan-workbench-redesign.md`。
- 将库存规则从单文件 app 抽出，逐步和 `core/` 的 payload 协议、快照格式对齐。
- 增加出库/重新入库的 Material 3 操作卡。
- 增加真机扫码冒烟记录，验证相机权限、对焦、手电筒、震动和声音。
- 接入正式 release 签名；当前 release 包仍使用 debug key 签名，适合外发测试，不适合上架。
