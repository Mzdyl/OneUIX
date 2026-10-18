# CMC 短信同步排查（2026-10-18）

## 设备与场景

| 设备 | 型号 | 地址 | 角色 | SIM |
|---|---|---|---|---|
| S21+ | SM-G9960 | 192.168.86.9 / ZeroTier 10.0.0.160 | PD（主设备） | 双卡：卡1 电信、卡2 CMCC |
| S24 Ultra | SM-S9280 | 10.0.0.130 | SD（副设备，被 OneUIX 伪装为 Tablet） | 单卡 |

触发背景：卡从 S24U（原双卡）挪到 S21 后，S24U 上的短信不再显示来源区分，并出现「收不到 / 很慢」。

## 结论摘要

1. **数据层与协议层正常**：主设备的 SIM 槽位在同步时被完整保留，卡1/卡2 在副设备上是 `sim_slot=0` / `sim_slot=1`。
2. **UI 层正常**：三星原生会按 CMC 主设备槽位绘制 SIM 图标；OneUIX 曾有一段「隐藏该图标」的 hook，但因字段名写错从未生效，已删除。
3. **真正的故障**：mdecservice 的 NMS `isMessageSyncEnabled` 会中途变成 `false`，此时所有消息停止推送并积压，直到进程重启才补拉。表现为「平时收不到、重启一次全冒出来」。
4. **主设备的 SMP push token 恒为 `null`**，但从「副设备能秒级收到推送」看，服务器是直接推给副设备的，因此它不是同步中断的主因，仍需单独处理。

## 数据层证据

`/data/data/com.samsung.android.messaging/databases/message_content.db` 的 `messages` 表相关字段：

- `sim_slot`：SIM 槽位
- `sim_imsi_id`：本机 SIM 的 IMSI 映射（`sim_imsi` 表），CMC 同步消息固定为 `0`
- `cmc_prop`：CMC 标记，取值 `syncedMessage` / `relayMessage`

对照实验（同一时段，卡1 与卡2 各一条）：

| 设备 | `_id` | `sim_slot` | `sim_imsi_id` | `cmc_prop` | 时间 |
|---|---|---|---|---|---|
| S21（源） | 22 | 0 | 1 | — | 15:59:22 |
| S21（源） | 23 | 1 | 2 | — | 16:05:42 |
| S24U（同步） | 3329 | 0 | 0 | syncedMessage | 15:59:22 |
| S24U（同步） | 3330 | 1 | 0 | syncedMessage | 16:05:42 |

`sim_imsi` 表映射：`1=460009…`、`2=460115…`、`3=460078…`、`4=454031…`；`sim_imsi_id=0` 表示非本机 SIM，即 CMC 来源。

结论：主设备槽位在同步链路中没有丢失。

## 协议层证据

`mdecservice` 反编译结果：

```java
// SmsDbEventRequest / MmsDbEventRequest
bundle.putInt(KEY_BUNDLE_SIM_INDEX,
    getSimProfileManager().hasDualSimInDualSimDevice() ? mRequestedSimSlot : -1);

// SimProfileManager
public boolean hasDualSimInDualSimDevice() {
    return NmsFeature.getPhoneCount() > 1 && getPresentSimCount() > 1;
}
```

PD 双卡时条件成立，槽位会随同步事件上报。`AttributeHelper` 解析时写入 `MessageAttribute.setSimIndex(...)`。

## UI 层证据

三星消息 `Yd.V.d(String, boolean)`：

```java
if (isCmc && CmcPdMultiSimManager.getSimCount() == 2
        && CmcFeature.isSupportPdCmcDualSimRegardlessOfSelectedSim()
        && CmcFeature.getSelectedSimSlotsOnPd() != null) {
    int slot = CmcPdMultiSimManager.getSimSlotByImsiForInfo(str);
    int iconIndex = CmcFeature.getSimIconIndexOnPd(slot);
    // 按槽位设置 SIM 图标
}
```

OneUIX 原先在 hook 之后把 SIM 徽标隐藏：

```kotlin
val simSlotImg = chain.thisObject.reflect["s"] as? View
simSlotImg?.visibility = View.GONE
```

实际字段为 `f10538q`（`base_list_item_info_cmc_sim_slot_img`），`reflect["s"]` 恒为 null，因此这段从未执行。已删除，避免将来混淆字段变化时它意外生效、反而遮住原生徽标。

## 同步中断证据

正常与异常的一次对照：

```
# 正常（副设备推送 1 秒到达）
16:05:42  S21  SMS 入库
16:05:43  S24U SPP_PUSH_RECEIVED  eventTypeMessage

# 异常（完全不推，直到重启）
16:30:53  S21  NMS2 requestToServer dataType=sms result=200
          S24U 无任何 push 记录
16:33:18  force-stop mdecservice 后
          onMessageSyncEnabled → isMessageSyncEnabled=true
          补拉写入 _id=3331（16:30:53）
```

状态对照：

```
# 中断时
ClientMgr: runningServiceMap={0=[], 1=[]} isMessageSyncEnabled=false isCalllogSyncEnabled=false

# 重启后
DatabaseMgr: onMessageSyncEnabled:
SubMgr<0>: enableMsgSync: enabledServiceSet=[calllog, message]
ClientMgr: runningServiceMap={0=[calllog, message], 1=[]} isMessageSyncEnabled=true
```

服务器侧 `mActiveServices=[calllog, message]` 一直存在，说明不是 entitlement 配置问题，而是客户端状态没有重新走 `initMessageSync()`。

## 推送 token 状况

```
S24U(SD): [SMP] get token : ...e281b (100)    正常，清数据后可重新注册
S21 (PD): [SMP] get token : null              始终为 null
```

`devices` 表中主设备登记的 `DEVICE_PUSH_TOKEN` 是 OneUIX `fixCmcPushToken` 写死在代码里的常量（`0601654e…c58680`），属于历史遗留值。副设备登记的 token 与自身 `SmpPref.pid` 一致。

## 已排除项

- **杀消息应用**：`am force-stop com.samsung.android.messaging` 后 12 秒内未出现 `onMessageSyncDisabled` / `deinitMessageSync`。
- **省电限制**：`am get-standby-bucket com.samsung.android.mdecservice` 返回 `5`（EXEMPTED）。
- **P2P 通道**：两端 `cmcp2phelper` 可见互相 reachable，主设备侧有 `CphReceiver: WiFi [U<---]` 入包记录。
- **默认短信应用**：S24U 的 SMS role holder 为 `com.samsung.android.messaging`（`sms_default_application` 为 null，走 role）。`NmsClientManager.filterService()` 在副设备上走 `isSmDefault=true` 分支，不会剔除 `message`。

## 根因与修复（已解决）

### 1. 网络模式设定确认
在 `CmcSettingManager.java` 内部枚举中：
- `0` = `NETWORK_MODE_USE_MOBILE_NETWORK`（WLAN 或移动网络）
- `1` = `NETWORK_MODE_WIFI_ONLY`（仅限 WLAN）
设备设置中的 `cmc_network_type = 0` 证明移动网络限制已正确解除（`isMobileDataRestricted = false`）。

### 2. 推送瘫痪与唤醒修复
- **副设备 (S24U)**：三星推送包 `com.sec.spp.push` 处于系统级 `stopped=true` 状态，系统丢弃一切后台广播，导致云端新消息通知根本无法唤醒副设备。
  - **修复**：在系统级拉起 `com.sec.spp.push/.PushClientService` 解除 `stopped` 状态并加入后台白名单（bucket 5 EXEMPTED），同时在 `MdecService.kt` 的 `Application.onCreate` 中加入常驻唤醒兜底，防止重启或被冻结后断联。
- **推送信标隔离**：优化 OneUIX `fixCmcPushToken`，优先使用本地已安装的 SPP 真实信标，杜绝在存在合法 SPP 服务时将无效的静态 Dummy Token 注入数据库。

### 3. NMS 状态机死锁与自愈修复
在 `NmsClientManager` 与 `EntitlementProfileManager` 中，网络微弱抖动会导致配置短暂切入 `DEACTIVATED` 并调用 `deinitMessageSync()` 关闭短信同步；随后重连时由于三星账户认证结果比对无变动直接返回，导致状态停滞在 `ACTIVE` 而非 `READYTOSYNC`，`isMessageSyncEnabled` 永久死锁为 `false`。
- **修复**：
  1. 在 `NmsDatabaseManagerBase.deinitMessageSync()` 上注入 Hook：当 CMC 仍处于激活状态时拦截注销，防止网络抖动误杀短信同步通道。
  2. 在 `NmsClientManager.isMessageSyncEnabled()` 上注入自愈 Hook：检测到 CMC 激活但标志位为 `false` 时自动复位为 `true`。
  3. 在 `EntitlementProfileManager.isReadyToSync()` 上注入保护 Hook：只要具备有效配置字典与网络条件即判定为就绪，打通重连链路。
