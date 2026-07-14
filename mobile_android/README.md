# 原生 Android app

这是当前手机端主线。

```text
applicationId: studio.inventory.android
versionName: 0.4.0
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
./gradlew assembleDebug
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

仓库根目录也会保留一份便于安装测试：

```text
studio-inventory-native-0.4.0-debug.apk
```

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
- 0.4.0 重写扫码交互层，只维护一个扫码会话状态。
- 二维码识别后暂停相机分析，确认或取消后自动恢复。
- 物品、重量和库位必须先在底部确认层确认，确认后才进入当前流程。
- 切换模式时如果当前流程未完成，必须确认清空后才能切换。
- 当前流程常驻底部导航上方，完成或取消后自动消失。
- 库存列表项可点击查看详情，能看到耗材毛重/空盘/可用重量和零件总重/单重/数量。
- 库存详情可二次确认出库或归档，并查看最近流水。
- 入库、出库、盘点、绑定库位、归档、撤销上一笔。
- 库位整理模式。
- 本地 SQLite 四表保存：items / locations / transactions / scan_logs。
- 旧 JSON 测试数据会在数据库为空时自动迁移。
- 主流水只记录入库、出库、更新重量/数量和撤销；绑定库位、整理库位、归档和固定字段修改不进流水。
