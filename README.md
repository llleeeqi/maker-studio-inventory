# 个人工作室耗材与五金库存管理

面向中文用户的个人工作室库存管理项目。适合 3D 打印、模型制作、电子维修、五金收纳、耗材盘点和小型创客空间。

English documentation is available as a secondary reference: [README.en.md](./README.en.md)

## 项目定位

这是一个 **扫码驱动的个人工作室库存管理 App**。

核心目标：

```text
生成固定标签 -> 扫码 -> 补重量/数量和库位 -> 点确认写入 -> 本地查库存
```

当前主线是原生 Android 第一版：

```text
mobile_android/
Kotlin + Jetpack Compose + CameraX + ML Kit + JSON snapshot
```

旧 Web/Capacitor 和 Flutter 手机实现只作为历史参考，不再作为继续开发方向。

## 当前决策

Android 配置：

```text
applicationId = studio.inventory.android
minSdk = 31
targetSdk = 36
compileSdk = 36
```

第一版只做 Android 本地闭环：

- 页面内嵌相机扫码，只识别二维码。
- 只支持 `msi:v1`，不兼容旧短码。
- 新增页只生成标签 payload，打印先占位。
- 扫物品码只展示固定信息，不自动写库存。
- 入库必须补齐物品、重量/数量和库位，并点击确认。
- 库位整理模式允许进入后连续扫码自动更新库位。
- 本地先用 JSON 文件保存，后续流程跑通再迁到数据库。

## 二维码协议

长期协议：

```text
msi:v1;key=value;key=value
```

示例：

```text
msi:v1;type=spool;id=FIL-260617-001;brand=Bambu;material=PLA;color=white;tare_g=200
msi:v1;type=part;id=PART-260617-001;name=M3x8黑色圆头螺丝;category=screw;spec=M3x8;unit_weight_g=0.42
msi:v1;type=other;id=ITEM-260617-001;name=热风枪
msi:v1;type=location;id=LOC-260617-001;name=A架第一层
msi:v1;type=weight;value_g=712.4
```

规则：

- 二维码只存固定信息，不存当前重量、数量、库位和状态。
- 每张实体标签唯一 ID。
- 耗材名称可由 `brand + material + color` 自动生成。
- 耗材入库必须有当前毛重，且 `current_g > tare_g`。
- 零件主变量是数量，可用总重量和 `unit_weight_g` 换算。
- 入库必须有库位。

## 目录

| 路径 | 作用 |
|---|---|
| `mobile_android/` | 新原生 Android 主线，已创建 |
| `tools/` | `msi:v1` 测试 payload 工具 |
| `core/` | 早期 Web 检测版 JS 核心，保留作参考 |
| `app/` | 早期 Web 检测版 UI，保留作参考 |
| `android/` | 早期 Android Web 壳工程，历史目录 |
| `mobile_flutter/` | Flutter 0.2.x 历史实现，保留作参考 |
| `tests/` | 早期 JS 核心测试 |
| `docs/` | 当前设计和后续开发记录 |

## 文档

从 [docs/README.md](./docs/README.md) 开始看。

关键文档：

- [架构决策.md](./架构决策.md)：当前技术路线。
- [docs/13-native-android-v1-plan.md](./docs/13-native-android-v1-plan.md)：原生 Android v1 总计划。
- [docs/12-msi-v1-and-0.2-scope.md](./docs/12-msi-v1-and-0.2-scope.md)：`msi:v1` 协议和本地记录边界。
- [docs/01-qr-input-workflows.md](./docs/01-qr-input-workflows.md)：扫码输入流程。
- [docs/04-next-steps.md](./docs/04-next-steps.md)：下一阶段开发清单。

## 后续方向

近期：

1. 真机测试原生 debug APK：`studio-inventory-native-0.3.3-debug.apk`。
2. 根据真机反馈修扫码预览、入库上下文和库位整理细节。
3. 补导出/导入 JSON。
4. 流程稳定后迁 SQLite/Room。

中期：

- SQLite/Room 数据库。
- 导出/导入和备份。
- WebDAV 或其他同步。
- 正式签名。
- 打印能力。
