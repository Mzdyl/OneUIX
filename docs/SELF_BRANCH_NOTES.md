# Self 分支维护笔记

本文档记录 `Self` 分支相对 `upstream/main` 的主要结构差异、自用功能和后续合并注意点。

## 上游同步基线

- 2026-07-20 审计基线：`upstream/main@bd3c9ebb`
- 审计时 `upstream/main` 是 `Self` 的直接祖先，不存在待合入的上游提交
- 后续状态以 `git fetch upstream` 和 `git log HEAD..upstream/main` 的实时结果为准

## 架构差异

### 设置模型拆分

`Self` 已将上游集中在 `Preference.Other` 的大量跨应用设置拆成按应用分组的偏好模型：

- `Preference.Gallery`
- `Preference.Notes`
- `Preference.Calendar`
- `Preference.Messaging`
- `Preference.Browser`
- `Preference.Video`
- `Preference.Weather`
- `Preference.ThemeCenter`
- `Preference.Launcher`
- `Preference.DualApp`
- `Preference.PhotoRetouching`
- `Preference.WatchPairing`
- `Preference.SamsungHealth`
- `Preference.HealthMonitor`
- `Preference.GalaxyStore`
- `Preference.SPen`
- `Preference.Bixby`

合并上游时，如果上游仍向 `Preference.Other` 添加字段，不要直接回退为上游结构。应把新字段迁移到对应应用分组，再同步 `hook/Main.kt` 和对应 `DetailPane*.kt`。

### UI 分类拆分

`Self` 将设置 UI 按目标应用拆分为多个 `DetailPane<目标>.kt` 文件，并通过 `SettingScreen.kt` 的 `Category` 分发到对应面板。

合并上游时，如果上游修改 `DetailPaneOther.kt`，通常需要把其中新功能手工迁移到对应拆分面板：

- Browser 功能迁移到 `DetailPaneBrowser.kt`
- Launcher 功能迁移到 `DetailPaneLauncher.kt`
- Galaxy Store 功能迁移到 `DetailPaneGalaxyStore.kt`
- Watch Pairing 功能迁移到 `DetailPaneWatchPairing.kt`
- S Pen 功能迁移到 `DetailPaneSPen.kt`
- 其余按 `Category.kt` 的应用分类归位

### SystemUI 时钟抽离

`Self` 将状态栏时钟自定义从 `SystemUI.kt` 抽离到 `hook/systemui/StatusBarClock.kt`。该模块包含：

- 自定义格式 `statusBarClockFormat`
- 秒级刷新 `updateStatusBarClockEverySecond`
- `{temp}`、`{lunar}`、`{rate}`、`{shichen}`、`{sec}`、`{date}` 占位符
- 使用 `WeakReference<TextView>` 避免持有 View 泄漏
- 对温度、农历、日期和 `DateTimeFormatter` 做缓存

合并上游 `SystemUI.kt` 时，不要把时钟逻辑重新塞回 `SystemUI.kt`。如果上游改了时钟相关 hook，需要迁移到 `StatusBarClock.kt`。

## 自用功能清单

### Bixby 自定义唤醒

核心文件：

- `app/src/main/java/io/github/soclear/oneuix/hook/Bixby.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneBixby.kt`
- `app/src/main/java/io/github/soclear/oneuix/data/Package.kt`
- `app/src/main/java/io/github/soclear/oneuix/data/Preference.kt`
- `app/src/main/java/io/github/soclear/oneuix/hook/Main.kt`

偏好项：

- `Preference.Bixby.injectModel`
- `Preference.Bixby.labsMgr`
- `Preference.Bixby.wwvBypass`

实现要点：

- `BIXBY_AGENT` 与 `BIXBY_WAKEUP` 两个包分别初始化
- `injectModel` 将 `Build.MODEL` 注入 Bixby 设备白名单缓存
- `labsMgr` 开启 `labs_custom_wakeup`
- `wwvBypass` 用方法签名匹配绕过唤醒词长度、黑名单和 locale 限制
- 自定义唤醒文本从 `SharedPreferencesImpl` 或 Bixby Wakeup XML 中读取，并带短期缓存
- 亚洲文字唤醒词通过 KWD 返回值修正，避免中文、韩文、日文文本训练失败

合并风险：

- 上游当前没有 `Bixby.kt`，合并时可能被删除或从 `Package.kt`、`Category.kt`、`SettingScreen.kt` 中丢失入口
- `Main.kt` 中 Bixby 分支必须保留 `Package.BIXBY_AGENT` 和 `Package.BIXBY_WAKEUP`

### Watch Pairing 区域绕过

核心文件：

- `app/src/main/java/io/github/soclear/oneuix/hook/WatchPairing.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneWatchPairing.kt`
- `app/src/main/java/io/github/soclear/oneuix/data/Preference.kt`
- `app/src/main/java/io/github/soclear/oneuix/hook/Main.kt`

偏好项：

- `Preference.WatchPairing.bypassRegionCheck`
- `Preference.WatchPairing.connectionMode`
- `Preference.WatchPairing.forceChinaGmsCore`

实现要点：

- `MODE_AUTO`、`MODE_WEAROS_CN`、`MODE_WEAROS_GLOBAL` 三种连接模式
- 绕过 `BluetoothUuidUtil.checkDeviceRegion`
- 按模式伪造 `GoogleRequirementUtils.isChinaEdition`
- 禁用 `PlatformUtils.isSamsungChinaModel`
- 将 `WEAR_OS_NOT_SUPPORTED_PHONE` 改为 `NO_PROBLEM`
- WearOS CN 模式可补充 `com.google.android.wearable.app.cn`
- 伪造 CSC 为 `TGY`

合并风险：

- 上游仍把 Watch Pairing 相关字段放在 `Preference.Other`
- 上游 `WatchPairing.init` 参数命名可能不同，合并后需要以 `Self` 的 `Preference.WatchPairing` 为准重新接线

### S Pen Google Translate

核心文件：

- `app/src/main/java/io/github/soclear/oneuix/hook/SPen.kt`
- `app/src/main/java/io/github/soclear/oneuix/hook/util/SamsungFeature.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneSPen.kt`

偏好项：

- `Preference.SPen.useGoogleTranslate`

实现要点：

- 伪装 `Validation.isChinaModel` 为 `false`
- 保持 `Validation.getCountryCode` 为 `CN`
- 通过 `SamsungFeature.overrideCscString` 精确覆盖翻译源相关 CSC key
- `useGoogleTranslate=true` 返回 `GOOGLE`，否则返回 `BAIDU`

合并风险：

- `SamsungFeature.kt` 是 `Self` 的通用 CSC/FloatingFeature 覆盖工具，上游当前没有该文件；合并时不要删除
- 上游如果修改 `SPen.kt`，需要保留对 `ConfigDefTranslatorSolution` 等翻译源 key 的窄范围处理

### CSC 和 Samsung Feature 覆盖工具

核心文件：

- `app/src/main/java/io/github/soclear/oneuix/hook/util/SamsungFeature.kt`

提供能力：

- `overrideCscString`
- `overrideCscBoolean`
- `overrideFloatingBoolean`

实现要点：

- 同时支持单参数和带默认值的 `getString` / `getBoolean`
- resolver 返回 `null` 时不干预原逻辑
- hook 失败时通过 `logError` 记录

合并风险：

- 该工具被 S Pen 等自用功能依赖。合并上游删除未引用文件时需要检查是否还有间接依赖

### Android 系统自用 Hook

核心文件：

- `app/src/main/java/io/github/soclear/oneuix/hook/Android.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneAndroid.kt`

偏好项：

- `disableAsksRestriction`
- `liftFcmNetworkLimit`
- `fcmFix`
- `hideNavigationBarGestureHint`

实现要点：

- `disableAsksRestriction` 清理 ASKS 受限包列表并禁用相关判断
- `liftFcmNetworkLimit` 使用上游实现，解除 `GmsAlarmManager` 的中国区和港版 FCM 网络限制
- `fcmFix` 在 `ActivityManagerService.broadcastIntentLocked` 中放宽 FCM 广播限制

合并风险：

- `allowGms` 已移除，和上游 `liftFcmNetworkLimit` 功能重叠时优先使用上游实现
- `fcmFix` 仍保留，因为它处理广播唤醒路径，不等同于 `liftFcmNetworkLimit`
- 上游新提交已加入 `allowAllRotation`，需要迁移到 `Preference.Android` 和 `DetailPaneAndroid.kt`

### SystemUI 自用 Hook

核心文件：

- `app/src/main/java/io/github/soclear/oneuix/hook/SystemUI.kt`
- `app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBarClock.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneSystemUI.kt`

当前 `Self` 额外关注：

- 自定义状态栏时钟格式和秒级刷新
- 状态栏电池温度、农历、刷新率等占位符
- 锁屏隐藏状态栏
- 电池图标宽高缩放
- 自定义运营商名称
- 实体 eSIM 适配
- 隐藏导航栏手势提示条由 Android 偏好侧管理

合并风险：

- 上游新提交包含电池电量文本、隐藏媒体 ongoing activity、禁用通知分组、更多 QS bar 项和 One UI 8.5 兼容修复
- 上游 `SystemUI.kt` 改动较多，合并时应优先保留 `Self` 的抽离边界，再把上游新增 hook 手工接入

### Coexist Flavor

核心文件：

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`

实现要点：

- `original`：`io.github.soclear.oneuix`
- `coexist`：`io.github.mzdyl.oneuix`

合并风险：

- 上游通常只维护原包名。合并构建脚本和 Manifest 时必须保留 `coexist` flavor、包名和相关组件声明

### 本地化策略

`Self` 当前只保留英文和中文，本地化上删除了法语和俄语。

合并风险：

- 上游当前重新新增 `values-fr/strings.xml` 和 `values-ru/strings.xml`
- 后续合并如果仍坚持只保留中英文，需要在解决冲突时删除这两个目录，并确保新增字符串同步到 `values/strings.xml` 和 `values-zh/strings.xml`

## 偏好读写容错

核心文件：

- `app/src/main/java/io/github/soclear/oneuix/hook/util/PreferenceProvider.kt`
- `app/src/main/java/io/github/soclear/oneuix/data/PreferenceCodec.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/PreferenceSerializer.kt`

实现要点：

- `Json` 开启 `ignoreUnknownKeys`
- 开启 `isLenient`
- 开启 `encodeDefaults`
- 开启 `coerceInputValues`
- `PreferenceCodec` 将上游旧版 `Preference.Other` 字段迁移到 `Self` 的应用分组
- 混合格式同时存在旧字段和新字段时，以新字段为准
- `PreferenceProvider.getPreferenceFile()` 不主动创建空文件，未修改过偏好时不启用默认 Hook
- `PreferenceSerializer.readFrom()` 对空文件、`{}` 和解析异常回退默认偏好

合并风险：

- 上游偏好字段频繁增删，必须同步更新 `PreferenceCodec` 的旧字段迁移映射
- 必须保留容错 JSON 配置，否则用户已有 `preference.json` 可能导致设置页或 hook 读取失败

## 上游功能核对

2026-07-20 审计确认此前记录的待合入功能均已进入 `Self`。后续每次合并仍需按“字段迁移规则”和“每个新功能的接线清单”核对冲突解决结果，不能仅依赖提交祖先关系。

## 合并操作建议

### 合并前检查

```bash
git status --short --branch
git fetch upstream
git log --oneline --no-merges HEAD..upstream/main
```

如果工作区有未提交改动，先 stash、提交或确认这些改动不在冲突路径内。

### 冲突文件优先级

优先解决这些文件：

1. `app/src/main/java/io/github/soclear/oneuix/data/Preference.kt`
2. `app/src/main/java/io/github/soclear/oneuix/hook/Main.kt`
3. `app/src/main/java/io/github/soclear/oneuix/hook/SystemUI.kt`
4. `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneSystemUI.kt`
5. `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneOther.kt`
6. `app/src/main/java/io/github/soclear/oneuix/ui/SettingScreen.kt`
7. `app/build.gradle.kts`
8. `app/src/main/AndroidManifest.xml`

### 字段迁移规则

上游新增字段迁移到 `Self` 的目标位置：

- `android.allowAllRotation` 保留在 `Preference.Android`
- `android.liftFcmNetworkLimit` 保留在 `Preference.Android`，用于替代已删除的 `allowGms`
- `settings.spoofPhoneStatusAsOfficial` 保留在 `Preference.Settings`
- `systemUI.statusBar.addBatteryLevelText` 等电池文本字段保留在 `Preference.SystemUI.StatusBar`
- `systemUI.qs.hideQsBarDataUsage` 保留在 `Preference.SystemUI.QS`
- `systemUI.other.disableNotificationGrouping` 保留在 `Preference.SystemUI.Other`
- `systemUI.other.hideOngoingActivityMedia` 和包名列表保留在 `Preference.SystemUI.Other`
- `other.redirectCustomTab` 应迁移到 `Preference.Browser`
- `other.watchPairing*` 应迁移到 `Preference.WatchPairing`
- `other.useSPenGoogleTranslate` 应迁移到 `Preference.SPen`

### 每个新功能的接线清单

合并上游新增功能时，至少确认：

1. `Preference.kt` 有字段且默认值合理
2. `hook/<Target>.kt` 保留上游实现，并兼容 `Self` 的工具函数
3. `hook/Main.kt` 在正确 package 分支中读取 `Self` 的字段路径
4. `ui/category/DetailPane<Target>.kt` 有设置项和事件
5. `SettingScreen.kt` 的分类分发仍完整
6. `values/strings.xml` 和 `values-zh/strings.xml` 有对应字符串
7. 如涉及新包名，`Package.kt`、`Category.kt`、Manifest queries 同步更新

### 构建验证

合并完成后至少执行：

```bash
./gradlew assembleOriginalDebug
```

如果改动涉及 coexist flavor 或 Manifest/package name，再执行：

```bash
./gradlew assembleCoexistDebug
```

## 不应丢失的文件

合并后确认这些文件仍存在：

- `app/src/main/java/io/github/soclear/oneuix/hook/Bixby.kt`
- `app/src/main/java/io/github/soclear/oneuix/hook/WatchPairing.kt`
- `app/src/main/java/io/github/soclear/oneuix/hook/SPen.kt`
- `app/src/main/java/io/github/soclear/oneuix/hook/util/SamsungFeature.kt`
- `app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBarClock.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneBixby.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneWatchPairing.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneSPen.kt`
- `app/src/main/java/io/github/soclear/oneuix/ui/component/DropdownItem.kt`

## 快速审计命令

```bash
git diff --name-status upstream/main...HEAD
git diff --check
rg -n "Bixby|WatchPairing|SamsungFeature|StatusBarClock|coexist|values-fr|values-ru" .
rg -n "preference\\.other\\.(useSPenGoogleTranslate|watchPairing|redirectCustomTab)" app/src/main/java
```

第二条 `rg` 应尽量没有结果；如果有，说明上游字段还没有迁移到 `Self` 的拆分偏好模型。
