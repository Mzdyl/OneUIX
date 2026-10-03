# 上游同步与真机检查（2026-10-03）

设备：10.0.0.130，SM-S9280，Android 16（API 36），One UI 8.5，Root / LSPosed 已启用。

## 上游同步

Self 已合入 upstream/main 的 b2c46dbf，包含 3 个新提交。合并提交为 f303695a。
保留 Original / Coexist、自有 Hook、中文/英文语言策略和旧配置迁移。
配置服务采用上游的异步等待机制；远程配置读取使用 statSize，并统一经过自有迁移解码器。

## 已修复

- MdecService 的 5 处服务器地址/账号认证参数修改，以及 Android FCM 的 1 处参数修改，改为复制参数数组后调用 proceed，避免修改 LibXposed 102 的只读列表。
- CMC 通话记录迁移等待用户解锁，并移至后台线程；原设备开机日志有 `Unknown URI content://call_log/calls`。此次验证了解锁后的启动，未重启手机复测首次解锁路径。
- S Pen 可选的 `com.samsung.sdk.clickstreamanalytics.internal.policy.Validation` 在本机固件不存在；缺失时跳过统计 SDK Hook，继续安装翻译 CSC Hook。
- NFC 应用与脚本拒绝 9/15 位等非完整字节 UID；SAK/ATQA 在进入 Root 命令前校验格式，模块部署失败时停止执行。
- NFC 状态不再仅凭残留卡号文件宣称“已生效”，而是比较系统与模块配置，并保留实体刷卡待验证提示。支持目录覆盖导致没有单文件挂载记录的情况。

## 验证结果

| 项目 | 结果与边界 |
| --- | --- |
| 构建 | Original / Coexist 的 Debug、Release 均成功，Release 的 lintVital 通过 |
| JVM 测试 | 每个 flavor 7 项通过：5 项配置迁移/备份测试，2 项 NFC UID 测试 |
| Android Shell 校验 | 3 个合法、4 个非法 UID 样例通过；配置匹配/不匹配两个状态分支通过，使用临时文件隔离测试 |
| 安装 | 同签名 Original Release 覆盖安装成功，保留应用数据；NFC CLI 同步更新并备份旧脚本 |
| 配置持久化 | UI 关闭“解除同一 WLAN 限制”，确认远程文件落盘，冷启动仍关闭；恢复原值后 JSON 与测试前当前配置完全一致 |
| S Pen | 冷启动翻译设置页成功，Google 选项可见；新日志未重现统计 SDK 类缺失错误。未进行实体 S Pen 选词翻译 |
| CMC | 冷启动设置页成功，显示平板副设备身份和“WLAN 或移动网络”；未观察到本模块初始化异常。未拨打电话、发送短信或测试双机 RTP |
| 手表 | 管理器冷启动成功，日志确认区域绕过及 Global 模式初始化；未重新配对实体手表 |
| 相册 / 三星健康 | 冷启动成功，测试窗口未发现本模块初始化异常；未验证云端同步或完整健康业务 |
| Quick Share | 设置页冷启动成功，Google 账号区及与 Apple 设备分享选项可见；未发送文件、上传联系人或改变可见性 |
| 状态栏 | 截图可见自定义时钟、温度、上下行网速；仅视觉抽查，未重启 SystemUI |
| NFC | 本机系统配置与模块配置内容一致；状态输出已正确区分配置检查与实际刷卡，未切换卡片或重启 NFC |
| Bixby | **未通过：自定义唤醒桥接存在版本兼容问题，见下文** |

## Bixby 未解决问题

本机 Bixby Agent 4.1.20.19、Voice Wake-up 4.1.30.18。
冷启动 Bixby 实验室后，“创建唤醒短语”入口可见，但新日志出现 12 处 Hook 初始化失败。
当前代码依赖旧版混淆类/方法，例如 `eh0.l0`、`ut.m`、`eh0.w`、`eh0.n`、`ek0.o`、`a51.o`、`no0.b.o`。
失败涉及唤醒词类型桥接、自定义短语桥接、训练管理器/回调和最终 ASR 分发；不能以菜单可见认定自定义唤醒可用。
此项需要针对新 APK 重新定位，宜改为特征或签名匹配，避免继续依赖固定混淆名称。本次未改写这条训练链路，也未录制或覆盖用户唤醒短语。

## 交付与限制

四种 APK 位于 `app/build/outputs/apk/{original,coexist}/{debug,release}/`。
本次更改已在本地提交，未推送远端。
未重启手机；system_server 中的 FCM 改动需重启后加载，开机首次解锁路径仍需回归。
原 APK、原 NFC CLI、旧版和当前实际配置、测试日志与 UI 证据保存在本机 `/tmp/oneuix-audit-20261003/`，其中包含设备私有信息，不纳入 Git。
本机 Gradle 环境变量原先指向离线磁盘，构建时临时使用 `GRADLE_USER_HOME=/Users/Mzdyl/.gradle`，未修改用户 Shell 配置。
