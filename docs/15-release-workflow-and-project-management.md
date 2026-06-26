# 发版工作流和项目管理

## 当前版本

```text
当前可测版: v0.3.8
APK: studio-inventory-native-0.3.8-debug.apk
Release: https://github.com/llleeeqi/maker-studio-inventory/releases/tag/v0.3.8
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

### P0: 先修现场使用阻塞

```text
真机验证 v0.3.8 UI 分层
修复仍然存在的重叠、遮挡、文字溢出
把物品码、重量码、库位码确认从页面内容彻底收敛到强提醒浮层或底部弹层
补齐确认弹窗里的可编辑字段: tare_g / unit_weight_g / location name / note
```

### P1: 完整日常闭环

```text
库存详情页补低频动作入口: 出库、归档、查看最近流水
增加导出/导入入口
打印机到货后验证 40x30mm 纸面效果
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
