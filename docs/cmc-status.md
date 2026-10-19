# CMC 跨设备能力现状（2026-10-19）

## 一页速览

| 能力 | 同 Wi-Fi | 跨网络（蜂窝 + Wi-Fi，ZeroTier 互通） | 依赖 |
|---|---|---|---|
| 使用远程 SIM 拨打电话 | ✅ 有声音 | ❌ 服务器不转发跨网 INVITE | 呼叫建立依赖三星 CMC 服务器转发，国区服务器只在同网场景下转发 |
| 接听打到远程 SIM 的电话 | ✅ | ❌（同上） | 同上 |
| 短信收发（双向同步） | ✅ | ✅ | 三星 NMS 云端 + SPP 推送，与局域网无关 |
| 用远程 SIM 发短信 | ✅ | ✅ | 同上；中继请求经云端对象的 push 触发主设备发送 |
| 通话记录同步 | ✅ | ✅ | NMS 云端 |

**当前唯一未解决项：跨网络接打电话。** 卡点在信令层 —— 副设备的 INVITE 到服务器后不再被转发（详见 [cmc-audio-path.md](cmc-audio-path.md)）。

## 短信为什么不受局域网限制

短信不经过 P2P，全程走三星的云：

1. 任一设备收到的短信 → `mdecservice` 写入 `MDMNbuffertable.db`（`sync_status` 由 `Updating` → `Posted`）→ 上传到 `nms-m0-cn1.mdc-prd.cn` 的对象存储；
2. 服务器向对端 `com.sec.spp.push` 发推送；
3. 对端 `mdecservice` 收到推送后按游标拉取增量，写入本地 `content://sms`。

用远程 SIM 发短信同理，只是方向反过来：副设备把待发内容写进云端对象（`CMC_OPEN_CMD_SEND_RELAY_SMS`），主设备收到 push 拉下对象、自己用 SIM 发出，再把状态写回云端，副设备靠推送收回执。

所以短信只要求两件事：

- 两端都能连上三星 CMC/NMS 服务器；
- 两端的 SPP 推送长连接是通的（`ss -tnp | grep 5229` 应为 `ESTAB`）。

一旦推送断掉，短信会「静默失效」而不报错 —— 见 [cmc-sms-relay-send-blocked.md](cmc-sms-relay-send-blocked.md) 里 mihomo 把 5229 抓进代理组那一例。

## 通话为什么离不开同网段

通话的媒体流不走云端，走设备之间的 P2P RTP：

```
副设备 saeCreateChannel(localIp, remoteIp, port)  →  主设备 sreCreateStream(...)
```

- 同 Wi-Fi：两端地址本来就能直连，hook 不再改写地址（`sameIpv4Subnet` 判定），音质与时延都正常；
- 跨网络：ZeroTier 解决了「发现与信令」，`Augmented P2P discovery host list` 会注入 `10.0.0.x`，主设备能收到 P2P 命令并正常拨号；
- 但主设备把蜂窝音频桥接成 RTP 的那一步走 `CmcP2pController`，本固件里 `mIsEnableP2pFeature = false`、`startP2pBind()` 是空实现，通道不会建立 → 主设备侧 `sreCreateStream` 调用次数为 0 → 副设备 `RTP RX 0` → `RTP Timeout(1401)` 挂断。

详细过程见 [cmc-audio-path.md](cmc-audio-path.md)。

## 环境前提（复现前先自查）

1. 两端 `SamsungCMC_PD` / `SamsungCMC_SD` 均为 `REGISTERED`（RAT 18），设备已解锁：
   `logcat | grep "RegisterTask(s)"`
2. 两端 `com.sec.spp.push` 的 5229 连接为 `ESTAB`（副设备若挂了代理，必须让 SPP 直连）。
3. 同网通话不要改写媒体地址；跨网通话需要 ZeroTier 双向可达。

## 判断故障位置的方法

| 现象 | 先看什么 |
|---|---|
| 通话无声 | `ApMediaStats` 的 `RTP RX Recvcount`；为 0 说明没有媒体流入，不是音量问题 |
| 拨号后很快失败 | 主设备是否有 `SIPMSG ... INVITE`；只有 P2P 命令没有 SIP 说明信令没到 |
| 短信不同步 | 两端 `NMS_push_message.xml` 游标、`ss` 里 5229 连接、buffer 表 `sync_status` |
| 短信发不出去 | 主设备 `content://sms` 是否有新行；没有则看 `SqliteWrapper.insert` 是否报错 |

## 文档索引

- [cmc-audio-path.md](cmc-audio-path.md) — 通话音频链路、同网/跨网差异、RTP 排查
- [cmc-call-failure-diagnosis.md](cmc-call-failure-diagnosis.md) — 拨号失败（SIP 未发出 + P2P 等待窗口）
- [cmc-sms-relay-send-blocked.md](cmc-sms-relay-send-blocked.md) — 远程发短信卡「发送中」、推送中断、SD 切换入口消失
- [cmc-sms-cursor-recovery.md](cmc-sms-cursor-recovery.md) — 推送游标丢失导致漏消息
- [cmc-sms-sync-2026-10-18.md](cmc-sms-sync-2026-10-18.md) — 早期短信同步排查记录
