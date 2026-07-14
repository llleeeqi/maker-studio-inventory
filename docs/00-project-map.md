# 项目文件地图

## 当前目标

当前项目不是通用 ERP，也不是单纯二维码生成器，而是 **扫码驱动的个人工作室库存系统**。

当前开发主线：

```text
原生 Android app
```

第一版目标是先在 Android 手机上跑通：

```text
生成固定标签 -> 扫码 -> 补重量/数量和库位 -> 点确认入库 -> 本地数据库保存 -> 查库存/出库/整理库位
```

## 顶层结构

```text
工作室物品管理/
├── mobile_android/      # 新原生 Android 主线
├── tools/               # 虚拟货架测试台
├── docs/                # 当前设计和后续开发记录
├── README.md            # 当前状态和快速说明
├── 架构决策.md          # 当前技术路线取舍
├── studio-inventory-native-0.5.1-universal.apk
└── studio-inventory-native-0.5.1-arm64.apk
```

## mobile_android/

`mobile_android/` 是新主线目录，使用：

```text
Kotlin
Jetpack Compose
CameraX PreviewView
ML Kit Barcode Scanning
Android SQLite 本地数据库
OkHttp WebDAV
WorkManager
```

当前 0.5.1 测试 APK 已使用 Android SQLite schema 2，并实现本地优先加密 WebDAV 同步、全量备份恢复、手动测量值录入和真实二维码标签预览；旧 JSON snapshot 只作为自动迁移源。

Android 配置：

```text
applicationId = studio.inventory.android
minSdk = 31
targetSdk = 36
compileSdk = 36
```

第一版页面：

```text
扫码 / 库存 / 新增 / 流水
```

详见 [13-native-android-v1-plan.md](./13-native-android-v1-plan.md)。

当前 release APK：

```text
studio-inventory-native-0.5.1-universal.apk
studio-inventory-native-0.5.1-arm64.apk
```

## tools/

`tools/` 是虚拟货架测试台，不是正式库存软件。

线上地址: https://llleeeqi.github.io/maker-studio-inventory/tools/

| 文件 | 职责 |
|---|---|
| `index.html` | 虚拟货架、测试库位、测试物品和重量码页面 |
| `tools.js` | 生成 `v1;` 测试二维码和推荐扫码顺序 |

电脑 Web 测试工具只模拟真实现场：货架、标签、重量二维码。它不提供复制 payload 或手动输入入口。

## 已清理的历史实现

以下旧路线已经从仓库中删除，不再作为参考目录保留：

```text
core/
app/
tests/
android/
mobile_flutter/
Web/Capacitor 顶层配置
Flutter 旧 APK
旧 native APK
```

后续功能直接落在 `mobile_android/`。电脑端测试只保留 `tools/` 虚拟货架。

## docs/

当前建议从这里开始：

1. [14-current-progress.md](./14-current-progress.md)
2. [13-native-android-v1-plan.md](./13-native-android-v1-plan.md)
3. [12-v1-protocol-and-scope.md](./12-v1-protocol-and-scope.md)
4. [01-qr-input-workflows.md](./01-qr-input-workflows.md)
5. [09-operation-button-logic.md](./09-operation-button-logic.md)
6. [16-webdav-sync-v1.md](./16-webdav-sync-v1.md)
