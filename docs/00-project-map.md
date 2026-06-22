# 项目文件地图

## 当前目标

当前项目不是通用 ERP，也不是单纯二维码生成器，而是 **扫码驱动的个人工作室库存系统**。

当前开发主线：

```text
原生 Android app
```

第一版目标是先在 Android 手机上跑通：

```text
生成固定标签 -> 扫码 -> 补重量/数量和库位 -> 点确认入库 -> 本地 JSON 保存 -> 查库存/出库/整理库位
```

## 顶层结构

```text
工作室物品管理/
├── mobile_android/      # 新原生 Android 主线
├── tools/               # 虚拟货架测试台
├── core/                # 早期 Web 检测版的 JS 业务核心，作为参考和测试资料
├── app/                 # 早期 Web 检测版 UI，作为参考和工具备份
├── android/             # 早期 Android Web 壳工程，历史目录
├── mobile_flutter/      # Flutter 0.2.x 历史实现，保留作参考
├── tests/               # 早期 JS 核心测试
├── docs/                # 当前设计和后续开发记录
├── README.md            # 当前状态和快速说明
├── 架构决策.md          # 当前技术路线取舍
└── 项目大纲.md          # 原始大纲
```

## mobile_android/

`mobile_android/` 是新主线目录，使用：

```text
Kotlin
Jetpack Compose
CameraX PreviewView
ML Kit Barcode Scanning
JSON snapshot 文件
```

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

当前 debug APK：

```text
studio-inventory-native-0.3.4-debug.apk
```

## tools/

`tools/` 是虚拟货架测试台，不是正式库存软件。

| 文件 | 职责 |
|---|---|
| `index.html` | 虚拟货架、测试库位、测试物品和重量码页面 |
| `tools.js` | 生成 `v1;` 测试二维码和推荐扫码顺序 |

电脑 Web 测试工具只模拟真实现场：货架、标签、重量二维码。它不提供复制 payload 或手动输入入口。

## core/ 和 app/

`core/` 与 `app/` 是早期 Web 检测版。它们验证过库存状态机、快照、筛选、二维码输入等概念，但不再作为 Android app 的主实现约束。

保留原因：

- 旧逻辑可以作为 Kotlin 业务规则的参考。
- `tests/` 里仍有部分流程用例可迁移。
- `tools/` 仍方便模拟已贴标签的测试现场。

新 Android app 可以直接用 Kotlin 实现业务规则；硬约束是 `v1` 字段语义和文档一致。

## mobile_flutter/

`mobile_flutter/` 是 Flutter 0.2.x 历史实现，当前不再继续作为主线。

保留原因：

- 可查看早期 Material 3 页面组织。
- 可参考扫码诊断、库存页面和 JSON 结构尝试。
- 已生成的 APK 仍可作为历史测试包。

不要再基于该目录继续补手机主功能。

## android/

`android/` 是早期 Android Web 壳工程，当前作为历史目录保留，不再继续推进。

## docs/

当前建议从这里开始：

1. [13-native-android-v1-plan.md](./13-native-android-v1-plan.md)
2. [12-v1-protocol-and-scope.md](./12-v1-protocol-and-scope.md)
3. [01-qr-input-workflows.md](./01-qr-input-workflows.md)
4. [09-operation-button-logic.md](./09-operation-button-logic.md)
