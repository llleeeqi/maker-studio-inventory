# 下一阶段开发清单

## 正式 app 第一版已完成

这些已经在 `app/` 和 `core/` 里接通，并有核心测试覆盖：

1. 停用/归档物品：`core/catalog.js` 的 `archiveCatalogItem()`，正式 app 表单按钮可归档/恢复。
2. 克隆物品：`cloneCatalogForm()` 从已有物品复制参数，新物品清空库位和当前库存。
3. 自动 ID 生成规则：`generateCatalogId()` 按耗材材料/颜色或零件名称生成递增 ID。
4. 快照合并预览：导入前调用 `previewMergeStates()`，显示远程新增、远程覆盖、本地保留和流水变化。
5. 备份/调试快照：查库存页保留折叠的“备份和调试”入口，用于导出本地备份、合并导入和覆盖恢复。

第一版状态详见 [08-first-version-app.md](./08-first-version-app.md)。

## 0.2：手机本地扫码闭环

0.2 只做手机本地闭环，详见 [12-msi-v1-and-0.2-scope.md](./12-msi-v1-and-0.2-scope.md)。

1. Flutter app 支持 `msi:v1` 可读二维码协议。
2. 新增/编辑能生成 `spool`、`part`、`other`、`location`、`weight` payload。
3. 扫未知 `msi:v1` 标签能恢复 `ItemProfile`，但不自动创建库存状态。
4. 数据模型拆成 `ItemProfile`、`ItemState`、`Transaction`。
5. 入库必须满足类型条件：耗材要当前重量，零件要数量或总重量，其他只需确认。
6. 库位可选，不阻塞入库；后续可扫码绑定。
7. 关键动作写流水，流水保留带时区的精确 `created_at` 和 `device_id`。
8. 本地先用 JSON 快照持久化，支持导出/导入恢复。
9. Flutter Dart 逻辑可以独立实现，但 payload、快照和字段语义必须和文档一致。

## 正式 app 继续补强

1. 给归档操作加二次确认，避免误归档。
2. 标签下拉默认隐藏已归档物品，必要时再提供显示开关。
3. 增加“未称重 / 未估算”筛选。
4. 增加“按库位分组”和“只看某货架/箱子”。
5. 给导入预览增加更细的字段差异展示。

## 同步

WebDAV 放中期，0.2 先只做本地 JSON 快照和手动导入导出。

1. 实现 WebDAV 配置页。
2. 实现上传本地 snapshot。
3. 实现下载远程 snapshot。
4. 下载后合并 `profiles`、`states`、`transactions`。
5. 同步成功后上传合并后的新 snapshot。

## 中期：助手与服务端路线

1. WebDAV 自动同步。
2. 增加自然语言助手查询，先只读库存和低库存状态，不直接改数据。
3. 调研 Docker 服务端 + PWA 渐进式方案。
4. 评估 PWA 在手机浏览器里的扫码速度、离线能力和安装体验。
5. 如果 PWA 高频扫码性能不够，继续保留 Android APK 作为主扫码入口。
6. 服务端版本优先复用 `msi:v1`、快照协议和合并规则。

## 远期：多设备和 PWA

1. Docker 服务端承载共享库存数据。
2. 手机、平板和桌面通过 PWA 访问同一份库存。
3. 增加多设备协作、权限、备份恢复和发布流程。

## 打印

1. 先等厂商 Web/JS SDK，优先验证电脑 Web 端通过 USB 打印文字 + 二维码。
2. 第一版只做打印验证页，用于验证 `msi:v1` payload、中文文字和二维码可扫性。
3. Android / iOS / 小程序 / UNIAPP 蓝牙 SDK 放后面。
4. ESP8266 局域网打印桥放中期探索。
