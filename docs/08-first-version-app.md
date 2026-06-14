# 第一版正式 app 状态

## 当前结论

第一版正式 app 已经可以作为本地检测版使用。Web 入口是 `app/`，早期 Capacitor APK 是 `studio-inventory-debug.apk`；新的 Flutter 手机 release 包是 `studio-inventory-flutter-0.2.4-arm64-release.apk`。Flutter 版本使用 Material 3 和 Android 原生构建链路，第一屏是扫码工作台。

## 已接通的用户流程

扫码工作台：

- 查库存：扫 `spool:` 或 `part:` 后显示库存、低库存状态和库位。
- 盘点称重：扫 `weight:` + 物品码，顺序不限，自动更新耗材毛重或零件估算数量。
- 绑定库位：扫 `location:` + 物品码，顺序不限，自动写库位。
- 外挂扫码入口：`window.StudioInventoryScanner.push(payload)`。

库存和目录：

- 新增/编辑耗材卷和零件。
- 从库存列表载入物品编辑。
- 归档/恢复物品，不删除历史流水。
- 从已有物品克隆参数。
- 自动生成下一个可用 ID。
- 搜索、类型筛选、归档状态筛选、低库存筛选。

数据和标签：

- 物品标签二维码生成。
- 流水记录。
- 本地数据自动保存到 WebView 存储。
- 备份/调试入口可导出完整 JSON 快照。
- 备份/调试入口可覆盖导入快照。
- 备份/调试入口可合并导入快照，并在导入前显示远程新增、远程覆盖、本地保留和流水变化。

## 运行方式

APK：

```text
studio-inventory-flutter-0.2.4-arm64-release.apk
studio-inventory-debug.apk
```

重新构建：

```bash
npm run apk:debug
```

构建输出：

```text
mobile_flutter/build/app/outputs/flutter-apk/app-debug.apk
mobile_flutter/build/app/outputs/flutter-apk/app-release.apk
android/app/build/outputs/apk/debug/app-debug.apk
```

Web 检测版：

在项目根目录执行：

```bash
python3 -m http.server 8080
```

然后访问：

```text
http://127.0.0.1:8080/app/
```

测试二维码工具：

```text
http://127.0.0.1:8080/tools/
```

## 验证方式

核心测试：

```bash
for f in tests/*.mjs; do node "$f"; done
```

当前应输出：

```text
catalog tests passed
filters tests passed
merge tests passed
scanner-port tests passed
snapshot tests passed
workflow tests passed
```

浏览器冒烟检查：

1. 打开 `app/`，确认顶部显示本地物品数和流水数。
2. 在快捷扫码页点“重量 712.4g”和“黑 PLA 卷”，确认盘点称重模式可写流水。
3. 在查库存页搜索 `PLA`，确认列表筛选生效。
4. 载入一个物品，点“克隆”，确认新 ID 自动填入且库位/当前库存被清空。
5. 点“归档”，确认默认列表隐藏；切到“已归档”能看到。
6. 展开“备份和调试”，点“导出本地备份”，确认浏览器下载完整快照。

APK 构建检查：

```bash
cd mobile_flutter
/data/flutter/bin/flutter analyze
/data/flutter/bin/flutter test
/data/flutter/bin/flutter build apk --release --target-platform android-arm64

npm run build:web
npx cap sync android
cd android
./gradlew assembleDebug
/root/android-sdk/build-tools/36.0.0/apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
```

当前签名验证结果应包含：

```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
```

当前 Flutter release APK 还应包含：

```text
native-code: 'arm64-v8a'
```

## 已知边界

- 当前 Flutter 外发包是 arm64 release 构建，但仍使用 debug key 签名；后续要接正式 release 签名。
- Flutter app 当前还是检测版，库存规则先在 Dart 内实现，后续要和 `core/` 的快照/合并协议继续对齐。
- 存储仍是 `localStorage`，长期方案是 SQL.js + Capacitor Filesystem。
- 手动导入/导出只作为备份和调试入口；WebDAV 自动同步还未接。
- 标签目前只生成二维码预览，还未接精臣 BLE 打印。
- Web/Capacitor 路线目前通过手动输入、测试按钮或外部调用桥接；Flutter 路线已经接入相机扫码。
