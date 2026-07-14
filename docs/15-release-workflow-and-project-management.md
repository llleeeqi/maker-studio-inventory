# 发版工作流和项目管理

## 当前版本

```text
当前可测版: v0.4.1
APK: studio-inventory-native-0.4.1-debug.apk
Release: https://github.com/llleeeqi/maker-studio-inventory/releases/tag/v0.4.1
```

## 工作流

每一轮开发按这个顺序走：

```text
确认需求边界
更新 docs/14-current-progress.md 和相关设计文档
实现 Android 代码
./gradlew assembleDebug
复制 APK 到仓库根目录
计算 sha256sum
git commit
git tag vX.Y.Z
git push origin main vX.Y.Z
gh release create 并上传 APK
最终确认 release asset 已上传
```

发版前必须确认：

```text
git diff --check
./gradlew assembleDebug
APK 文件存在
sha256sum 已记录
git status 干净
GitHub Release 可打开
```

## 版本记录

### v0.4.1

模拟器验收和交互收口版本。

已完成：

```text
中性灰+青绿主题和图标底部导航
相机控制压缩为启动/暂停主按钮和手电筒
当前流程浮层缩短，不遮挡步骤、相机控制和底部导航
物品确认默认显示摘要，需要时再展开编辑
扫码规则集中到 ScanWorkflowRules 并补单元测试
库存卡片只显示备注，不泄露内部 searchText
详情和流水使用中文动作及可读日期时间
固定重量参数保持原精度，0.42g 不再被改写为 0.4g
SQLite 日常操作改为逐表增量 upsert/append，整库替换只用于迁移/导入
库存搜索规则抽出并覆盖固定字段、库位、类型和状态组合测试
流水页显示版本和可点击 GitHub 仓库地址
debug 构建提供受保护的自动化扫码广播，release 构建不包含入口
```

验证状态：

```text
./gradlew testDebugUnitTest assembleDebug 通过
APK 版本 0.4.1 (41)
SHA256: 92db41731ff4464847b710fd63ca5c530546117b18c3e300e2d9a129fa812db5
Android 12 MuMu: 耗材/零件/其他入库通过
更新库存、绑定库位、整理库位、重复扫码跳过、出库通过
模式切换保留/清空、库存详情、流水、20 秒自动暂停通过
SQLite 直查：更新目标行、保留其他行、追加流水/日志通过
库存搜索品牌关键字、类型过滤和仓库跳转通过
实体手机相机/大字体/软键盘和 40x30mm 真实打印待复核
```

### v0.4.0

扫码交互层重写版本。

已完成：

```text
单一 ScanWorkflowState 管理扫码会话
物品、重量和库位扫码后进入底部确认层
确认期间暂停相机分析，确认或取消后恢复
未完成流程切换模式时确认清空
当前流程浮在底部导航上方，并按模式精简字段
库存详情展示关键变量、时间和最近流水
详情内二次确认出库或归档
归档物品拒绝重新进入扫码流程
库位整理重复扫已在目标库位的物品时跳过
离开扫码页释放扫描器和分析线程
```

验证状态：

```text
./gradlew assembleDebug 通过
APK 版本 0.4.0 (40)
SHA256: 8dcd6029f5705ad7fff6539497ba9df18ae669ced5354b752744df3b94f85d97
真机视觉、相机暂停恢复和软键盘布局待验
```

### v0.3.8

UI 分层修正版本。

已完成：

```text
顶部标题缩小并左上角显示
扫码页相机区域固定高度，启动前后不撑开页面
步骤条改成灰/蓝/绿状态块
扫码流程未完成时，底部导航上方常驻强提醒浮层
库存列表项可点击打开详情
详情展示耗材毛重/空盘/可用重量，零件总重/单重/数量，以及库位和时间字段
```

待真机重点验证：

```text
相机启动后是否还会撑大或覆盖
强提醒浮层是否挡住底部导航
小屏幕/大字体下按钮和文字是否换行合理
库存详情弹层是否能稳定打开和关闭
```

### v0.3.7

数据边界版本。

已完成：

```text
Android SQLite 四表: items / locations / transactions / scan_logs
旧 JSON 测试数据在数据库为空时自动迁移
主流水只记录入库、出库、更新重量/数量、撤销
绑定库位、整理库位、归档、固定字段修改不进主流水
出库清空库位，但保留最后重量/数量
扫码页三模式: 入库 / 更新库存 / 绑定库位
```

### v0.3.6

真机可用性验证版本。

已验证：

```text
扫码功能正常
tools 虚拟货架耗材/重量/库位扫码链路可用
连接德佟标签打印机功能正常
```

## 当前项目队列

### P0: 实体设备和标签打印复核

```text
实体 Android 手机复核相机画面和扫码速度
复核大字体、软键盘下没有重叠和遮挡
标签机到货后验证 40x30mm 纸面排版和二维码可扫性
```

### P1: 完整日常闭环

```text
增加导出/导入入口
根据纸面效果决定是否调整标签到 50x40 或 60x40
```

### P2: 稳定和长期维护

```text
是否从 SQLiteOpenHelper 迁成 Room DAO
正式签名
备份和恢复
WebDAV 或其他同步
```

## 当前不做

```text
不换 Qt
不回到 Flutter/Capacitor
不做手动 payload 输入
不做服务端
不做多设备协作
```

## 文档入口

继续开发时优先看：

```text
docs/14-current-progress.md
docs/15-release-workflow-and-project-management.md
docs/09-operation-button-logic.md
docs/01-qr-input-workflows.md
docs/03-data-and-sync.md
```
