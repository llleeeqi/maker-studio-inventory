# 项目文件地图

## 当前目标

当前项目不是“二维码生成器”，而是 **二维码输入驱动的快捷库存管理软件**。

二维码生成只作为辅助：贴标签、造测试数据、验证输入协议。正式使用时，第一动作应该是扫码。

## 顶层结构

```text
工作室物品管理/
├── core/                # 平台无关核心
├── app/                 # Web 外壳 / 当前正式库存软件
├── mobile_flutter/      # Flutter / Dart / Material 3 Android 手机 app
├── tools/               # 测试工具
├── tests/               # 可在 Node 里跑的核心流程测试
├── docs/                # 后续开发记忆和设计说明
├── README.md            # 快速打开和当前状态
├── 架构决策.md          # 技术路线取舍
└── 项目大纲.md          # 原始大纲
```

## core/

`core/` 是平台无关核心。Android、Web、Docker 未来都应该复用它。

| 文件 | 职责 | 为什么这么分 |
|---|---|---|
| `inventory.js` | 数据模型、payload 解析、库存计算、流水写入 | 最核心业务，不能绑定 UI 或平台 |
| `catalog.js` | 新建/编辑物品目录 | 物品维护和扫码库存流程分开 |
| `filters.js` | 库存列表搜索、类型筛选、低库存筛选 | 筛选规则可被 Web、报表、助手复用 |
| `scanner-port.js` | 外挂扫码输入端口 | 不绑定任何扫码库，只把外部结果归一化成 payload 字符串 |
| `workflow.js` | 二维码输入状态机 | Android 原生扫码和网页手动输入都走同一套流程 |
| `snapshot.js` | 快照导入/导出和格式校验 | WebDAV 同步、手动备份、Docker 迁移都复用同一份快照协议 |
| `merge.js` | 本地快照和远程快照合并 | 同步策略独立出来，避免 UI、WebDAV、库存算法互相耦合 |

## app/

`app/` 当前是 Web 外壳，也是以后打进 Android APK 的 UI 入口。

| 文件 | 职责 | 为什么这么分 |
|---|---|---|
| `index.html` | 页面结构，四个主入口 | 保持 UI 入口清楚，后续套 Capacitor 时直接作为 Web 根 |
| `styles.css` | 移动优先样式 | 当前不引入 UI 框架，样式集中便于快速改 |
| `app.js` | 页面状态、渲染、原生扫码桥 | 只负责界面和平台入口，不直接定义库存流程规则 |
| `storage.js` | 当前检测版 localStorage 存取 | 隔离存储实现，未来替换 SQL.js/文件快照时不碰 `core/` |
| `qr.js` | 离线二维码 SVG 渲染 | 打标签和测试工具共用，不依赖外部 API |
| `capacitor.config.json` | Android 壳配置草案 | 后续 Capacitor 初始化时保留 app 名和 webDir 意图 |

## mobile_flutter/

`mobile_flutter/` 是新的手机 app 主线，使用 Flutter / Dart / Material 3 和 Android 原生构建链路。

| 路径 | 职责 |
|---|---|
| `lib/main.dart` | 当前 Flutter 单文件 app：扫码、库存、新增、流水四个主入口 |
| `android/` | Flutter 生成的 Android 工程，包名 `studio.inventory.mobile` |
| `test/widget_test.dart` | 首页和底部导航的 widget 冒烟测试 |
| `pubspec.yaml` | Flutter 依赖、版本号和 app 元数据 |

当前 0.2.4 手机安装包已经生成：

```text
studio-inventory-flutter-0.2.4-arm64-release.apk
```

这条路线的原则：手机端高频交互先用 Flutter Material 3 做成真 app；扫码、相机、震动、声音、手电筒等能力用插件或 Android 原生链路完成；库存规则后续再和 `core/` 做更严格的共享或协议对齐。

## tools/

`tools/` 是测试页，不是正式库存软件。

| 文件 | 职责 |
|---|---|
| `index.html` | 测试工具页面 |
| `tools.js` | 生成 `msi:v1` 测试二维码，并保留 `weight:` / `spool:` / `part:` / `location:` 短码兼容模式 |

保留它的原因：正式 app 要尽量少干扰，测试二维码、样例输入、压力测试都放工具页。

## tests/

| 文件 | 职责 |
|---|---|
| `workflow.test.mjs` | 验证查库存、盘点称重、绑定库位和未知物品边界 |
| `catalog.test.mjs` | 验证新增/更新物品目录和表单校验 |
| `snapshot.test.mjs` | 验证快照导出/导入、旧格式兼容和错误快照拦截 |
| `merge.test.mjs` | 验证快照合并的 updated_at 规则和流水去重 |
| `filters.test.mjs` | 验证搜索、类型筛选和低库存筛选 |
| `scanner-port.test.mjs` | 验证外挂扫码结果归一化和浏览器桥接入口 |

这些测试只覆盖平台无关逻辑，不依赖浏览器或 Android。后续改库存流程时先跑这里。
