# 下一阶段开发清单

更新时间：2026-07-16

当前可测试版本为 `0.5.3`。已完成能力和实测状态见 `14-current-progress.md`，本文件只维护尚未完成的工作和优先级。

## 产品功能方向

- 开发类似思源 Docker 版的自托管服务，由一台常开的电脑进程或 Docker 容器运行。
- 服务端持有工作数据库、设备身份和同步状态，并通过现有 WebDAV 仓库与 Android 客户端同步。
- 浏览器是服务端界面，不直接把 WebDAV 当数据库，也不承诺离线独立工作；使用时必须能够连接自托管服务。
- 同一套 Web 界面同时服务电脑浏览器和 iOS PWA，减少原生 iOS App 的审核和发布成本。
- iOS PWA 使用 `getUserMedia` 调用摄像头扫码，必须在实体 iPhone 上验证权限、后台恢复和页面生命周期。
- 打印优先验证两条路径：服务端所在电脑通过德佟 Windows/PC Web 接口连接 USB 标签机；iOS PWA 调起德佟 App 并移交打印任务。
- 德佟公开资料目前没有发现 App 调起打印所需的 URL Scheme、Universal Link 或分享协议，该路径需要向厂商确认后再实现。
- 如果德佟 App 无法被外部调起，iOS PWA 仍可把打印任务提交给服务端，由连接标签机的电脑完成打印。

相关官方资料：

- [WebKit：iOS/iPadOS 主屏幕 Web App](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/)
- [WebKit：摄像头 getUserMedia](https://webkit.org/blog/7763/a-closer-look-into-webrtc/)
- [WebKit：暂不实现 Web Bluetooth 和 WebUSB](https://webkit.org/tracking-prevention/)
- [德佟 SDK 下载页](https://en.detonger.com/)
- [德佟 iOS 蓝牙打印接口](https://en.detonger.com/software/um/%E5%BE%B7%E4%BD%9F%E7%94%B5%E5%AD%90-%E8%93%9D%E7%89%99%E6%89%93%E5%8D%B0%E6%8E%A5%E5%8F%A3iOS%E4%BD%BF%E7%94%A8%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8-2021-05-08.pdf)
- [德佟 Windows USB 打印接口](https://en.detonger.com/software/um/%E5%BE%B7%E4%BD%9F%E7%94%B5%E5%AD%90-USB%E6%89%93%E5%8D%B0%E6%8E%A5%E5%8F%A3Windows%E4%BD%BF%E7%94%A8%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8-2021-05-08.pdf)
- 增加待打印标签清单：可以先生成并保存准备打印的标签，等物品、打印机或标签纸到位后集中打印。
- 待打印标签不算库存建档，不影响库存数量和库位；实际物品仍需扫码并完成入库后才写入库存。
- 接入能够显示或输出二维码的电子秤，继续保留手动录入重量作为备用方式。

## P0：实体设备验收

- 使用 40 x 30 mm 标签纸验证德佟 LPAPI 实际打印。
- 检查文本边距、二维码尺寸、Q 级纠错、浓度、走纸偏移和连续打印稳定性。
- 在小米 17 上复核透明状态栏、屏幕开孔、底部手势区、相机画面和软键盘。
- 在实体手机上检查大字体和较长中文库位名称，确保按钮和浮层不重叠。

## P1：多设备同步验收

- 使用两台实体 Android 设备连接同一个 WebDAV 仓库。
- 验证同时修改同一物品、设备改名冲突、云锁续期和离线恢复。
- 增加同步诊断摘要和可导出的故障报告，便于定位服务器兼容问题。
- 复核长期运行时索引、备份和日志不会无上限增长。

## P2：本地数据和搜索

- 库存规模明显增长后，将当前搜索索引改为增量更新。
- 评估从 SQLiteOpenHelper 迁移到 Room DAO，并保持现有数据库无损升级。
- 为导入、恢复和数据库升级补充更完整的异常诊断。

## P3：打印能力扩展

- 德佟 LPAPI 真机打印稳定后，整理已验证型号清单。
- 根据实际采购需求，再决定是否接入其他品牌 SDK 或 ESC/POS、CPCL 等协议。
- 只有具备实体设备和官方 SDK 时才承诺新型号支持，不根据“蓝牙可配对”判断兼容。

## P4：发布完善

- 配置正式 Android 签名和稳定的签名保管流程。
- 完善 Release 说明、APK 校验值和升级回滚记录。
- 根据实体设备结果决定下一个版本号和发布范围。

## 暂不进入近期计划

- 账号、成员和审批系统。
- 重新引入 Flutter/Capacitor 手机客户端。
- 自然语言库存助手。
