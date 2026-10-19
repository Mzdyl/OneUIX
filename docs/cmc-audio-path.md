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

## 异地场景（一台蜂窝、一台 Wi-Fi，经 ZeroTier）

实测环境：S24U 关闭 Wi-Fi 走蜂窝（`rmnet_data0 = 10.48.148.94`），S21+ 保持 Wi-Fi（`192.168.86.9`），两端 ZeroTier 互通（`ping 10.0.0.160` 3/3，RTT ≈ 335ms）。

### 现象

```
SD: saeCreateChannel before: localIp=10.48.148.94, remoteIp=192.168.86.9
    saeCreateChannel after:  localIp=10.0.0.130,   remoteIp=10.0.0.160   ← 跨网段，改写生效
SD: bindToNetwork 100 -> null                                            ← 解除绑定生效
SD SAE: RTP TX 77 / RTP RX 0

抓包: SD → PD  10.0.0.130 → 10.0.0.160:51024  有包
      PD → SD  0 个包
PD: sreCreateStream 调用次数 = 0
PD: P2P Command received from urn:duid:5974757e...（SD 的 INVITE 已到达）
```

### 结论

**ZeroTier 只能解决「看起来在同一 WLAN」这一层**：

- 发现与信令确实被绕过了：`Augmented P2P discovery host list` 里注入了 `10.0.0.130`，主设备能收到 P2P 命令并正常拨号；
- 但主设备把蜂窝音频桥接成 RTP 流这一步，走的是 `CmcP2pController`，而该固件里 `mIsEnableP2pFeature = false`、`startP2pBind()` 是空实现，通道根本不会建立；
- 因此 PD 侧没有产生任何音频包，SD 收不到回程，`RTP Timeout(1401)` 挂断。

所以异地场景不是「地址错了」，而是「主设备没有流可发」。9-25 的会话里 PD 有 `sreCreateStream` 调用，说明当时该通道由另一条路径拉起；本固件上没有走到那条路径。

### 后续方向

1. 反编译 S21+ 的 `imsservice.apk`（与 S24U 不是同一版本），定位 CMC 媒体流真正的创建入口，确认能否 hook 出 `sreCreateStream` 同等效果；
2. 或放弃三星 P2P 通道，在副设备侧自行实现音频中继（把 VoLTE 音频经 ZeroTier 转发），工程量更大但不依赖固件实现。

在解决之前：**异地（不同网络）通话不可用，同网段（同 Wi-Fi）通话正常。**

## 跨网络通话：2026-10-19 的推进与卡点

### 已修好的两处

1. **音频引擎初始化从 20 秒缩到 3 秒**

   副设备跨网时默认网络是蜂窝，`PdnController.getDefaultNetworkBearer()` 返回 `0`（CELLULAR），
   native 的 `CallSession::SelectAudioInterface()` 看到 `pdn is internet` 就不肯立刻 `saeInitialize`：

   ```
   15:12  SelectAudioInterface(): Current pdn is internet, cmcType 2
   15:13:05  saeInitialize        ← 20 秒后，通话 10 秒早就结束了
   ```

   `ImsService.kt` 里按 `mdecDeviceType == 2 && hasVirtualLanInterface()` 把它改成返回 `1`（WIFI）后：

   ```
   15:22:41  呼叫
   15:22:44  saeInitialize        ← 3 秒
   15:22:44  saeCreateChannel after: localIp=10.0.0.130, remoteIp=10.0.0.160
   ```

2. **主设备开始回包**

   主设备的默认网络也是蜂窝，媒体 socket 绑在 `rmnet_data0`（116/118）上，发往 `10.0.0.x` 的包出不去：

   ```
   PD: bindToNetwork() 118 → ResipMediaHandler: bindToNetwork : 118
   ```

   把解绑条件从 `isSd || isWifiNetwork` 扩成「只要 `zt*` 接口存在就解绑」后：

   ```
   PD: bindToNetwork 118 -> null (virtual LAN not in ConnectivityManager)
   ```

   抓包确认主设备开始发包：`10.0.0.160.48316 > 10.0.0.130.51024`。

### 卡点：包到了接口，没到 socket

两端同时抓包，副设备 `ztklh7akuw` 上确实收到主设备的 RTP：

```
15:35:28.544  IP 10.0.0.160.47508 > 10.0.0.130.51024: UDP, length 368   （每 200ms 一个）
```

但副设备那个 socket（`imsservice` 的 `*:51024`，inode 2992382）计数纹丝不动：

```
/proc/net/udp6: ...:C750  st 07  tx_queue:rx_queue 00000000:00000000  ...  drops 0
```

`rx_queue` 和 `drops` 都是 0 —— 内核层面既没排队也没记丢弃，包没有交给这个 socket。

已排除：

- 端口：`saeCreateChannel` 传进来的端口是占位值 `1234`，真实端口由 native 管理，不是端口不匹配
- iptables：`mangle`/`nat` 都没有 TPROXY / REDIRECT，mihomo 走的是 `ip rule` 的 uidrange，不拦入站 UDP
- 路由表：把 `10.0.0.0/24 dev ztklh7akuw` 加进全部路由表（rmnet_data0/1、*_local、swlan0、1014/1049/2022）后依旧 `RX 0`，已回退

### 下一步方向

`ztklh7akuw` 是 root 版 ZeroTier 直接建的 tun 接口，不在 `ConnectivityManager` 里
（`virtualLanNetwork()` 探测到的网络只有 `wlan0`/`rmnet_*`），媒体 socket 没法绑定到一个"包含 zt 接口"的网络。
很可能正是这一点让 SAE 的接收路径不认这些包。

所以首选方案：**让 ZeroTier 以 VPNService 形式运行**（官方 ZeroTier App / Tailscale 这类会注册成 Android 网络），
这样 `ConnectivityManager` 能看到它，现有的 `virtualLanNetwork()` 分支就能把 socket 绑过去，收发走同一条路径。

备选：native 层 hook SAE 的 socket，或在副设备自建音频中继（工程量大，不依赖固件）。
