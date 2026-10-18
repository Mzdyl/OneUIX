# CMC 跨设备通话音频链路（2026-10-19）

## 现象

副设备（S24U，mock SD）用 CMC 账户外拨时：

- 信令完全正常：`START_OUTGOING_CALL requestedAccountHandle=...CMC_0` → `REQUEST_START_CONNECTION targetPhoneAccount=CMC_0`
- SIP 正常：`INVITE → 100 Trying → 180 Ringing → 200 OK → ACK`
- 主设备（S21+）也确实用 SIM 卡拨了出去（`PLACE_OUTGOING_CALL tpac=...1`）
- 但 10 秒后副设备挂断：`RTP Timeout(1401)`

SAE 统计是判断依据：

```
#ApMediaStats RTP TX: sendcount[324] sendFailCount[0]
#ApMediaStats RTP RX: Recvcount[0]                      ← 一个包都没收到
```

## 根因

### 1. 媒体地址被无条件改写成 ZeroTier（主因）

`ImsService` 的 RTP hook 在 `saeCreateChannel` / `sreCreateStream` 里把本地/远端地址替换成虚拟局域网 IP：

```
saeCreateChannel before: localIp=192.168.86.10, remoteIp=192.168.86.9
saeCreateChannel after:  localIp=10.0.0.130,   remoteIp=10.0.0.160
```

两台设备本来就在同一个 Wi-Fi 子网（`192.168.86.0/24`），原始地址完全可达。改写之后：

- RTP 从 ZeroTier 网卡发出；
- 音频引擎的 socket 绑定在默认网络（Wi-Fi）上；
- 回程包（`10.0.0.160 → 10.0.0.130:51024`）虽然到达网卡，却进不了该 socket。

结果就是「发得出去、收不回来」。

### 2. `ConnectivityManager` 不认识 ZeroTier

```
virtual LAN probe -> network=null, nets=102:iface=wlan0, 115:iface=rmnet_data1
```

ZeroTier 是 root 直管的接口，没有注册成 `VpnService` 网络，因此不存在可用于 `bindToNetwork` 的 `Network` 对象。想靠「把 socket 绑到 ZeroTier 网络」解决是走不通的。

### 3. 对端地址可能带脏字符

旧版实现直接返回 preference 原值，`virtualLanPeerIp` 一旦是 `o10.0.0.130`，就会把非法地址写进流参数：

```
sreCreateStream after (CMC Stream): localIp=10.0.0.160, remoteIp=o10.0.0.130
```

现在统一经过 `PeerAddressCache.validAddress()` 过滤，非法值会被丢弃并回退到自动探测。

## 修复

1. `ImsService.kt` 增加 `sameIpv4Subnet()`：**同网段时保持原始地址不动**，只有跨网段才启用 ZeroTier 改写；`saeCreateChannel` / `sreCreateStream` 两处都生效。
2. `bindToNetwork` 不再盲目置 `null`：优先绑定虚拟局域网网络，取不到时保持原值（蜂窝 VoLTE 流不受影响）。
3. 外拨 CMC 由 `enableCmcOutgoingCall` 开关控制（默认关闭，关闭时外拨回落本机 SIM）。该固件上部分场景仍会「拨通但无声」，所以保留为实验项。

## 验证

修复后同一次通话：

```
OneUIX: saeCreateChannel same LAN, keep 192.168.86.10/192.168.86.9
#ApMediaStats RTP TX: sendcount[324]
#ApMediaStats RTP RX: Recvcount[1279] lastRecvd[6]msecs
```

双向音频恢复，且拨号账户仍是 `CMC_0`。

## 排查手法（下次直接复用）

1. `dumpsys telecom | grep START_OUTGOING_CALL` —— 确认账户是 `CMC_0` 还是本地 SIM。
2. `logcat -d | grep "ApMediaStats"` —— 只看 `RTP RX` 是否为 0，这是「有没有声音」的硬指标。
3. `logcat -d | grep -E "saeCreateChannel|sreCreateStream|same LAN"` —— 确认 hook 是否真的被调用（探针 `media hook probe -> matched=N`）。
4. `tcpdump -i any -n -q 'udp and <过滤噪音>'` —— 确认包到底有没有到达网卡、目的地址是 Wi-Fi 还是 ZeroTier。
5. 测试前必须确认两端 `SamsungCMC_PD/SD` 都是 `REGISTERED`；`killall com.sec.imsservice` 后 CMC 副线会掉到 IDLE，需要等它自己重注册（锁屏状态下不会注册）。

## 已知问题

首次外拨存在概率性失败：副设备侧 `PLACE_OUTGOING_CALL tpac=CMC_0` 之后约 5 秒直接 `LOG_DISCONNECTED_CALL`，主设备完全没有 `START_OUTGOING_CALL`（没有 fork 过去），SD 侧 SIP 里也没有 INVITE。第二次拨打通常正常。

该现象在 9-26 的版本上同样存在（当时记录为「第一拨不通，第二次才拨通」），与本仓库的 hook 无关，怀疑与 CMC 云端（`nms-m0-cn1.mdc-prd.cn` / `ssc-cn1`）的 fork 下发时机或主设备 CMC 线的活跃状态有关。可在换卡、换网或主设备重启后复现。

## 结论速记

- 同 Wi-Fi 场景：**不要再改写媒体地址**，直接用 `192.168.86.x` 直连，音质与时延都正常。
- 跨网（只靠 ZeroTier）场景：尚未打通，ZeroTier 接口不是 `ConnectivityManager` 网络，音频引擎的 socket 收不到该接口的回程包。
- 判断「有没有声音」只看 `ApMediaStats` 的 `RTP RX Recvcount`，不要凭界面状态判断。
