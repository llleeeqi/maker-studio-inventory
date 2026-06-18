# 原生 Android 边界记录

## 当前理解

第一版不强行复用早期 JS `core/`，而是在原生 Android 里用 Kotlin 直接实现业务规则。

边界变成：

```text
Android UI / 相机 / 文件
  -> 提供扫码、输入、展示、保存能力

Kotlin 业务核心
  -> 解析 msi:v1
  -> 维护 items
  -> 计算库存
  -> 写 transactions
  -> 读写 JSON snapshot
```

早期 `core/` 和 `tests/` 作为参考资料保留，不再约束实现语言。

## 必须稳定的边界

这些必须和文档一致：

```text
msi:v1 payload 字段
本地 JSON 结构
入库/出库/库位整理规则
容量裁剪规则
撤销上一笔规则
```

这些可以随实现调整：

```text
Kotlin 类名
Compose 组件拆分
JSON 序列化库
CameraX 封装方式
状态管理方式
```

## 平台能力

原生 Android 层负责：

- CameraX 预览和 ML Kit 扫码。
- 相机权限和生命周期。
- 手电筒、震动、提示音。
- 本地 JSON 文件读写。
- 复制 payload 到剪贴板。
- 后续系统分享、导入导出和打印能力。

## 工具边界

`tools/` 仍然可以作为电脑端测试工具。

它可以生成和验证 `msi:v1` payload，但正式库存事实以 Android 本地 JSON 为准。
