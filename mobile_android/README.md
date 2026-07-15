# 原生 Android app

这是当前手机端主线。

```text
applicationId: studio.inventory.android
versionName: 0.5.2
minSdk: 31
targetSdk: 36
compileSdk: 36
```

技术栈：

```text
Kotlin
Jetpack Compose
CameraX
ML Kit Barcode Scanning
Android SQLite
Gson JSON migration/export format
```

## 构建

```bash
cd mobile_android
./gradlew testDebugUnitTest assembleDebug
./gradlew clean assembleRelease
./gradlew clean assembleRelease -PtargetAbi=arm64-v8a
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

仓库根目录也会保留一份便于安装测试：

```text
studio-inventory-native-0.5.2-universal.apk
studio-inventory-native-0.5.2-arm64.apk
```

0.5.2 已在 Android 12 MuMu 模拟器完成全面屏、本地拼音搜索和库位分组验收。小米 17 全面屏、实体手机大字体/软键盘和真实标签打印仍需后续复核。逐项测试步骤见 `MANUAL_TEST_MATRIX.md`。

下载：[GitHub Release v0.5.2](https://github.com/llleeeqi/maker-studio-inventory/releases/tag/v0.5.2)

## 0.5.2 变更表

| 范围 | 新行为 | 验证 |
|---|---|---|
| 全面屏 | 顶部和底部系统栏使用安全区，不再与透明状态栏重叠 | MuMu 通过 |
| 拼音搜索 | 中文、完整拼音、首字母、包含和轻微错字匹配 | 单元测试和 MuMu 通过 |
| 本地索引 | 首次搜索后台全量生成；不写数据库、不进同步和备份 | MuMu 通过 |
| 搜索候选 | 最多 6 项，点击直接进入库存详情 | MuMu 通过 |
| 库位分组 | 按库位查看当前在库物品并下钻详情 | MuMu 通过 |
| 手动更新 | 点击检查；发现新版直接打开对应 GitHub Release | MuMu 通过 |
| 首次引导 | 一次性开始引导，开始按钮直接启动扫码 | MuMu 通过 |

## 0.5.1 变更表

| 范围 | 新行为 | 验证 |
|---|---|---|
| 手动测量 | 耗材录入毛重；零件按总重估算或直接录入数量 | MuMu 通过 |
| 标签预览 | SDK bitmap 不可用时使用 ZXing 真实二维码 | MuMu 通过 |
| 物理尺寸 | 按屏幕报告 DPI 尽量 1:1 显示 40x30mm | MuMu 通过 |
| APK 精简 | release 开启 R8/资源压缩，支持通用和 arm64 构建 | 构建通过 |

## 0.4.1 变更表

| 范围 | 新行为 | 验证 |
|---|---|---|
| 扫码会话 | 使用单一 `ScanWorkflowState` 管理模式、物品、重量/数量、库位、整理状态和当前确认 | 编译通过 |
| 确认等待 | 扫码后暂停相机分析，物品、重量和库位分别在底部确认层处理 | MuMu 通过 |
| 底部浮层 | 当前流程常驻底部导航上方，只显示当前模式需要的字段 | MuMu 通过 |
| 模式切换 | 未完成流程切换模式时先确认清空 | 编译通过 |
| 库位整理 | 连续扫码自动移动；重复扫已在目标库位的物品时跳过 | 编译通过 |
| 库存详情 | 展示关键变量、位置、格式化时间和最近三条主流水 | MuMu 通过 |
| 出库/归档 | 从详情层发起并二次确认；归档物品不再允许扫码操作 | 编译通过 |
| 相机生命周期 | 20 秒空闲暂停，确认期间停分析，离页释放扫描器和分析线程 | MuMu 通过 |
| 固定参数精度 | 单重和空盘参数按原精度回填，不因确认页格式化改写数据 | 单元测试和 MuMu 通过 |
| 视觉层级 | 中性灰+青绿主题、图标底栏、紧凑相机控制和流程浮层 | MuMu 通过 |
| SQLite 写入 | 日常操作按物品、库位、流水和扫码日志增量写入，整表替换只用于迁移/导入 | SQLite 直查通过 |
| 库存搜索 | 搜索固定字段、备注、中文库位，并组合类型和状态筛选 | 单元测试和 MuMu 通过 |
| 更新下载 | 流水页显示版本和可点击 GitHub 仓库地址 | MuMu 通过 |

交互边界见 `INTERACTION_REWRITE.md`，完整验收步骤见 `MANUAL_TEST_MATRIX.md`。

## 当前功能

- 四个入口：扫码 / 库存 / 新增 / 流水。
- 页面内嵌 CameraX 相机预览。
- 相机空闲 20 秒自动暂停，减少耗电。
- ML Kit 只识别二维码。
- 只认 `v1;`。
- 中文字段值写入二维码时会百分号编码，扫码后自动解码。
- 新增页生成固定标签并接入德佟 LPAPI 打印骨架。
- 生成标签后展示 40x30mm 标签预览。
- 打印机搜索可手动停止，20 秒后也会自动停止。
- 可开启启动自动连接打标机，优先连接上次打印机。
- 40x30mm 标签模板：左侧三行文字，右侧 Q 级纠错二维码。
- 扫码页顶部三模式：入库 / 更新库存 / 绑定库位。
- 0.4.1 重写扫码交互层，只维护一个扫码会话状态。
- 二维码识别后暂停相机分析，确认或取消后自动恢复。
- 物品、重量和库位必须先在底部确认层确认，确认后才进入当前流程。
- 确认物品后可点击“重量/数量”步骤或当前流程“手动录入”，无需电子秤生成重量二维码。
- 正常流程仍不提供手动 payload 输入。
- 切换模式时如果当前流程未完成，必须确认清空后才能切换。
- 当前流程常驻底部导航上方，完成或取消后自动消失。
- 库存列表项可点击查看详情，能看到耗材毛重/空盘/可用重量和零件总重/单重/数量。
- 库存搜索支持中文、拼音、拼音首字母和轻微拼写错误，索引仅在本机后台生成。
- 库存页可切换按物品或按库位查看，库位详情列出该位置的全部在库物品。
- 流水页可手动检查更新，不在后台自动请求；有新版本直接打开发布页。
- 首次进入扫码页显示一次开始引导，点开始后直接启动相机。
- 库存详情可二次确认出库或归档，并查看最近流水。
- 入库、出库、盘点、绑定库位、归档、撤销上一笔。
- 库位整理模式。
- 本地 SQLite 四表保存：items / locations / transactions / scan_logs。
- 旧 JSON 测试数据会在数据库为空时自动迁移。
- 主流水只记录入库、出库、更新重量/数量和撤销；绑定库位、整理库位、归档和固定字段修改不进流水。
- 日常数据库写入使用单条 upsert/append；旧 JSON 迁移和显式导入才执行整库替换。
- 流水页显示当前版本和项目仓库，可跳转 GitHub Release 下载更新。
