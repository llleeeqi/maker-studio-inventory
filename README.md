# 个人工作室耗材与五金库存管理

一个面向个人工作室和小型创客空间的 Android 库存管理 App，适合管理 3D 打印耗材、螺丝和电子零件、工具及其他带标签物品。

项目以扫码为主要输入方式：先生成并打印物品标签，实际使用时扫码补充重量、数量和库位，再确认写入本地库存。

## 主要功能

- 扫码入库、更新重量或数量、绑定和整理库位。
- 管理耗材空盘重量、当前毛重和可用重量。
- 管理零件单重、总重和估算数量。
- 按物品或库位查看库存，并支持中文、拼音和模糊搜索。
- SQLite 本地存储，日常操作不依赖网络。
- 可选 WebDAV 多设备同步、冲突处理和全量备份恢复。
- 生成 40 x 30 mm 物品标签，并显示真实二维码预览。
- 虚拟货架测试台可在没有电子秤和标签机时模拟完整扫码流程。

## Android 下载

当前测试版本为 `0.5.2`，支持 Android 12 及以上版本。

- [GitHub Release v0.5.2](https://github.com/llleeeqi/maker-studio-inventory/releases/tag/v0.5.2)
- 通用 APK：`studio-inventory-native-0.5.2-universal.apk`
- arm64 APK：`studio-inventory-native-0.5.2-arm64.apk`

## 标签打印机

当前明确支持 **德佟 DeTong / DothanTech 的 LPAPI 蓝牙标签机**。项目已接入德佟 Android LPAPI，并验证了打印机搜索和连接。

德佟 DP23、DP30 系列的官方手册明确标注使用 LPAPI，可作为优先实测型号。其他德佟型号需要在购买前逐型号确认；普通蓝牙打印机、ESC/POS 小票机和其他品牌标签机目前不保证兼容。

详细兼容边界和真机验收状态见 [Android 构建与设备支持记录](./docs/17-android-build-and-device-support.md)。

## 虚拟货架

- 在线测试：[GitHub Pages](https://llleeeqi.github.io/maker-studio-inventory/tools/)
- 本地入口：[tools/index.html](./tools/index.html)

## 项目结构

| 路径 | 内容 |
|---|---|
| `mobile_android/` | 原生 Android App 主线 |
| `tools/` | 虚拟货架和二维码测试台 |
| `docs/` | 产品规则、协议、进度、构建和发版记录 |

## 文档

从 [docs/README.md](./docs/README.md) 开始查看完整文档。

- [当前进度](./docs/14-current-progress.md)
- [操作和按钮逻辑](./docs/09-operation-button-logic.md)
- [扫码工作流](./docs/01-qr-input-workflows.md)
- [二维码协议](./docs/12-v1-protocol-and-scope.md)
- [WebDAV 同步设计](./docs/16-webdav-sync-v1.md)
- [发版和项目管理](./docs/15-release-workflow-and-project-management.md)

项目仓库：[github.com/llleeeqi/maker-studio-inventory](https://github.com/llleeeqi/maker-studio-inventory)
