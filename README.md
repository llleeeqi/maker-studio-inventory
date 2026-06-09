# 工作室物品管理

当前是最小检测版：纯 Web 核心，无构建依赖。重点是 **基于二维码输入的快捷库存管理**，不是单独的二维码生成工具。

扫码模块是外挂：核心只接收扫码结果字符串。无论是 Android 原生扫码、网页扫码库、扫码枪，还是测试按钮，只要把 `spool:...` / `weight:...` 这类 payload 交给 `window.StudioInventoryScanner.push()` 即可。

## 打开方式

### 安装 APK

当前已生成 Android debug APK：

```text
studio-inventory-debug.apk
```

这是可直接安装的调试签名包。Android 手机上如果提示来源限制，需要允许“安装未知来源应用”。

### 浏览器检测版

在本目录启动静态服务：

```bash
python3 -m http.server 8080
```

然后访问：

```text
正式库存软件：http://127.0.0.1:8080/app/
测试工具页：http://127.0.0.1:8080/tools/
```

## 已有功能

- 样例库存数据
- 新增/编辑耗材卷和零件
- 手动模拟二维码输入
- 查库存：扫 `spool:` / `part:` 立即显示库存和库位
- 盘点称重：扫 `weight:` + 物品码，顺序不限，自动写库存
- 绑定库位：扫 `location:` + 物品码，顺序不限，自动更新库位
- `weight:` + `spool:` 更新耗材毛重并计算可用重量
- `weight:` + `part:` 更新零件估算数量
- 库存搜索
- 类型筛选和低库存筛选
- 物品标签二维码生成
- 流水记录
- 本地 localStorage 保存
- JSON 快照导出
- 当前筛选结果导出
- JSON 快照导入
- JSON 快照合并导入
- 合并导入预览
- 物品归档/恢复
- 从已有物品克隆参数
- 按材料、颜色或名称自动生成 ID

## 两个入口

- `app/`：正儿八经使用的软件。第一屏就是扫码工作台。
- `tools/`：测试工具。用于生成重量码、物品码、库位码，方便在手机上扫。
- `core/`：平台无关核心。Android/Web/Docker 都应该复用它。

## 后续开发文档

从 [docs/README.md](./docs/README.md) 开始看。

- [docs/00-project-map.md](./docs/00-project-map.md)：文件分工和为什么这么分
- [docs/01-qr-input-workflows.md](./docs/01-qr-input-workflows.md)：二维码输入库存流程
- [docs/02-android-apk.md](./docs/02-android-apk.md)：Android APK 路线
- [docs/03-data-and-sync.md](./docs/03-data-and-sync.md)：本地数据和 WebDAV 同步
- [docs/04-next-steps.md](./docs/04-next-steps.md)：下一阶段开发清单
- [docs/05-catalog-management.md](./docs/05-catalog-management.md)：物品目录新增、编辑和字段规则
- [docs/06-inventory-filters.md](./docs/06-inventory-filters.md)：库存搜索、类型筛选和低库存筛选
- [docs/07-core-shell-boundary.md](./docs/07-core-shell-boundary.md)：核心和外壳的边界记录，Android 优先时重点看
- [docs/08-first-version-app.md](./docs/08-first-version-app.md)：第一版正式 app 当前状态、验证方式和已知边界

## APK 方向

网页核心已经接入 Capacitor Android 壳，并产出 debug APK。当前 APK 使用 WebView 承载本地 `www/` 静态资源：

- UI 和业务逻辑继续复用当前 Web Core。
- 数据先存在本地，后续替换成 SQL.js + 文件快照。
- 扫码不要长期靠浏览器 JS 解码，APK 内接 Android 原生扫码插件。
- 以后同一套核心也能放到 Docker 里做网页版。

重新构建：

```bash
npm run apk:debug
```

输出路径：

```text
android/app/build/outputs/apk/debug/app-debug.apk
studio-inventory-debug.apk
```

## 当前协议

```text
weight:712.4
spool:PLA-BLK-001
part:M3-INSERT
location:RACK-A01
```

## 下一步

- Android 原生扫码替换手动输入。
- SQL.js + Capacitor Filesystem 替换 localStorage。
- 增加 WebDAV 快照同步。
