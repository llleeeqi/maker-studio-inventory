# 下一阶段开发清单

## 正式 app 第一版已完成

这些已经在 `app/` 和 `core/` 里接通，并有核心测试覆盖：

1. 停用/归档物品：`core/catalog.js` 的 `archiveCatalogItem()`，正式 app 表单按钮可归档/恢复。
2. 克隆物品：`cloneCatalogForm()` 从已有物品复制参数，新物品清空库位和当前库存。
3. 自动 ID 生成规则：`generateCatalogId()` 按耗材材料/颜色或零件名称生成递增 ID。
4. 快照合并预览：导入前调用 `previewMergeStates()`，显示远程新增、远程覆盖、本地保留和流水变化。
5. 导出当前筛选结果：`查库存` 页的“导出当前列表”只导出当前筛选命中的物品和相关流水。

第一版状态详见 [08-first-version-app.md](./08-first-version-app.md)。

## 正式 app 继续补强

1. 给归档操作加二次确认，避免误归档。
2. 标签下拉默认隐藏已归档物品，必要时再提供显示开关。
3. 增加“未称重 / 未估算”筛选。
4. 增加“按库位分组”和“只看某货架/箱子”。
5. 给导入预览增加更细的字段差异展示。

## Android APK

1. 初始化 Capacitor 工程。
2. 把 `app/` 作为 Web 根。
3. 接原生二维码扫码插件。
4. 把扫码结果传给 `window.StudioInventory.handleScanPayload(payload)`。
5. 把 `storage.js` 替换为 SQL.js + 文件存储。

## 同步

1. 实现 WebDAV 配置页。
2. 实现上传本地 snapshot。
3. 实现下载远程 snapshot。
4. 下载后调用 `mergeStates()` 合并。
5. 同步成功后上传合并后的新 snapshot。

## 打印

1. 标签模板先在 `app/label` 思路下稳定。
2. Android BLE 连接精臣。
3. 把二维码和文字转成打印机协议包。
4. 打印成功写流水。
