# 发版工作流和项目管理

## 当前版本

```text
当前可测版: v0.5.2
APK: studio-inventory-native-0.5.2-universal.apk
推荐实体手机: studio-inventory-native-0.5.2-arm64.apk
Release: https://github.com/llleeeqi/maker-studio-inventory/releases/tag/v0.5.2
```

## 工作流

每一轮开发按这个顺序走：

```text
确认需求边界
更新 docs/14-current-progress.md 和相关设计文档
实现 Android 代码
./gradlew testDebugUnitTest assembleDebug
./gradlew clean assembleRelease
./gradlew clean assembleRelease -PtargetAbi=arm64-v8a
复制通用和 arm64 release APK 到仓库根目录
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
./gradlew testDebugUnitTest assembleDebug
通用和 arm64 release APK 文件存在
sha256sum 已记录
git status 干净
GitHub Release 可打开
```

## 版本记录

### v0.5.2

全面屏适配、本地拼音搜索和库位分组版本。

已完成：

```text
状态栏、屏幕开孔和底部手势区使用系统安全区布局
库存搜索支持中文、完整拼音、拼音首字母和轻微拼写错误
首次搜索时后台全量建立本地派生索引，并显示索引提示
本地候选点击后直接打开物品库存详情
索引不写数据库、不参与 WebDAV 同步或备份
库存变化后下次搜索全量重建，增量索引列为后续待做
库存页增加按物品/按库位切换和库位内容清单
流水页增加手动检查更新，有更高版本直接跳转 GitHub Release
首次进入扫码页显示一次开始引导，开始按钮直接启动相机
```

验证状态：

```text
./gradlew testDebugUnitTest assembleDebug 通过
Android 12 MuMu edge-to-edge 顶部状态栏避让通过
refengqi 和 rfq 均找到本地“热风枪”并打开详情
测试库位正确显示 3 件在库物品，并可继续打开物品详情
arm64 release 冷启动和 R8 后拼音搜索通过
手动检查更新在当前版本正确提示已是最新
首次引导开始相机和重启后不重复显示通过
通用 APK: 26,811,943 bytes，SHA256 ac3e09f5af91a9f3b17659d52c200d24fa30865af50d38a3c9c8f5a488ddc123
arm64 APK: 11,262,522 bytes，SHA256 e58a6bf394482b53a4146cef8a6ed0a41982e53c4ec4bf77598eec79f522f3d4
小米 17 全面屏仍待用户安装复核
```

### v0.5.1

手动测量、标签所见所得预览和 APK 精简版本。

已完成：

```text
耗材可手动录入毛重，并校验毛重大于空盘重量
零件可按总重自动估算数量，或切换为直接录入数量
重量/数量步骤块和当前流程浮层都可打开手动录入弹层
手动录入期间暂停相机分析和 20 秒空闲计时
标签预览使用真实 Q 级纠错二维码，不再使用占位图案
读取屏幕像素和 DPI，尽量按 40x30mm 实际尺寸显示预览
release 启用 R8 和资源压缩，并提供通用、arm64 两种 APK
```

验证状态：

```text
./gradlew testDebugUnitTest assembleDebug 通过
Android 12 MuMu: 耗材 650g / 空盘 200g，正确显示可用 450g
Android 12 MuMu: 零件 25g / 单重 0.5g，正确估算 50 件
Android 12 MuMu: 零件直接输入 60 件，旧总重被清除
40x30mm 预览在 MuMu 报告的 480dpi 下按约 756px 宽显示
arm64 release 冷启动、ML Kit 初始化、相机启动和标签预览通过
通用 release 冷启动通过
通用 APK: 26,713,639 bytes，SHA256 5fc40a10227caf29c4bd5d5aed0a3667a3f986e6dbc51a517d0a549d82c0e32e
arm64 APK: 11,164,218 bytes，SHA256 d02bf53d4ab30125086fefcd37bc658b0a378d053be7c62109d8229e131f1f28
真实标签机纸面尺寸和二维码可扫性仍待设备实测
```

### v0.5.0

本地优先 WebDAV 同步和全量备份版本。

已完成：

```text
SQLite schema 2 无损升级，流水增加来源设备字段
右上角小云朵全局同步状态，点击进入同步与备份页面
WebDAV 加密对象/索引仓库、设备登记、latest 指针和云锁
前台定时检查、变动防抖、WorkManager 后台同步和返回同步
base/local/remote 三方合并，完整物品冲突和追加式流水合并
冲突物品重新扫码确认在库，或确认出库/归档并写校正流水
首次绑定安全合并、采用云端、本机重建云端三种策略
本机/云端自动和手动全量备份，文件导出和导入
恢复暂停同步、取消恢复、恢复结果设为新云端基准
Android Keystore 本机凭据保护，AES-256-GCM 云端仓库和备份凭据块
索引和自动备份保留策略，防止历史无限堆积
```

验证状态：

```text
./gradlew testDebugUnitTest assembleDebug assembleRelease 通过
Debug APK SHA256: 90a9609683dc0949d9b5c025ca1ded069252e2ca47caab4a834f8dc9f3d3e44a
Android 12 MuMu 升级后保留 3 个物品、2 个库位和原有流水
真实 WebDAV 初始化、加密对象上传、库存变动同步通过
5 秒前台同步空闲 16 秒后索引数量保持不变
本机备份、云端备份、备份凭据密文检查通过
系统文件选择器导出、导入、恢复暂停和取消恢复通过
自动备份并发竞态已用数据库原子占位修正，重复元数据已清理
真实双实体设备并发冲突仍待后续复核
```

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
根据纸面效果决定是否调整标签到 50x40 或 60x40
第二台实体 Android 设备复核并发冲突和首次绑定三种策略
```

### P2: 稳定和长期维护

```text
是否从 SQLiteOpenHelper 迁成 Room DAO
正式签名
同步诊断导出和更多 WebDAV 服务兼容性测试
```

## 当前不做

```text
不换 Qt
不回到 Flutter/Capacitor
不做手动 payload 输入
不做服务端
不做账号、成员、审批和服务端协作系统
```

## 文档入口

继续开发时优先看：

```text
docs/14-current-progress.md
docs/15-release-workflow-and-project-management.md
docs/09-operation-button-logic.md
docs/01-qr-input-workflows.md
docs/03-data-and-sync.md
docs/16-webdav-sync-v1.md
```
