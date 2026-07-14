# 个人工作室耗材与五金库存管理

面向中文用户的个人工作室库存管理项目。适合 3D 打印、模型制作、电子维修、五金收纳、耗材盘点和小型创客空间。

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

当前 0.4.1 测试 APK 已使用 Android SQLite 四表；旧 JSON snapshot 只作为自动迁移源。

下载：[GitHub Release v0.4.1](https://github.com/llleeeqi/maker-studio-inventory/releases/tag/v0.4.1)

旧 Web/Capacitor、Flutter 手机实现和早期 JS 检测版已清理。当前仓库只保留原生 Android 主线、虚拟货架测试台、文档和最新测试 APK。

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
- SQLite 数据库使用 `items / locations / transactions / scan_logs` 四张表。
- 0.4.1 已重写扫码会话、扫码后确认、模式切换和底部当前流程浮层。
- 0.4.1 库存列表支持点击详情，并提供二次确认的出库和归档。
- 0.4.1 已在 Android 12 MuMu 模拟器完成入库、更新库存、绑定/整理库位、出库、详情和流水验收。

## 0.4.1 改动

| 范围 | 原问题 | 0.4.1 行为 | 状态 |
|---|---|---|---|
| 扫码状态 | 物品、重量、库位和冲突状态分散，容易互相覆盖 | 统一为一个 `ScanWorkflowState`，一个流程只保留一套当前上下文 | 已构建 |
| 扫码确认 | 扫码结果塞在页面里，等待和确认不清晰 | 识别后暂停相机分析，在 App 内底部确认层确认或取消 | MuMu 已验收 |
| 当前流程 | 提醒区容易挤压、覆盖页面 | 当前流程浮在底部导航上方，并按模式和物品类型只显示必要字段 | MuMu 已验收 |
| 模式切换 | 未完成流程可能被直接丢失 | 切换入库、更新库存、绑定库位前确认是否清空 | 已构建 |
| 库位整理 | 连续扫码可能重复写同一物品 | 已在目标库位的物品直接跳过，不重复更新时间和成功日志 | 已构建 |
| 库存详情 | 列表不能展开查看关键变量 | 点击查看耗材重量、零件数量、库位、格式化时间和最近流水 | MuMu 已验收 |
| 低频动作 | 出库和归档入口不明确 | 从库存详情发起，并再次确认 | MuMu 已验收 |
| 归档边界 | 归档标签仍可能重新进入扫码流程 | 归档物品拒绝入库、盘点、绑定和整理库位 | 已构建 |
| 相机资源 | 离开扫码页后分析线程和扫描器可能残留 | 页面销毁时释放资源，20 秒无扫码自动暂停 | MuMu 已验收 |
| 零件精度 | `0.42g` 单重会被确认页四舍五入成 `0.4g` | 固定重量参数按原精度回填和显示，420g 正确换算为 1000 件 | 单元测试和 MuMu 已验收 |
| 数据落盘 | 每次保存会整批重写四张表 | 日常操作改为物品、库位、流水、扫码日志分别增量写入；整表替换只用于迁移/导入 | SQLite 直查已验收 |
| 库存查找 | 搜索和类型/状态组合条件缺少独立验证 | 支持 ID、名称、品牌、材料、颜色、备注和库位，组合类型/状态过滤 | 单元测试和 MuMu 已验收 |
| 更新入口 | App 内找不到项目和新版本下载位置 | 流水页显示版本和可点击 GitHub 仓库地址 | MuMu 已验收 |

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
| `mobile_android/` | 原生 Android 主线 |
| `tools/` | 虚拟货架测试台 |
| `docs/` | 当前设计和后续开发记录 |
| `studio-inventory-native-0.4.1-debug.apk` | 当前本地最新测试 APK |

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
- [docs/15-release-workflow-and-project-management.md](./docs/15-release-workflow-and-project-management.md)：发版流程、版本记录和项目队列。
- [mobile_android/INTERACTION_REWRITE.md](./mobile_android/INTERACTION_REWRITE.md)：0.4.1 交互重写边界。
- [mobile_android/MANUAL_TEST_MATRIX.md](./mobile_android/MANUAL_TEST_MATRIX.md)：0.4.1 验收表。

## 后续方向

近期：

1. 在实体 Android 手机上复核相机画面、软键盘和大字体布局。
2. 标签机到手后验证 40x30mm 实际打印和二维码可扫性。
3. 补导出/导入能力。

中期：

- 是否把当前 SQLiteOpenHelper 数据层迁成 Room DAO。
- 导出/导入和备份。
- WebDAV 或其他同步。
- 正式签名。
- 真机验证打印能力。
