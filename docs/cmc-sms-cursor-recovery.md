# CMC 短信同步：推送游标丢失导致漏消息（2026-10-19）

## 现象

主设备（S21+）收到了短信，副设备（S24U）一直收不到；重启副设备的 `mdecservice` 之后，积压的消息又会一次性冒出来。

## 定位过程

1. 两端推送 Token 正常：

   | | pushInfo | devices 表 |
   |---|---|---|
   | S21+ | `060181cbc0…` | 与自身记录一致 |
   | S24U | `0601dcdb…` | 与自身记录一致 |

2. 主设备缓冲表里该短信是 `Posted`（已上传云端），说明发送侧没问题。
3. 副设备 `content://sms` 里没有这条，缓冲表也没有对应行。
4. 关键在推送游标（`/data/user_de/0/com.samsung.android.mdecservice/shared_prefs/NMS_push_message.xml`）：

   ```
   <string name="date">2026-10-18T20:06:18.094Z</string>
   <int name="index" value="4" />
   ```

   重启后第一次收到推送时，索引日期被直接写成「当前时间」，而那条漏掉的短信时间在这之前。

5. 把游标手动退到 `2026-10-18T18:00:00.000Z`、`index=0` 后重启 `mdecservice`，副设备**立刻补拉到该短信**，确认根因在游标。

## 根因

`LostPushDetectorMessage.makeSearchOperation()` 依赖 `getLastReceivedPushTime()`：

```java
String lastReceivedPushTime = getLastReceivedPushTime();
if (TextUtils.isEmpty(lastReceivedPushTime)) {
    NMSLog.d(str, "No need to search");
    return;   // 游标为空时直接放弃补拉
}
```

而 `recordPushHistory()` 只有在 `isPushHistoryExist()` 为 true 时才会调用 `makeSearchOperation()`：

```java
if (isPushHistoryExist() && lastReceivedPushIndex != num - 1) {
    makeSearchOperation();       // 索引断裂才补拉
}
```

重启/换网后 SharedPreferences 被清空 → `isPushHistoryExist()` 为 false → 不触发补拉；紧接着把新的 index/date 写进去，游标之前的消息就被永久跳过。

## 修复

`hook/MdecService.kt` 里成对 hook `LostPushDetectorBase`：

- `isPushHistoryExist()`：结果为空（false）时强制返回 `true`，让 `recordPushHistory` 走索引断裂分支；
- `getLastReceivedPushTime()`：结果为空时返回「24 小时前」的 ISO 时间，作为补拉起点。

两者配合，重启或换网后的第一条推送就会触发一次 24 小时窗口的补拉。日志可确认：

```
OneUIX: empty push history, force catch-up search
OneUIX: LostPushDetector empty cursor, trigger catch-up search from 2026-10-17T20:10:46.824Z
```

## 备注

- 补拉只依赖推送到达；如果长时间完全没有推送，仍需要手动重启 `mdecservice` 触发。
- 该机制与 `docs/cmc-sms-sync-2026-10-18.md` 里记录的 `isMessageSyncEnabled` 死锁是两个独立问题，两者都已修复。
