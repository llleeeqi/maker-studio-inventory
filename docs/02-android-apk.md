# Android APK 路线

## 结论

已经可以做成本地 Android APK。当前有两条 Android 路线：

- `mobile_flutter/`：Flutter / Dart / Material 3 手机 app，当前手机主线。
- `android/`：Capacitor WebView 壳，保留为 Web 核心打包路线和历史对照。

当前 Flutter 可安装 release APK：

```text
studio-inventory-flutter-0.2.1-arm64-release.apk
```

早期 Capacitor debug APK：

```text
studio-inventory-debug.apk
```

这里说的“网页核心”不是让用户打开一个外网网页，而是把 HTML/JS/CSS 打包进 Android App。运行时是本机 app，UI 用 WebView 承载，扫码、文件、蓝牙走 Android 原生插件。

Flutter 路线则不是 WebView 套壳，而是 Flutter Material 3 UI 加 Android 原生构建链路。高频手机交互优先放在 Flutter app 里推进。当前 0.2.1 分发包是 arm64 release APK，debug APK 不作为外发包。

## 为什么用 Capacitor

Capacitor 的路线适合这个项目：

- UI 和业务逻辑是 Web Core，未来能复用到 Docker/网页版。
- Android 壳只负责平台能力：相机扫码、文件存储、BLE 打印。
- 不把库存计算写死在 Android 原生层，减少未来迁移成本。

Android 壳的原则：

- 相机扫码、文件读写、权限、通知、BLE、系统分享等平台能力尽量用 Android 原生或 Capacitor 原生插件完成。
- Android 原生层只把结果转换成稳定 payload、快照文件或状态回调，不复制库存计算、目录规则、筛选规则和合并规则。
- 固定业务逻辑继续放在 `core/`，Android/Web/Docker 调用同一套核心。

官方资料：

- Capacitor 文档：https://capacitorjs.com/docs
- Android 平台文档：https://capacitorjs.com/docs/android
- Google ML Kit Barcode Scanning：https://developers.google.com/ml-kit/vision/barcode-scanning/android

## 扫码会不会快

关键不在“是不是网页 UI”，而在“二维码解码由谁做”。

不推荐：

```text
WebView 摄像头画面 -> JS 每帧解码
```

推荐：

```text
Android 原生相机/ML Kit 解码 -> 得到 payload 字符串 -> Web Core 处理库存
```

后者更接近普通扫码 App 的路线。Web Core 只接收字符串，不做视频帧计算。

## 当前 APK 资源结构

Capacitor 的 `webDir` 是 `www`。构建前会运行：

```bash
npm run build:web
```

这个脚本把以下资源复制到 `www/`：

```text
index.html
app/
core/
```

这样 APK 里同时有正式页面和平台无关核心，`app/app.js` 里的 `../core/...` import 可以正常工作。

## APK 第一阶段集成点

```text
www/index.html -> www/app/index.html
  ↓
Capacitor Android WebView
  ↓
原生扫码插件返回 payload
  ↓
window.StudioInventoryScanner.push(payload)
  ↓
core/scanner-port.js 归一化
  ↓
core/workflow.js 处理扫码上下文
  ↓
core/inventory.js 更新库存/流水
```

实际页面桥接入口是：

```js
window.StudioInventoryScanner.push(payload)
```

这个入口内部会调用 `core/scanner-port.js` 和 `core/workflow.js`，所以 Android 原生扫码、网页手动输入、测试按钮都共享同一套规则。

Android 原生层负责更稳、更快地完成平台动作；库存判断和数据变更仍由核心完成。

## 后续需要新增的模块

建议新增：

```text
app/platform/
├── scanner-web-camera.js    # 如果要在网页里直接开相机扫码
├── scanner-android.js       # APK：调用原生扫码插件
├── storage-web.js           # 当前检测版：localStorage
└── storage-android.js       # APK：SQL.js + Capacitor Filesystem
```

当前还没拆 `platform/`，因为最小版先保持简单。等引入 Capacitor 插件时再拆，避免现在写空壳。

## APK 构建大概步骤

当前项目内重新构建 debug APK：

```bash
npm install
npm run apk:debug
```

输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

为了方便直接拿文件，当前也复制了一份到项目根目录：

```text
studio-inventory-debug.apk
```

## 当前构建环境记录

- JDK：OpenJDK 21
- Android SDK：`/root/android-sdk`
- compileSdk / targetSdk：36
- build tools：36.0.0；Gradle 构建过程中也自动补了 35.0.0
- Gradle wrapper：`android/gradlew`
- APK 签名：debug 签名，`apksigner verify` 已通过 v2 签名校验

## 当前 APK 边界

- Capacitor APK 仍是 debug APK，不是上架用 release 包。
- Flutter APK 已有原生相机扫码、手电筒、震动和提示音；Capacitor 路线仍是页面内手动输入、测试按钮或外部 JS bridge。
- 存储仍是 WebView localStorage；SQL.js + Capacitor Filesystem 还未接。
