# 核心 / 外壳边界记录

## 当前理解

这个项目可以类比“前后端”，但不是传统服务器前后端。

更准确的结构是：

```text
外壳 Shell
  - 展示库存表
  - 提供录入界面
  - 接扫码枪 / 相机 / Android 原生扫码 / 测试按钮
  - 把扫到的 payload 交给核心

核心 Core
  - 解析 payload
  - 维护物品表
  - 计算库存
  - 写流水
  - 导入/导出/合并快照
```

所以核心不依赖 Android，不依赖浏览器，也不依赖某个二维码库。

## 为什么这样拆

用户操作上看：

```text
查表：外壳展示核心里的物品表
录入：外壳把扫码结果交给核心
盘点：核心按当前上下文计算并更新表
同步：核心导出/导入/合并快照，外壳或同步模块负责搬文件
```

这样后续换外壳不会重写业务逻辑：

- 当前 Web 检测版：`app/`
- Android APK：Capacitor / WebView / 原生扫码插件
- Docker 网页版：HTTP 服务托管同一套 Web UI
- 其他扫码输入：只要能给出字符串 payload

## 当前目录边界

```text
core/
  inventory.js      # 物品表、库存计算、流水
  workflow.js       # 扫码上下文状态机
  scanner-port.js   # 外挂扫码输入端口
  catalog.js        # 物品新增/编辑
  filters.js        # 库存列表筛选
  snapshot.js       # 快照导入/导出
  merge.js          # 快照合并

app/
  index.html        # Web 外壳页面
  app.js            # Web 外壳编排
  storage.js        # 当前 Web 检测版 localStorage
  qr.js             # Web 里的二维码 SVG 渲染
  styles.css        # Web 样式

tools/
  index.html        # 测试工具
  tools.js          # 测试二维码生成
```

## 扫码边界

核心只接收 payload。

```js
window.StudioInventoryScanner.push("spool:PLA-BLK-001");
window.StudioInventoryScanner.push({ rawValue: "weight:712.4" });
```

扫码来源可以是任意一种：

- Android 原生扫码插件
- Web 相机扫码库
- USB/蓝牙扫码枪
- 手动输入
- 测试工具按钮

扫码库扫出来以后，只要把结果交给 `scanner-port.js`，后面都走同一套核心。

## Android 优先时怎么接

Android 壳需要做的事情：

1. 打开相机或扫码插件。
2. 得到字符串，例如 `spool:PLA-BLK-001`。
3. 调用 WebView 里的：

```js
window.StudioInventoryScanner.push(payload);
```

4. 核心处理库存，Web 外壳刷新显示。

Android 不应该直接改库存表，也不应该复制一套库存计算。

Android 原生层应该优先负责这些平台能力：

- 相机扫码和扫码速度优化。
- 文件读写、导入导出和本地数据库文件保存。
- Android 权限、系统分享、通知和后台任务。
- BLE 打印、设备连接和系统级错误处理。

这些能力完成后，只把稳定的 payload、文件内容或操作结果交给 Web/Core。固定库存逻辑仍然只在 `core/` 里维护。

## 记录

这份文档记录了当前架构判断：**核心负责数据和业务，外壳负责输入和展示。**

后续继续开发时，如果一个新功能不知道放哪里，先按这个问题判断：

```text
它是否依赖某个平台的 UI、文件、相机、蓝牙？
  是 -> 放外壳或 platform 适配层
  否 -> 放 core
```
