# CMC 中继短信「发送中」与同步中断（2026-10-19）

## 结论先行

两个现象，两个独立原因，只有一个需要处理：

| 现象 | 原因 | 处理 |
|---|---|---|
| 主设备收到短信，副设备不同步 | 副设备 `com.sec.spp.push` 的 5229 长连接被代理规则抓走，一直 SYN-SENT，推送全断 | 改代理规则直连（环境侧，已生效） |
| 副设备远程发短信后长时间停在「发送中」 | 中继发送链路上出现过一次 AppOps NPE，**系统自带重试，下一次就成功** | 不改代码，属于偶发且可自愈 |

## 完整链路（12:04 与 12:08 两次）

副设备（`com.samsung.android.messaging`，12:04:53）：

```
SmsSender: [SEND]sendSms conversationId=5, recipient#=1, DeliveryReport=true
SmsSendMessage: [SMS] storeSendSmsQueue ... correlationTag = f2cd4b21a499fc16, cmcProp = relayMessage
SmsSendMessage: saved remoteId = 3263, localId = 3362
SmsSenderUpdate: updateSmsStatus : msgId = 3362, messageStatus = 1101
CmcOpenServiceWrapper: sendCommand : CMC_OPEN_CMD_SEND_RELAY_SMS
```

主设备第一次（12:04:54）：收到了中继请求，但在写 outbox 时被 system_server 挡下：

```
E DatabaseUtils: java.lang.NullPointerException: Attempt to invoke virtual method
  'boolean java.lang.Object.equals(java.lang.Object)' on a null object reference
  at android.permission.PermissionManager.resolveDeviceIdForPermissionCheck
  at com.android.server.appop.AppOpsService.getPersistentDeviceIdForOp
  at android.content.ContentProvider$Transport.enforceWritePermission
  at android.content.ContentProvider$Transport.insert
    ← com.samsung.android.messaging ... SqliteWrapper.insert
```

主设备第二次（12:08:51，同一 correlationTag，副设备重试触发）：

```
[IMS] MsgEventListenerModule: Device is PD, Relay requested Msg
[IMS] NmsMessageSender: sendRelaySMS()
CS/SmsSendMessage: storeSendSmsQueue ... cmcProp = relayMessage
TP/SmsProvider: insert content://sms/2488 succeeded      ← 这次成功
```

随后 `10001` 在 12:09:41 回了 `_id=2489`，说明短信确实到达对方。

## 关于那个 AppOps NPE

反编译设备 `services.jar` / `framework.jar`：

```java
public final String getPersistentDeviceIdForOp(int deviceId, int op) {
    int resolved = PermissionManager.resolveDeviceIdForPermissionCheck(
            this.mContext, deviceId, AppOpsManager.opToPermission(op));
    ...
}

public static int resolveDeviceIdForPermissionCheck(Context ctx, int deviceId, String permission) {
    if (deviceId != 0) {
        Set<String> s = DEVICE_AWARE_PERMISSIONS;
        if (!s.contains(permission)) return 0;   // permission 为 null 时 NPE
        ...
    }
}
```

`AppOpsManager.opToPermission(op)` 对没有对应权限的 op 返回 `null`，`Set12.contains(null)` 会调用 `null.equals(...)`，直接 NPE。只有 `deviceId != 0`（CMC 中继带虚拟设备 id）才会走到这一行，所以普通短信永远碰不到。

**但它是偶发的、且系统自己会恢复**：同一 correlationTag 的重试在 4 分钟后成功插入并发出。因此不为它加 system_server 级别的 hook —— 那类改动影响每次 AppOps 记录，收益不匹配风险。曾试过一版 hook（`AppOpsService.getPersistentDeviceIdForOp`），已经在 system_server 里加载不到 `services.jar` 的类而失败，代码已整体回滚。

## 真正需要修的：副设备收不到推送

12:09:41 主设备收到 `_id=2489`（buffer `sync_status=Posted`），副设备完全没有动静，`NMS_push_message.xml` 游标停在 `2026-10-18T20:28:11.922Z / index=17`。

```
SD: ss -tnp | grep 5229
SYN-SENT  ... [192.168.86.10]:35332 -> [52.81.140.35]:5229    (反复重连失败)
SD: ip route get 52.81.140.35
52.81.140.35 dev Meta table 2022 src 198.18.0.0                (走了 mihomo 的 TUN)
```

副设备上 root 级 `mihomo` 的 `/data/adb/akashaProxy/config.yaml` 里：

```
- DST-PORT,5228,💬FCM
- DST-PORT,5229,💬FCM      # 三星 SPP 长连接同样使用 5229
```

SPP 的 5229 长连接被丢进 FCM 代理组，经节点连不上三星推送服务器，`com.sec.spp.push` 全程 SYN-SENT，所有推送（含 CMC 短信同步）失效。

修复：在该规则之前插入直连

```
- PROCESS-NAME,com.sec.spp.push,DIRECT
- DOMAIN-SUFFIX,samsungosp.com,DIRECT
- DOMAIN-SUFFIX,samsungosp.com.cn,DIRECT
- DOMAIN-SUFFIX,samsungmdec.com,DIRECT
- DOMAIN-SUFFIX,mdc-prd.cn,DIRECT
```

热重载（`PUT /configs?force=true`）并重启 `com.sec.spp.push` 后，连接立刻变成 `ESTAB ...:5229`，副设备补拉回两条：

```
_id=3263  date=1792382938000  10001  type=2   （12:08:58 主设备发出的那条）
_id=3264  date=1792382981014  10001  type=1   （12:09:41 主设备的回复）
```

备份：`/data/adb/akashaProxy/config.yaml.bak-spp-20261019`。

结论：这是副设备的代理分流规则与三星推送端口冲突，不是模块代码问题；CMC 通话不受影响（走 P2P/Wi-Fi 直连）。

## 追加：SD「本机 / 远程」切换入口消失

副设备的 Messages 输入框里只剩底部一个 `📱中国电信`，没有切换本机与远程设备的入口。A/B 验证：

- 启用 `Messaging.hookSecondaryDeviceSms()`：只有 `📱中国电信`
- 注释掉该调用并重启 `com.samsung.android.messaging`：输入框右侧出现 `📱 若非的 S21+` 的设备胶囊

这组 hook 是 `6c458845` 带进来的，四处改写都在 `com.samsung.android.messaging` 内：

```
CmcFeature.getCmcDeviceType()          → 强制 DEVICE_TYPE_SD
C4.b.c()                               → 同上
CmcFeature.isCmcOpenSecondaryDevice()  → true
CmcFeature.isCmcOpenPrimaryDevice()    → false
CmcFeature.isCmcSdStandAloneSupport()  → false
FeaturesUtilImpl.isCmcSdStandAloneSupport() → false
```

`isCmcSdStandAloneSupport → false` 让 Messages 认为副设备的本机 SIM 不能独立发信，本机那一路就被从选择器里摘掉了，只剩远程。

移除后回归验证（12:49:50）：

```
ORC/SmsSender: [SEND]sendSms conversationId = 5, DeliveryReport = true
CS/SmsSendMessage: requestCmd, isForcePending = false, simSlot = 0, isCmcSend = true
CS/SmsSendMessage: storeSendSmsQueue ... cmcProp = relayMessage
CS/SmsSendMessage: saved remoteId = 3268, localId = 3379
CS/CmcOpenServiceWrapper: sendCommand : CMC_OPEN_CMD_SEND_RELAY_SMS
```

主设备插入 `_id=2493`，双方随后各自收到 10001 的回复（SD `3269` ↔ PD `2494`），中继链路与同步都正常。

结论：这组 hook 对当前固件是多余的，保留 `MdecService` 侧的 deviceType 设置即可。已整段移除 `Messaging.hookSecondaryDeviceSms()` 及 `Main.kt` 里的调用。
