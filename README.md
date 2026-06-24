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
Kotlin + Jetpack Compose + CameraX + ML Kit
```

当前 0.3.7 测试 APK 已使用 Android SQLite 四表；旧 JSON snapshot 只作为自动迁移源。

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
- 只支持 `v1;`，不兼容旧短码和 `msi:v1;`。
- 新增页生成固定标签，并接入德佟 LPAPI 打印骨架。
- 扫物品码只展示固定信息，不自动写库存。
- 入库必须补齐物品、重量/数量和库位，并点击确认。
- 库位整理模式允许进入后连续扫码自动更新库位。
- 0.3.7 已起 SQLite 数据库，使用 `items / locations / transactions / scan_logs` 四张表。

## 二维码协议

长期协议：

```text
v1;key=value;key=value
```

示例：

```text
v1;type=spool;id=FIL-260617-001;brand=Bambu;material=PLA;color=white;tare_g=200;created_on=260622;note=备注
v1;type=part;id=PART-260617-001;name=M3x8黑色圆头螺丝;unit_weight_g=0.42;created_on=260622;note=备注
v1;type=other;id=ITEM-260617-001;name=热风枪;created_on=260622;note=备注
v1;type=location;id=LOC-260617-001;name=A架第一层;created_on=260622;note=备注
v1;type=weight;value_g=712.4
```

规则：

- 二维码只存固定信息，不存当前重量、数量、库位和状态。
- 字段值里的中文、空格和分隔符会在二维码中百分号编码，App 扫码后自动解码。
- 每张实体标签唯一 ID。
- 耗材名称可由 `brand + material + color` 自动生成。
- 耗材入库必须有当前毛重，且 `current_g > tare_g`。
- 零件主变量是数量，可用总重量和 `unit_weight_g` 换算。
- 入库必须有库位。

## 目录

| 路径 | 作用 |
|---|---|
| `mobile_android/` | 新原生 Android 主线，已创建 |
| `tools/` | 虚拟货架测试台 |
| `core/` | 早期 Web 检测版 JS 核心，保留作参考 |
| `app/` | 早期 Web 检测版 UI，保留作参考 |
| `android/` | 早期 Android Web 壳工程，历史目录 |
| `mobile_flutter/` | Flutter 0.2.x 历史实现，保留作参考 |
| `tests/` | 早期 JS 核心测试 |
| `docs/` | 当前设计和后续开发记录 |

虚拟货架测试台：

- GitHub Pages: https://llleeeqi.github.io/maker-studio-inventory/tools/
- 本地打开: [tools/index.html](./tools/index.html)

## 文档

从 [docs/README.md](./docs/README.md) 开始看。

关键文档：

- [架构决策.md](./架构决策.md)：当前技术路线。
- [docs/13-native-android-v1-plan.md](./docs/13-native-android-v1-plan.md)：原生 Android v1 总计划。
- [docs/12-v1-protocol-and-scope.md](./docs/12-v1-protocol-and-scope.md)：`v1;` 协议和本地记录边界。
- [docs/01-qr-input-workflows.md](./docs/01-qr-input-workflows.md)：扫码输入流程。
- [docs/04-next-steps.md](./docs/04-next-steps.md)：下一阶段开发清单。
- [docs/14-current-progress.md](./docs/14-current-progress.md)：当前实测进度和上下文恢复入口。

## 后续方向

近期：

1. 梳理并确认手机 App 页面按钮、可点击条件和点击后流程。
2. 按确认后的操作逻辑调整扫码页、库存页、新增页和流水页。
3. 补扫码后 App 内确认弹窗的可编辑字段。
4. 补导出/导入能力。

中期：

- 是否把当前 SQLiteOpenHelper 数据层迁成 Room DAO。
- 导出/导入和备份。
- WebDAV 或其他同步。
- 正式签名。
- 真机验证打印能力。
