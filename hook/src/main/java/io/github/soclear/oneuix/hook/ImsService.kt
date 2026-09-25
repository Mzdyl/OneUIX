package io.github.soclear.oneuix.hook

import android.content.Context
import android.os.Bundle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

object ImsService {
    private val cachedPeerIps = ConcurrentHashMap.newKeySet<String>()

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun handleHooks(
        enableVirtualLanP2p: Boolean = false,
        virtualLanPeerIp: String = "",
        useChinaCmcServer: Boolean = false,
        bypassSameWifiRestriction: Boolean = false,
        unlockMobileNetwork: Boolean = false
    ) {
        if (param.packageName != Package.IMS_SERVICE) return
        val classLoader = param.classLoader

        val isChina = useChinaCmcServer || try {
            val sysProp = Class.forName("android.os.SystemProperties")
            val getMethod = sysProp.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, "ro.csc.countryiso_code", "") as? String)?.equals("CN", ignoreCase = true) == true
        } catch (_: Throwable) {
            false
        }

        // 1. 国行服务器中继协议声明：单独 Hook getCmcRelayType 返回 "priv-p2p"，不污染 isSupportSameWiFiOnly
        if (isChina) {
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.cmc.CmcAccountManager")
                clazz.declaredMethods.filter { it.name == "getCmcRelayType" }.forEach { method ->
                    xposedModule.hook(method).intercept { "priv-p2p" }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.handler.secims.CmcProfile")
                clazz.declaredMethods.filter { it.name == "getCmcRelayType" }.forEach { method ->
                    xposedModule.hook(method).intercept { "priv-p2p" }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.cmc.CmcAccessTokenStorage")
                clazz.declaredMethods.filter { it.name == "getServerUrl" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val orig = chain.proceed() as? String
                        if (orig.isNullOrEmpty() || !orig.contains(".cn")) {
                            "cn-auth2.samsungosp.com.cn"
                        } else {
                            orig
                        }
                    }
                }
                val tokenClass = classLoader.loadClass("com.sec.internal.ims.core.cmc.CmcAccessTokenStorage\$CmcAccessToken")
                val tokenCtor = tokenClass.getConstructor(String::class.java, String::class.java)
                val getTokenMethod = tokenClass.getDeclaredMethod("getToken")
                val getUrlMethod = tokenClass.getDeclaredMethod("getUrl")
                clazz.declaredMethods.filter { it.name == "update" && it.parameterTypes.size == 1 }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val tokenObj = chain.args[0]
                        if (tokenObj != null) {
                            val url = getUrlMethod.invoke(tokenObj) as? String
                            if (url.isNullOrEmpty() || !url.contains(".cn")) {
                                val token = getTokenMethod.invoke(tokenObj) as? String ?: ""
                                chain.args[0] = tokenCtor.newInstance(token, "cn-auth2.samsungosp.com.cn")
                            }
                        }
                        chain.proceed()
                    }
                }
                clazz.declaredMethods.filter { it.name == "initFromPref" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        chain.proceed()
                        try {
                            val storage = chain.thisObject
                            val currentTokenObj = storage?.reflect?.get("mCmcAccessToken")
                            val token = currentTokenObj?.reflect?.call("getToken") as? String ?: ""
                            val url = currentTokenObj?.reflect?.call("getUrl") as? String ?: ""
                            if (!url.contains(".cn")) {
                                val newToken = tokenCtor.newInstance(token, "cn-auth2.samsungosp.com.cn")
                                storage?.reflect?.set("mCmcAccessToken", newToken)
                                storage?.reflect?.call("updatePref")
                                xlog("OneUIX: CmcAccessTokenStorage corrected SA URL to cn-auth2.samsungosp.com.cn in pref")
                            }
                        } catch (t: Throwable) {
                            xlog(t)
                        }
                        null
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.cmc.CmcSAServiceImpl")
                clazz.declaredMethods.filter { it.name == "handleAccessTokenSuccess" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val bundle = chain.args.getOrNull(1) as? Bundle
                        if (bundle != null) {
                            bundle.putString("api_server_url", "cn-auth2.samsungosp.com.cn")
                            bundle.putString("auth_server_url", "cn-auth2.samsungosp.com.cn")
                        }
                        chain.proceed()
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.cmc.CmcAccountManager")
                clazz.declaredMethods.filter { it.name == "getCmcRegiConfigForUserAgent" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val bundle = chain.proceed() as? Bundle
                        bundle?.putString("SA_SERVER_URL", "cn-auth2.samsungosp.com.cn")
                        bundle
                    }
                }
            }.onFailure { xlog(it) }
        }

        // 2. 解除同一 Wi-Fi 限制
        if (bypassSameWifiRestriction || enableVirtualLanP2p) {
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.cmc.CmcAccountManager")
                val method = clazz.getDeclaredMethod("isSupportSameWiFiOnly")
                xposedModule.hook(method).intercept { false }
            }.onFailure { xlog(it) }

            // 统一主副设备 CMC PANI（i-wlan-node-id / BSSID）：
            // 国行 TAS 服务器强制要求主副设备处于“同一 Wi-Fi”才允许 CMC 呼叫，其服务端判定依据即为 SIP 头域中的 P-Access-Network-Info (PANI)。
            // 若副设备走 5G 蜂窝数据（或两端处于不同 Wi-Fi），副设备上报 3GPP-NR-TDD 或不同 BSSID，TAS 会直接返回 403 Forbidden [Server relay is restricted]。
            // 拦截 PaniGenerator.generate，当 profile 为 CMC（cmcType != 0）时，统一返回基于主设备 ID 生成的一致虚拟 WLAN PANI。
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.handler.secims.PaniGenerator")
                val method = clazz.declaredMethods.firstOrNull {
                    it.name == "generate" && it.parameterTypes.size == 4 && it.parameterTypes[3].name.endsWith("ImsProfile")
                }
                if (method != null) {
                    xposedModule.hook(method).intercept { chain ->
                        val profile = chain.args[3]
                        val cmcType = profile?.reflect?.call("getCmcType") as? Int ?: 0
                        val name = profile?.reflect?.call("getName") as? String ?: ""
                        if (cmcType != 0 || name.startsWith("SamsungCMC", ignoreCase = true)) {
                            val virtualBssid = getVirtualWlanBssid(classLoader)
                            val pani = "IEEE-802.11;i-wlan-node-id=$virtualBssid"
                            xlog("OneUIX: Overriding CMC PANI ($name, type=$cmcType): $pani")
                            pani
                        } else {
                            chain.proceed()
                        }
                    }
                }
            }.onFailure { xlog(it) }

            // 呼叫路由保障：当副设备发起呼叫时，确保主设备 (PD) 的 SIP URI 必然存在于 p2p 目标列表中
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.servicemodules.volte2.CmcServiceHelper")
                val regClass = classLoader.loadClass("com.sec.ims.ImsRegistration")
                val profileClass = classLoader.loadClass("com.sec.ims.volte2.data.CallProfile")
                val method = clazz.getDeclaredMethod("updateCmcP2pList", regClass, profileClass)
                xposedModule.hook(method).intercept { chain ->
                    chain.proceed()
                    try {
                        val profile = chain.args[1]
                        val reg = chain.args[0]
                        val imsProfile = reg?.reflect?.call("getImsProfile")
                        val cmcType = imsProfile?.reflect?.call("getCmcType") as? Int ?: 0
                        if (cmcType == 2 || cmcType == 4 || cmcType == 8) {
                            @Suppress("UNCHECKED_CAST")
                            val p2pList = (profile?.reflect?.call("getP2p") as? List<String>)?.toMutableList() ?: mutableListOf()
                            val imsRegistryClass = classLoader.loadClass("com.sec.internal.ims.registry.ImsRegistry")
                            val accountMgr = imsRegistryClass.getDeclaredMethod("getCmcAccountManager").invoke(null)
                            val cmcInfo = accountMgr?.reflect?.get("mCmcInfo")
                            val lineId = cmcInfo?.reflect?.get("mLineId") as? String
                            val ownerDevId = (cmcInfo?.reflect?.get("mLineOwnerDeviceId") as? String)
                                ?: (accountMgr?.reflect?.call("getCurrentLineOwnerDeviceId") as? String)
                            if (!lineId.isNullOrEmpty() && !ownerDevId.isNullOrEmpty()) {
                                val gr = if (ownerDevId.startsWith("urn:duid:")) ownerDevId else "urn:duid:$ownerDevId"
                                val pdUri = "sip:$lineId@samsungims.com;gr=$gr"
                                if (!p2pList.contains(pdUri)) {
                                    p2pList.add(0, pdUri)
                                    profile?.reflect?.call("setP2p", p2pList)
                                    xlog("OneUIX: Ensured PD URI in CallProfile p2p list: $p2pList")
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        xlog(t)
                    }
                }
            }.onFailure { xlog(it) }
        }

        // 3. 解除移动网络限制（蜂窝数据 5G/4G 下注册与呼叫）
        if (unlockMobileNetwork || enableVirtualLanP2p) {
            // 解除 Wi-Fi Only 设置（国行 ROM 默认隐藏该开关且为 true）
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.cmc.CmcAccountManager")
                val method = clazz.getDeclaredMethod("isWifiOnly")
                xposedModule.hook(method).intercept { false }
            }.onFailure { xlog(it) }

            // 允许在蜂窝网络（5G/LTE，RAT != 18）下注册 CMC
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.RegistrationGovernorCmc")
                clazz.declaredMethods.filter { it.name == "isReadyToRegisterOnMobileNetwork" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val rat = chain.args[0] as? Int ?: 0
                        if (rat == 18) {
                            chain.proceed()
                        } else {
                            true
                        }
                    }
                }
            }.onFailure { xlog(it) }

            // 确保 CMC 配置文件在 LTE (13) 和 NR (20) 下均开启 mmtel 语音服务集
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.core.cmc.CmcProfileManager")
                clazz.declaredMethods.filter { it.name == "updateProfile" || it.name == "makeProfileMap" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val map = chain.thisObject?.reflect?.get("mProfileMap") as? Map<*, *>
                            map?.values?.forEach { profile ->
                                for (rat in listOf(13, 18, 20)) {
                                    profile?.reflect?.call("setServiceSet", rat, setOf("mmtel"))
                                    profile?.reflect?.call("setNetworkEnabled", rat, true)
                                }
                            }
                        } catch (t: Throwable) {
                            xlog(t)
                        }
                        result
                    }
                }
            }.onFailure { xlog(it) }
        }

        // 3. 虚拟局域网（ZeroTier / WireGuard / VPN）P2P 跨公网呼叫支持
        if (enableVirtualLanP2p) {
            // 放行底层 Wi-Fi Direct 特性开关（三星硬编码返回 false）
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.imsphone.cmc.CmcP2pController")
                val method = clazz.getDeclaredMethod("isEnabledWifiDirectFeature")
                xposedModule.hook(method).intercept { chain ->
                    if (hasVirtualLanInterface()) true else (chain.proceed() as? Boolean ?: false)
                }
            }.onFailure { xlog(it) }

            // 放行 Wi-Fi 连通性判断：当虚拟网卡存在时，即使断开物理 Wi-Fi 也认为网络可用
            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.cmcp2phelper.utils.P2pUtils")
                val method = clazz.getDeclaredMethod("isWifiConnected", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed() as? Boolean ?: false
                    if (result) true else hasVirtualLanInterface()
                }
            }.onFailure { xlog(it) }

            // 监听套接字通配绑定 (0.0.0.0) 与本地虚拟 IP 广播
            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.cmcp2phelper.utils.P2pUtils")
                clazz.declaredMethods.filter { it.name == "getIpAddress" || it.name == "getIPv4Address" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val arg0 = chain.args.firstOrNull() as? String
                        if (arg0 == "wlan" || arg0 == "swlan") {
                            val stackTrace = Thread.currentThread().stackTrace
                            val isSocketBinding = stackTrace.any { element ->
                                element.className.contains("CphUnicastWifiReceiver") ||
                                element.className.contains("CphUnicastWifiSender")
                            }
                            if (isSocketBinding) {
                                "0.0.0.0"
                            } else {
                                getVirtualLanIp() ?: chain.proceed()
                            }
                        } else {
                            chain.proceed()
                        }
                    }
                }
            }.onFailure { xlog(it) }

            // 监听对端发来的握手包并自学习对端 IP（双向自愈），避免因 ARP 缓存过期导致丢失
            runCatching {
                val receiverClass = classLoader.loadClass("com.samsung.android.cmcp2phelper.transport.internal.CphReceiver")
                val msgClass = classLoader.loadClass("com.samsung.android.cmcp2phelper.data.CphMessage")
                val method = receiverClass.getDeclaredMethod("handleReceivedMessage", msgClass)
                xposedModule.hook(method).intercept { chain ->
                    try {
                        val msg = chain.args[0]
                        val ip = msg?.reflect?.call("getResponderIP") as? String
                        if (!ip.isNullOrBlank() && ip != "0.0.0.0" && ip != "127.0.0.1") {
                            cachedPeerIps.add(ip)
                        }
                        // 无论消息类型（Discovery 探测或 Command 呼叫命令），强制将发送方加入 CphDeviceManager 的 cacheMap，确保主副设备对等互通
                        val devMgrClass = classLoader.loadClass("com.samsung.android.cmcp2phelper.data.CphDeviceManager")
                        devMgrClass.getDeclaredMethod("addToCache", msgClass).invoke(null, msg)
                    } catch (_: Throwable) {}
                    chain.proceed()
                }
            }.onFailure { xlog(it) }

            // 监听 P2P 呼叫命令（event: 101, method: INVITE 等），输出即时链路状态日志
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.servicemodules.volte2.CmcP2pHelperManager\$p2pCommandListener")
                val method = clazz.getDeclaredMethod("onReceiveCommand", String::class.java, String::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val devId = chain.args.getOrNull(0) as? String
                    val msg = chain.args.getOrNull(1) as? String
                    xlog("OneUIX: P2P Command received from $devId: $msg")
                    chain.proceed()
                }
            }.onFailure { xlog(it) }

            // 确保对端 IP 查询永不返回空，即使 cacheMap 尚未构建也能直接命中虚拟 LAN IP
            runCatching {
                val devMgrClass = classLoader.loadClass("com.samsung.android.cmcp2phelper.data.CphDeviceManager")
                val method = devMgrClass.getDeclaredMethod("getTargetIpAddress", String::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed() as? String
                    if (result.isNullOrEmpty()) {
                        val fallbackIp = cachedPeerIps.firstOrNull()
                            ?: virtualLanPeerIp.takeIf { it.isNotBlank() }?.trim()
                            ?: getZeroTierArpIps().firstOrNull()
                            ?: ""
                        if (fallbackIp.isNotEmpty()) {
                            xlog("OneUIX: Fallback getTargetIpAddress for ${chain.args[0]} -> $fallbackIp")
                        }
                        fallbackIp
                    } else {
                        result
                    }
                }
            }.onFailure { xlog(it) }

            // 确保主设备 (PD) 发送 P2P Command 时 supportDevices 不为空（否则官方代码直接 return 丢弃指令）
            runCatching {
                val devMgrClass = classLoader.loadClass("com.samsung.android.cmcp2phelper.data.CphDeviceManager")
                val method = devMgrClass.getDeclaredMethod("getDeviceList", String::class.java)
                xposedModule.hook(method).intercept { chain ->
                    @Suppress("UNCHECKED_CAST")
                    val result = chain.proceed() as? Collection<Any>
                    if (result.isNullOrEmpty()) {
                        val peerIp = cachedPeerIps.firstOrNull()
                            ?: virtualLanPeerIp.takeIf { it.isNotBlank() }?.trim()
                            ?: getZeroTierArpIps().firstOrNull()
                        if (peerIp != null) {
                            try {
                                val infoClass = classLoader.loadClass("com.samsung.android.cmcp2phelper.DiscoveredDeviceInfo")
                                val imsRegistryClass = classLoader.loadClass("com.sec.internal.ims.registry.ImsRegistry")
                                val accountMgr = imsRegistryClass.getDeclaredMethod("getCmcAccountManager").invoke(null)
                                val cmcInfo = accountMgr?.reflect?.get("mCmcInfo")
                                val lineId = (cmcInfo?.reflect?.get("mLineId") as? String) ?: (chain.args[0] as? String) ?: ""
                                val cmcSettingMgr = accountMgr?.reflect?.get("mCmcSetting")
                                @Suppress("UNCHECKED_CAST")
                                val devIdList = (cmcSettingMgr?.reflect?.call("getDeviceIdList") as? List<String>) ?: emptyList()
                                val ownDevId = (cmcInfo?.reflect?.get("mDeviceId") as? String) ?: ""
                                val targetDevIds = devIdList.filter { it != ownDevId }.ifEmpty {
                                    listOf((cmcInfo?.reflect?.get("mLineOwnerDeviceId") as? String) ?: "")
                                }
                                val constructor = infoClass.getConstructor(String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
                                val fallbackList = targetDevIds.filter { it.isNotBlank() }.map { constructor.newInstance(lineId, it, true) }
                                if (fallbackList.isNotEmpty()) {
                                    xlog("OneUIX: Fallback getDeviceList provided simulated peers: $targetDevIds for line $lineId")
                                    fallbackList
                                } else {
                                    result ?: emptyList<Any>()
                                }
                            } catch (_: Throwable) {
                                result ?: emptyList<Any>()
                            }
                        } else {
                            result ?: emptyList<Any>()
                        }
                    } else {
                        result
                    }
                }
            }.onFailure { xlog(it) }

            // 对等发现列表注入：将 ZeroTier 对端 IP 注入 P2P Discovery 列表
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.servicemodules.volte2.CmcP2pHelperManager")
                clazz.declaredMethods.filter { it.name == "startDiscovery" && it.parameterTypes.size == 2 }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        @Suppress("UNCHECKED_CAST")
                        val originalList = chain.args[0] as? List<String> ?: emptyList()
                        val extraIps = mutableSetOf<String>()
                        if (virtualLanPeerIp.isNotBlank()) {
                            val manualIp = virtualLanPeerIp.trim()
                            extraIps.add(manualIp)
                            cachedPeerIps.add(manualIp)
                        }
                        val arpIps = getZeroTierArpIps()
                        extraIps.addAll(arpIps)
                        cachedPeerIps.addAll(arpIps)
                        extraIps.addAll(cachedPeerIps)

                        val combinedList = ArrayList(originalList)
                        for (ip in extraIps) {
                            if (!combinedList.contains(ip)) {
                                combinedList.add(ip)
                            }
                        }
                        xlog("OneUIX: Augmented P2P discovery host list: $combinedList")
                        val newArgs = chain.args.toTypedArray()
                        newArgs[0] = combinedList
                        chain.proceed(newArgs)
                    }
                }
            }.onFailure { xlog(it) }

            // 放行 isInP2pArea：允许在蜂窝数据 (RAT != 18) 下维持 P2P 会话
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.servicemodules.volte2.CmcServiceHelper")
                val regClass = classLoader.loadClass("com.sec.ims.ImsRegistration")
                val method = clazz.getDeclaredMethod("isInP2pArea", regClass)
                xposedModule.hook(method).intercept { true }
            }.onFailure { xlog(it) }

            // 放行 isP2pDiscoveryDone：避免休眠唤醒后来电通知因等待周期探测完成而被延迟或取消
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.servicemodules.volte2.CmcServiceHelper")
                val method = clazz.getDeclaredMethod("isP2pDiscoveryDone")
                xposedModule.hook(method).intercept { true }
            }.onFailure { xlog(it) }

            // 保持主设备 mP2pRegiInfo 初始化
            runCatching {
                val clazz = classLoader.loadClass("com.sec.internal.ims.servicemodules.volte2.CmcServiceHelper")
                val regClass = classLoader.loadClass("com.sec.ims.ImsRegistration")
                val method = clazz.getDeclaredMethod("onRegistered", regClass)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        val helper = chain.thisObject
                        val regInfo = chain.args[0]
                        val profile = regInfo?.reflect?.call("getImsProfile")
                        val cmcType = profile?.reflect?.call("getCmcType") as? Int ?: 0
                        if (cmcType == 1) {
                            val currentP2pInfo = helper.reflect["mP2pRegiInfo"]
                            if (currentP2pInfo == null) {
                                helper.reflect["mP2pRegiInfo"] = regInfo
                                xlog("OneUIX: Initialized mP2pRegiInfo for Virtual LAN P2P")
                            }
                        }
                    } catch (t: Throwable) {
                        xlog(t)
                    }
                    result
                }
            }.onFailure { xlog(it) }

            // 7. P2P 虚拟局域网音频流（RTP）穿透自愈与接口解绑
            val cmcStreamChannels = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

            // 副设备端 (S24U/SD)：拦截 ResipMediaHandler.saeCreateChannel 与 saeUpdateChannel
            runCatching {
                val mediaHandlerClass = classLoader.loadClass("com.sec.internal.ims.core.handler.secims.ResipMediaHandler")
                mediaHandlerClass.declaredMethods.filter { it.name == "saeCreateChannel" || it.name == "saeUpdateChannel" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val peerIp = getPeerVirtualLanIp(virtualLanPeerIp)
                        val localVlanIp = getVirtualLanIp()
                        val origLocalIp = chain.args[2] as? String
                        val origRemoteIp = chain.args[4] as? String
                        xlog("OneUIX: ${method.name} before: localIp=$origLocalIp, remoteIp=$origRemoteIp, peerIp=$peerIp, localVlanIp=$localVlanIp")
                        val newArgs = chain.args.toTypedArray()
                        if (!peerIp.isNullOrEmpty()) {
                            newArgs[4] = peerIp
                        }
                        if (!localVlanIp.isNullOrEmpty()) {
                            newArgs[2] = localVlanIp
                        }
                        xlog("OneUIX: ${method.name} after: localIp=${newArgs[2]}, remoteIp=${newArgs[4]}")
                        chain.proceed(newArgs)
                    }
                }

                // 避免 SVE 进程被锁定在蜂窝网络 rmnet_data0 上导致无法向虚拟局域网 10.0.0.x 路由音频包
                mediaHandlerClass.declaredMethods.filter { it.name == "bindToNetwork" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val origNet = chain.args[0]
                        try {
                            val imsRegistryClass = classLoader.loadClass("com.sec.internal.ims.registry.ImsRegistry")
                            val accountMgr = imsRegistryClass.getDeclaredMethod("getCmcAccountManager").invoke(null)
                            val isSd = accountMgr?.reflect?.call("isSecondaryDevice") as? Boolean ?: false
                            if (isSd) {
                                xlog("OneUIX: SD skip bindToNetwork for SVE: $origNet -> null")
                                val newArgs = chain.args.toTypedArray()
                                newArgs[0] = null
                                return@intercept chain.proceed(newArgs)
                            }
                        } catch (_: Throwable) {
                            xlog("OneUIX: bindToNetwork bypass: $origNet -> null")
                            val newArgs = chain.args.toTypedArray()
                            newArgs[0] = null
                            return@intercept chain.proceed(newArgs)
                        }
                        chain.proceed()
                    }
                }
            }.onFailure { xlog(it) }

            // 主设备端 (S21+/PD)：拦截 ResipCmcHandler.sreCreateStream 与 sreSetNetId
            runCatching {
                val cmcHandlerClass = classLoader.loadClass("com.sec.internal.ims.core.handler.secims.ResipCmcHandler")
                cmcHandlerClass.declaredMethods.filter { it.name == "sreCreateStream" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        // args: (int i, int i2, int i3, String localIp, int localPort, String remoteIp, int remotePort, ...)
                        val streamType = chain.args[2] as? Int ?: 0
                        val origLocalIp = chain.args[3] as? String
                        val origRemoteIp = chain.args[5] as? String
                        val peerIp = getPeerVirtualLanIp(virtualLanPeerIp)
                        val localVlanIp = getVirtualLanIp()
                        xlog("OneUIX: sreCreateStream before: streamType=$streamType, localIp=$origLocalIp, remoteIp=$origRemoteIp, peerIp=$peerIp, localVlanIp=$localVlanIp")
                        // 严禁篡改运营商 VoLTE 流（IPv6 240e:...），仅对 CMC 流（IPv4 地址）替换虚拟局域网 IP
                        val isCmcStream = (origLocalIp != null && !origLocalIp.contains(":") && origRemoteIp != null && !origRemoteIp.contains(":"))
                        val result = if (isCmcStream) {
                            val newArgs = chain.args.toTypedArray()
                            if (!peerIp.isNullOrEmpty()) {
                                newArgs[5] = peerIp
                            }
                            if (!localVlanIp.isNullOrEmpty()) {
                                newArgs[3] = localVlanIp
                            }
                            xlog("OneUIX: sreCreateStream after (CMC Stream): localIp=${newArgs[3]}, remoteIp=${newArgs[5]}")
                            chain.proceed(newArgs)
                        } else {
                            chain.proceed()
                        }
                        if (isCmcStream && result is Int) {
                            cmcStreamChannels.add(result)
                            xlog("OneUIX: Tracked CMC stream channel: $result")
                        }
                        result
                    }
                }

                cmcHandlerClass.declaredMethods.filter { it.name == "sreDeleteStream" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val ch = chain.args[0] as? Int ?: 0
                        cmcStreamChannels.remove(ch)
                        chain.proceed()
                    }
                }

                // 解除主设备 CMC 流对特定 Wi-Fi 网络句柄（NetId）的强制绑定，放行由内核路由到 ZeroTier
                cmcHandlerClass.declaredMethods.filter { it.name == "sreSetNetId" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val ch = chain.args[0] as? Int ?: 0
                        val origNetId = chain.args[1] as? Long ?: 0L
                        if (cmcStreamChannels.contains(ch)) {
                            xlog("OneUIX: sreSetNetId intercepted for CMC stream: ch=$ch, origNetId=$origNetId -> setting 0L for ZeroTier routing")
                            val newArgs = chain.args.toTypedArray()
                            newArgs[1] = 0L
                            chain.proceed(newArgs)
                        } else {
                            chain.proceed()
                        }
                    }
                }
            }.onFailure { xlog(it) }

            // 拦截 UserAgent.updateRouteTable 避免蜂窝接口添加不可达的局域网路由导致抛错或破坏内核路由
            runCatching {
                val uaClass = classLoader.loadClass("com.sec.internal.ims.core.handler.secims.UserAgent")
                uaClass.declaredMethods.filter { it.name == "updateRouteTable" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val addr = chain.args[1] as? String
                        xlog("OneUIX: UserAgent.updateRouteTable intercepted: addr=$addr")
                        val peerIp = getPeerVirtualLanIp(virtualLanPeerIp)
                        val localIp = getVirtualLanIp()
                        if (addr == peerIp || addr == localIp || (addr != null && (addr.startsWith("10.0.0.") || addr.startsWith("192.168.")))) {
                            xlog("OneUIX: UserAgent.updateRouteTable bypassed for: $addr")
                            return@intercept null
                        }
                        chain.proceed()
                    }
                }
            }.onFailure { xlog(it) }
        }
    }

    private fun hasVirtualLanInterface(): Boolean = try {
        NetworkInterface.getNetworkInterfaces()?.toList()?.any { iface ->
            (iface.name.startsWith("zt") || iface.name.startsWith("tun") || iface.name.startsWith("wg")) &&
            iface.isUp &&
            iface.inetAddresses.toList().any { it is Inet4Address && !it.isLoopbackAddress }
        } ?: false
    } catch (_: Throwable) {
        false
    }

    private fun getVirtualLanIp(): String? = try {
        NetworkInterface.getNetworkInterfaces()?.toList()?.firstNotNullOfOrNull { iface ->
            if ((iface.name.startsWith("zt") || iface.name.startsWith("tun") || iface.name.startsWith("wg")) && iface.isUp) {
                iface.inetAddresses.toList().firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
            } else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun getPeerVirtualLanIp(virtualLanPeerIp: String): String? {
        if (virtualLanPeerIp.isNotBlank()) return virtualLanPeerIp.trim()
        val localIp = getVirtualLanIp()
        return cachedPeerIps.lastOrNull { it != localIp }
            ?: getZeroTierArpIps().firstOrNull { it != localIp && it != "10.0.0.120" && it != "10.0.0.150" }
            ?: getZeroTierArpIps().firstOrNull { it != localIp }
    }

    private fun getZeroTierArpIps(): List<String> = try {
        File("/proc/net/arp").readLines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 6) {
                val ip = parts[0]
                val flags = parts[2]
                val dev = parts[5]
                // 仅采纳 flags 非 0x0（已解析完成）的条目
                if (flags != "0x0" &&
                    (dev.startsWith("zt") || dev.startsWith("tun") || dev.startsWith("wg")) &&
                    ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
                ) {
                    ip
                } else null
            } else null
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun getVirtualWlanBssid(classLoader: ClassLoader): String = try {
        val imsRegistryClass = classLoader.loadClass("com.sec.internal.ims.registry.ImsRegistry")
        val accountMgr = imsRegistryClass.getDeclaredMethod("getCmcAccountManager").invoke(null)
        val cmcInfo = accountMgr?.reflect?.get("mCmcInfo")
        val ownerDevId = (cmcInfo?.reflect?.get("mLineOwnerDeviceId") as? String)
            ?: (accountMgr?.reflect?.call("getCurrentLineOwnerDeviceId") as? String)
        val lineId = cmcInfo?.reflect?.get("mLineId") as? String
        val rawSeed = ownerDevId?.ifBlank { null } ?: lineId?.ifBlank { null } ?: "OneUIX_CMC_VIRTUAL_BSSID"
        val cleanSeed = rawSeed.removePrefix("urn:duid:").trim()
        val md5 = java.security.MessageDigest.getInstance("MD5").digest(cleanSeed.toByteArray())
        val hex = md5.joinToString("") { "%02x".format(it) }
        "02" + hex.substring(0, 10)
    } catch (_: Throwable) {
        "020000c0ffee"
    }
}
