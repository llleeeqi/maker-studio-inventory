# 个人工作室耗材与五金库存管理

[English](./README.en.md)

这是一个面向个人工作室、创客空间和小型 3D 打印工作区的库存管理项目，用来管理 3D 打印耗材、五金零件、工具、备件和库位。

核心目标是把“识别物品、盘点余量、贴标签、移动库位、导出备份”这些琐碎动作做得足够快，适合一个人或小团队在真实工作台旁边反复使用。

项目不是单纯的二维码生成器。二维码只是入口，真正要解决的是：物品在工作室里流动时，用户能用扫码快速知道它是谁、在哪、还剩多少，并把变化记录下来。

在线二维码测试工具：

```text
https://llleeeqi.github.io/maker-studio-inventory/tools/
```

## 用途

适合管理这些东西：

- 3D 打印耗材卷：PLA、PETG、ABS、TPU 等。
- 小五金零件：热熔螺母、螺丝、轴承、连接件等。
- 工具、耗材、备件、盒子和货架库位。

典型动作：

- 扫物品码，快速确认物品身份和当前余量。
- 扫重量码和物品码，自动更新耗材余量或零件估算数量。
- 扫库位码和物品码，快速绑定新位置。
- 给新增物品生成标签，后续所有操作从扫码开始。
- 导出快照，后续做 WebDAV 同步或备份恢复。

## 核心理念

```text
扫码 -> 算出 -> 更新，尽量 10 秒内完成。
```

设计上优先保证：

- 离线可用：工作室没网也能查和记。
- 输入简单：扫码结果只是 `weight:` / `spool:` / `part:` / `location:` 这类字符串。
- 核心可复用：库存计算、状态机、快照合并都放在 `core/`，不绑死 Android 或浏览器。
- 同步外挂：本地先写成功，同步后续用 WebDAV 合并，不让网络阻塞盘点。
- 打印后置：标签打印是输出能力，不影响库存主流程。

## 当前形态

第一版已经跑通了核心闭环：

- 快捷扫码工作台。
- 耗材卷和零件目录。
- 库存搜索、筛选、低库存判断。
- 归档、恢复、克隆和自动 ID。
- 标签二维码预览。
- 流水记录。
- JSON 快照导入、导出、合并和预览。
- Web 检测版、Capacitor Android 壳和 Flutter Android 0.2.2 手机包。

当前可安装 Flutter APK：

[studio-inventory-flutter-0.2.2-arm64-release.apk](https://github.com/llleeeqi/maker-studio-inventory/releases/download/v0.2.2/studio-inventory-flutter-0.2.2-arm64-release.apk)

这个包使用 Flutter / Dart / Material 3 编写，Android 原生构建链路输出，包名为 `studio.inventory.mobile`，版本为 `0.2.2`。当前分发包是 arm64 release APK；debug APK 只用于本地调试。SHA-256：`9dc285cfaf57abe5caab7b3fc41b53b55dc31226d557622c166144da9ea4df90`。

长期推荐扫码协议：

```text
msi:v1;type=spool;id=PLA-BLK-001;name=黑色PLA;brand=Bambu;material=PLA;color=black;full_g=1200;tare_g=200;net_g=1000;created_on=260613
msi:v1;type=part;id=M3-SCREW-8-BLK;name=M3x8黑色圆头螺丝;category=screw;spec=M3x8;color=black;unit_weight_g=0.42;package_qty=100;created_on=260613
msi:v1;type=other;id=TOOL-001;name=热风枪;note=喷嘴套装;created_on=260613
msi:v1;type=location;id=RACK-A01;name=A架01格;created_on=260613
msi:v1;type=weight;value_g=712.4
```

设计规则：

- 二维码存固定档案，不存当前重量、数量、库位和出入库状态。
- 当前库存状态存在本地数据里，真正入库后才计入库存。
- 所有重量统一克，字段名带 `_g`。
- 所有实体标签带 `created_on=YYMMDD`。
- 库位可选，不阻塞入库。

旧短码继续兼容：

```text
weight:712.4
spool:PLA-BLK-001
part:M3-INSERT
location:RACK-A01
```

扫码输入边界：

```js
window.StudioInventoryScanner.push("spool:PLA-BLK-001");
window.StudioInventoryScanner.push({ rawValue: "weight:712.4" });
```

任何扫码来源只要能把 payload 交给这个入口，就能复用同一套库存流程。

## 结构图

```mermaid
flowchart LR
  User[用户<br/>扫码 / 编辑 / 导入导出] --> Shell[app/<br/>正式使用界面]
  User --> Flutter[mobile_flutter/<br/>Flutter Material 3 手机 app]
  Tools[tools/<br/>测试二维码] --> Shell
  Android[android/<br/>Capacitor APK 壳] --> Shell

  Flutter --> Scanner
  Shell --> Scanner[scanner-port<br/>扫码输入归一化]
  Scanner --> Workflow[workflow<br/>扫码状态机]
  Workflow --> Inventory[inventory<br/>库存计算 / 流水]

  Shell --> Catalog[catalog<br/>物品目录]
  Shell --> Filters[filters<br/>搜索与筛选]
  Shell --> Snapshot[snapshot<br/>快照导入导出]
  Snapshot --> Merge[merge<br/>快照合并]

  Shell <--> Storage[storage<br/>当前 localStorage]
  Core[core/<br/>平台无关核心] --> Scanner
  Build[build-web<br/>打包 app/core 到 www] --> Android
  Docs[docs/<br/>设计和排查记录]
```

## 目录

| 路径 | 作用 |
|---|---|
| `app/` | 正式使用界面，第一屏是扫码工作台 |
| `mobile_flutter/` | Flutter / Dart / Material 3 Android 手机 app |
| `core/` | 平台无关业务核心 |
| `tools/` | 测试二维码工具 |
| `android/` | Capacitor Android 壳 |
| `scripts/` | 构建辅助脚本 |
| `tests/` | 核心流程测试 |
| `docs/` | 设计、排查、后续开发记录 |

## 后续方向

近期：

- Flutter Android app 继续承接手机主入口，扫码、震动、声音、手电筒等高频能力优先走 Android 原生能力。
- 实现 `msi:v1` 协议、固定档案 Profile、当前状态 State、流水 Transaction。
- 手机本地 JSON 快照导出/导入。
- 扫码测试站生成 `spool / part / other / location / weight` 全字段测试码。

中期：

- WebDAV 快照同步。
- 标签打印 Web/JS SDK 验证页。
- 批量导入物品。
- 按库位/货架组织库存视图。
- 更细的合并冲突预览。
- 自然语言助手查询：“黑色 PLA 还剩多少？”“哪些东西低库存？”
- Docker 服务端 + PWA 渐进式路线调研，评估手机浏览器扫码、离线能力和性能边界。
- release 签名包和版本发布流程。

远期：

- Docker 服务端承载共享数据，手机和桌面通过 PWA 访问。
- 如果 PWA 扫码和离线性能不够，再保留 Android APK 作为高频扫码主入口。
- 多设备协作、权限和备份恢复流程。

## 文档

从 [docs/README.md](./docs/README.md) 开始看。

- [docs/00-project-map.md](./docs/00-project-map.md)：文件分工。
- [docs/01-qr-input-workflows.md](./docs/01-qr-input-workflows.md)：二维码输入流程。
- [docs/02-android-apk.md](./docs/02-android-apk.md)：Android APK 路线。
- [docs/03-data-and-sync.md](./docs/03-data-and-sync.md)：本地数据和同步。
- [docs/04-next-steps.md](./docs/04-next-steps.md)：下一阶段清单。
- [docs/05-catalog-management.md](./docs/05-catalog-management.md)：物品目录规则。
- [docs/06-inventory-filters.md](./docs/06-inventory-filters.md)：库存筛选。
- [docs/07-core-shell-boundary.md](./docs/07-core-shell-boundary.md)：核心和外壳边界。
- [docs/08-first-version-app.md](./docs/08-first-version-app.md)：第一版状态和验证。
- [docs/11-flutter-android-app.md](./docs/11-flutter-android-app.md)：Flutter Android 手机 app 路线和构建记录。
- [docs/12-msi-v1-and-0.2-scope.md](./docs/12-msi-v1-and-0.2-scope.md)：msi:v1 二维码协议和 0.2 手机闭环范围。
