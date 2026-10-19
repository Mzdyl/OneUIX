# CMC 外拨失败诊断（2026-10-19 11:13 / 11:58）

## 现象

副设备（S24U，mock SD）用「远程设备 SIM1」拨 10001：

- 11:13:35 那通：通话停在 `DIALING`，从未 `ACTIVE`，`duration=0`，7 秒后自动结束。
- 11:58:45 那通（重启 imsservice 之后）：`duration=26`，双向 RTP 正常，有声音。

## 11:13 失败时的日志证据

主设备（S21+）侧同一时刻只有 P2P 通知，没有任何 CMC SIP 信令：

```
11:13:36.677  CphReceiver: WiFi [U<---] from (10.0.0.130:55806) ... "method":"INVITE"
11:13:36.706  CmcP2pHelperManager: handleP2pCommandForOutgoingState, mP2pSipHistories size: 0
11:13:36.706  CmcP2pHelperManager: CMC P2P SIP DELAY TIMER started, waiting time(s): 10
11:13:36.707  [OneUIX] Clamped CMC P2P SIP delay timer from 10000 ms to 4000 ms
11:13:40.818  Received EVT_CMC_P2P_SIP_DELAY_CHECK_TIMER
11:13:40.819  resetConnection, cmcType 1 handle 2354 phoneId 0
11:13:40.821  RegiMgr: deregister: task=RegisterTask[0][mProfile=SamsungCMC_PD ...]
```

对比 11:58 成功那通，主设备侧：

```
11:58:46.089  CMC P2P SIP DELAY TIMER started, waiting time(s): 10
11:58:46.135  SIPMSG[0,3]: [<--] INVITE sip:..._0_xsouoro8yd@...:8000 SIP/2.0
11:58:47.273  SIPMSG[0,2]: [-->] INVITE tel:1xxx1;phone-context=sd.ims.mnc011.mcc460.3gppnetwork.org
11:58:49.174  SIPMSG[0,2]: [<--] SIP/2.0 200 OK
11:58:49.548  SIPMSG[0,3]: [-->] SIP/2.0 200 OK      → 主设备回 200 给副设备
```

副设备侧同步发包：`11:58:45.872 [-->] INVITE sip:...@samsungims.com`。

结论：失败时**副设备根本没有发出 CMC SIP INVITE**，主设备等不到 SIP，只能靠 P2P 通知干等。

## 两个叠加因素

1. **SD 侧未发出 SIP INVITE**（主因）。11:12:29 主设备的 `SamsungCMC_PD` 因 `reason=41` 重新注册，11:12:30 才回到 `REGISTERED`；SD 侧在此之后拨号，仍然发不出 INVITE。重启两侧 `imsservice` 让双方重新注册、重新做 P2P 发现之后，同一套拨号流程立即恢复正常。
2. **4 秒 clamp 放大了故障**。`ImsService.kt` 里 09-26 加的「CMC P2P SIP 超时钳制到 4s」让主设备在 SIP 尚未到达时提前判定失败，执行 `resetConnection` 并**主动注销 `SamsungCMC_PD`**，把一次可重试的等待变成呼叫失败 + CMC 掉线重注册。已删除，恢复系统原生 10s。

注意：成功那通里 SIP INVITE 在 timer 启动后 46ms 就到达，所以 4s 本身不是成功与否的分界；它只是把「信令没到」的情况从「等 10 秒后失败」变成「4 秒后失败并拆掉注册」。

## 判断依据与检查步骤

1. 主设备 `logcat | grep -E "SIPMSG.*INVITE"`：失败时只有 P2P 的 `CphReceiver` 记录，没有 `SIPMSG` 的 INVITE。
2. 主设备 `logcat | grep -E "handleP2pCommandForOutgoingState|resetConnection"`：`mP2pSipHistories size: 0` 后紧跟 `resetConnection` 即为本条故障。
3. 副设备 `dumpsys telecom`：`Call[id=TC@xx, state=DIALING]` 长期不进入 `ACTIVE`，且没有 `CONNECTION_EVENT (EVENT_CALL_CMC_PRIMARY_DEVICE_CONNECTED_TIME)`。
4. 恢复手段：两侧 `killall com.sec.imsservice`，确认 `RegisterTask(s)` 里 `SamsungCMC_SD / SamsungCMC_PD` 均为 `REGISTERED`（RAT 18），再拨号。

## 待确认

- SD 侧发不出 INVITE 的确切触发条件。目前证据只能定位到「主设备 CMC 注册刚刷新过」这一时间相关性，怀疑 SD 侧缓存了旧的 UA 句柄 / 旧的 P2P 会话，尚未拿到失败时的 SD 侧 SIP 日志。
- 复现方式：让主设备 CMC 注册抖动一次（切飞行模式或重连 Wi-Fi），随即在副设备拨号，同时抓两侧 `SIPMSG`。
