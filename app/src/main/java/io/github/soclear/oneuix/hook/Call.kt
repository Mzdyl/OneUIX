package io.github.soclear.oneuix.hook

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import java.util.concurrent.CompletableFuture
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.serialization.Serializable
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.SamsungFeature.overrideCscString
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.logError
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object Call {
    fun supportVoiceCallRecording(
        loadPackageParam: LoadPackageParam,
        preferRecordingButton: Boolean
    ) {
        if (loadPackageParam.packageName != Package.TELEPHONYUI &&
            loadPackageParam.packageName != Package.INCALLUI &&
            loadPackageParam.packageName != Package.DIALER
        ) return

        overrideCscString(loadPackageParam, "supportVoiceCallRecording") { key, _ ->
            if (key == "CscFeature_VoiceCall_ConfigRecording") {
                "RecordingAllowed" + if (preferRecordingButton) "" else "ByMenu"
            } else {
                null
            }
        }
    }

    fun showGeocodedLocationInRecentCall(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.DIALER) return
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttach

            XposedBridge.hookMethod(
                DexMethod(hookConfig.setSubTextMethod).getMethodInstance(classLoader),
                object : XC_MethodHook() {
                    val geoCodedLocationField =
                        DexField(hookConfig.geoCodedLocationField).getFieldInstance(classLoader)
                    val subTextField =
                        DexField(hookConfig.subTextField).getFieldInstance(classLoader)

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val baseCallLog = param.args[0]
                        val callLogViewItem = param.args[1]
                        val geocodedLocation = geoCodedLocationField.get(baseCallLog)
                        val subText = subTextField.get(callLogViewItem)
                        val subTextWithLocation = "$subText $geocodedLocation".trim()
                        subTextField.set(callLogViewItem, subTextWithLocation)
                    }
                }
            )
        }
    }

    fun isOpStyleCHN(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.DIALER) return
        try {
            findAndHookMethod(
                "com.samsung.android.dialtacts.util.CscFeatureUtil",
                loadPackageParam.classLoader,
                "isOpStyleCHNImpl",
                returnConstant(true)
            )
        } catch (t: Throwable) {
            logError("isOpStyleCHN failed", t)
        }
    }

    fun setCallAndTextDeviceType(loadPackageParam: LoadPackageParam, mdecDeviceType: Int) {
        if (loadPackageParam.packageName != Package.DIALER &&
            loadPackageParam.packageName != Package.INCALLUI &&
            loadPackageParam.packageName != Package.PHONE &&
            loadPackageParam.packageName != Package.ANDROID &&
            loadPackageParam.packageName != Package.TELECOM &&
            loadPackageParam.packageName != Package.IMS_SERVICE
        ) return

        if (mdecDeviceType == 2) {
            val classLoader = loadPackageParam.classLoader

            // 1. 让拨号盘和通话框架识别到 CMC 呼叫账户
            try {
                findAndHookMethod(
                    "android.telecom.TelecomManager",
                    classLoader,
                    "getCallCapablePhoneAccounts",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val list = (param.result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return
                            if (list.isEmpty()) return
                            if (list.none { it.id?.contains("CMC") == true }) {
                                val cmcHandle = PhoneAccountHandle(
                                    ComponentName("com.android.phone", "com.android.services.telephony.TelephonyConnectionService"),
                                    "CMC_0"
                                )
                                val updated = ArrayList(list)
                                updated.add(cmcHandle)
                                param.result = updated
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                logError("setCallAndTextDeviceType: TelecomManager.getCallCapablePhoneAccounts failed", t)
            }

            try {
                findAndHookMethod(
                    "android.telecom.TelecomManager",
                    classLoader,
                    "getPhoneAccount",
                    PhoneAccountHandle::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val handle = param.args[0] as? PhoneAccountHandle ?: return
                            if (handle.id?.contains("CMC") == true) {
                                try {
                                    val icon = android.graphics.drawable.Icon.createWithResource("com.samsung.android.dialer", android.R.drawable.sym_def_app_icon)
                                    val builder = PhoneAccount.builder(handle, "通过主设备拨打")
                                        .setCapabilities(
                                            PhoneAccount.CAPABILITY_CALL_PROVIDER or
                                            PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                                        )
                                        .setIcon(icon)
                                    param.result = builder.build()
                                } catch (t: Throwable) {
                                    logError("setCallAndTextDeviceType: mock PhoneAccount failed", t)
                                }
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                logError("setCallAndTextDeviceType: TelecomManager.getPhoneAccount failed", t)
            }

            // 2. 仅在拨号盘进程中使用 DexKit 动态匹配并放行底部跨设备切换栏
            if (loadPackageParam.packageName == Package.DIALER) {
                afterAttach {
                    try {
                        System.loadLibrary("dexkit")
                        DexKitBridge.create(classLoader, true).use { bridge ->
                            // 2.1 伪装 ContinuityModel (跨设备状态模型)
                            val continuityClassData = bridge.findClass {
                                matcher {
                                    usingStrings("ContinuityModel", "isSecondaryDevice : ")
                                }
                            }.firstOrNull()

                            if (continuityClassData != null) {
                                val isSecondaryMethod = bridge.findMethod {
                                    matcher {
                                        declaredClass(continuityClassData.name)
                                        usingStrings("isSecondaryDevice : ")
                                    }
                                }.firstOrNull()
                                if (isSecondaryMethod != null) {
                                    XposedBridge.hookMethod(isSecondaryMethod.getMethodInstance(classLoader), returnConstant(true))
                                }

                                val isCallAllowedMethod = bridge.findMethod {
                                    matcher {
                                        declaredClass(continuityClassData.name)
                                        usingStrings("isCallAllowedSdByPd : ")
                                    }
                                }.firstOrNull()
                                if (isCallAllowedMethod != null) {
                                    XposedBridge.hookMethod(isCallAllowedMethod.getMethodInstance(classLoader), returnConstant(true))
                                }
                            }

                            // 2.2 动态查找 CMC 切换器 (DialPadBottomToggleCmc) 并放开双卡限制
                            val cmcToggleClassData = bridge.findClass {
                                matcher {
                                    usingStrings("DialPadBottomToggleCmc", "isShowBottomToggle ")
                                }
                            }.firstOrNull()
                            val cmcClass = cmcToggleClassData?.getInstance(classLoader)

                            if (cmcClass != null) {
                                val dMethodData = bridge.findMethod {
                                    matcher {
                                        declaredClass(cmcToggleClassData.name)
                                        usingStrings("phone account size : ")
                                    }
                                }.firstOrNull()
                                val dMethod = dMethodData?.getMethodInstance(classLoader)

                                val qMethodData = bridge.findMethod {
                                    matcher {
                                        declaredClass(cmcToggleClassData.name)
                                        usingStrings("isShowBottomToggle ")
                                    }
                                }.firstOrNull()
                                val qMethodName = qMethodData?.name ?: "q"

                                for (m in cmcClass.declaredMethods) {
                                    if (m.name != "k" && m.returnType == java.lang.Boolean.TYPE && m.parameterTypes.isEmpty()) {
                                        findAndHookMethod(cmcClass, m.name, object : XC_MethodHook() {
                                            override fun beforeHookedMethod(param: MethodHookParam) {
                                                if (m.name == qMethodName) {
                                                    try {
                                                        dMethod?.invoke(param.thisObject)
                                                    } catch (_: Throwable) {}
                                                }
                                                param.result = true
                                            }
                                        })
                                    }
                                }
                            }

                            // 2.3 动态查找 DialPadBottomToggleHelper，兜底确保选用 CMC 切换器
                            val helperClassData = bridge.findClass {
                                matcher {
                                    usingStrings("DialPadBottomToggleHelper", "changeVisibility : ")
                                }
                            }.firstOrNull()

                            if (helperClassData != null) {
                                val helperClass = helperClassData.getInstance(classLoader)

                                val createToggleMethod = bridge.findMethod {
                                    matcher {
                                        declaredClass(helperClassData.name)
                                        usingStrings("DialPadBottomToggleCmc", "DialPadBottomToggleDsds")
                                    }
                                }.firstOrNull()?.getMethodInstance(classLoader)

                                if (createToggleMethod != null && cmcClass != null) {
                                    XposedBridge.hookMethod(createToggleMethod, object : XC_MethodHook() {
                                        override fun afterHookedMethod(param: MethodHookParam) {
                                            val currentResult = param.result
                                            if (currentResult == null || !cmcClass.isInstance(currentResult)) {
                                                try {
                                                    val ctor = cmcClass.declaredConstructors.firstOrNull { it.parameterTypes.size == 1 }
                                                    if (ctor != null) {
                                                        ctor.isAccessible = true
                                                        val paramType = ctor.parameterTypes[0]
                                                        val modelSet = helperClass.declaredFields
                                                            .filter { !Modifier.isStatic(it.modifiers) }
                                                            .mapNotNull { f ->
                                                                try {
                                                                    f.isAccessible = true
                                                                    f.get(param.thisObject)
                                                                } catch (_: Throwable) { null }
                                                            }
                                                            .firstOrNull { paramType.isInstance(it) }
                                                        if (modelSet != null) {
                                                            param.result = ctor.newInstance(modelSet)
                                                        }
                                                    }
                                                } catch (t: Throwable) {
                                                    logError("Replace toggle with CMC failed", t)
                                                }
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        logError("setCallAndTextDeviceType: DexKit resolution failed", t)
                    }
                }
            }

            // 3. 在系统核心服务 (system_server / Telecom) 中放行并识别 CMC 呼叫账户
            if (loadPackageParam.packageName == Package.ANDROID || loadPackageParam.packageName == Package.TELECOM) {
                val cmcHandle = PhoneAccountHandle(
                    ComponentName("com.android.phone", "com.android.services.telephony.TelephonyConnectionService"),
                    "CMC_0",
                    android.os.Process.myUserHandle()
                )

                fun mockAccount(handle: PhoneAccountHandle): PhoneAccount {
                    val icon = android.graphics.drawable.Icon.createWithResource("com.samsung.android.dialer", android.R.drawable.sym_def_app_icon)
                    val bundle = Bundle().apply {
                        putBoolean("com.samsung.telecom.extra.NO_CHECK_IF_VIDEO_CAPABLE", true)
                        putBoolean("com.samsung.telecom.extra.CAN_COEXIST_ACCOUNT", true)
                    }
                    return PhoneAccount.builder(handle, "通过主设备拨打")
                        .setAddress(Uri.fromParts("tel", "", null))
                        .setSubscriptionAddress(Uri.fromParts("tel", "", null))
                        .setCapabilities(
                            PhoneAccount.CAPABILITY_CALL_PROVIDER or
                            32 // CAPABILITY_MULTI_USER
                        )
                        .setHighlightColor(-16743937)
                        .setShortDescription("CMC")
                        .setSupportedUriSchemes(listOf("tel", "voicemail"))
                        .setExtras(bundle)
                        .setIcon(icon)
                        .build()
                }

                // 3.1 拦截 PhoneAccountRegistrar: 报告 CMC 账户具有外拨通话能力 (getCallCapablePhoneAccounts)
                try {
                    val registrarClass = XposedHelpers.findClassIfExists("com.android.server.telecom.PhoneAccountRegistrar", classLoader)
                    if (registrarClass != null) {
                        XposedBridge.hookAllMethods(registrarClass, "getCallCapablePhoneAccounts", object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val list = (param.result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return
                                if (list.none { it.id?.contains("CMC") == true }) {
                                    val newList = ArrayList(list)
                                    newList.add(cmcHandle)
                                    param.result = newList
                                }
                            }
                        })

                        XposedBridge.hookAllMethods(registrarClass, "getPhoneAccount", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val handle = param.args.firstOrNull() as? PhoneAccountHandle ?: return
                                if (handle.id?.contains("CMC") == true) {
                                    param.result = mockAccount(handle)
                                }
                            }
                        })

                        XposedBridge.hookAllMethods(registrarClass, "getPhoneAccountUnchecked", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val handle = param.args.firstOrNull() as? PhoneAccountHandle ?: return
                                if (handle.id?.contains("CMC") == true) {
                                    param.result = mockAccount(handle)
                                }
                            }
                        })
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: PhoneAccountRegistrar hooks failed", t)
                }

                // 3.2 拦截 CallsManager: 找到外拨账户以及构建可能账户时包含 CMC
                try {
                    val callsManagerClass = XposedHelpers.findClassIfExists("com.android.server.telecom.CallsManager", classLoader)
                    if (callsManagerClass != null) {
                        XposedBridge.hookAllMethods(callsManagerClass, "findOutgoingCallPhoneAccount", object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val callObj = param.args.firstOrNull { it != null && it.javaClass.name.endsWith(".Call") }
                                val callInitHandle = callObj?.let {
                                    try {
                                        XposedHelpers.callMethod(it, "getInitiatingAccountHandle") as? PhoneAccountHandle
                                            ?: XposedHelpers.callMethod(it, "getTargetPhoneAccount") as? PhoneAccountHandle
                                    } catch (_: Throwable) { null }
                                }
                                val handleArg = param.args.filterIsInstance<PhoneAccountHandle>().firstOrNull()
                                val cmc = listOfNotNull(handleArg, callInitHandle).firstOrNull { it.id?.contains("CMC") == true }
                                if (cmc != null) {
                                    param.result = CompletableFuture.completedFuture(listOf(cmc))
                                }
                            }
                        })

                        XposedBridge.hookAllMethods(callsManagerClass, "constructPossiblePhoneAccounts", object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val list = (param.result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return
                                if (list.none { it.id?.contains("CMC") == true }) {
                                    val newList = ArrayList(list)
                                    newList.add(cmcHandle)
                                    param.result = newList
                                }
                            }
                        })

                        val samsungRegistrarClass = XposedHelpers.findClassIfExists(
                            "com.samsung.server.telecom.basiccall.callsmanager.phoneaccount.SamsungPhoneAccountHandleRegistrar",
                            classLoader
                        )
                        if (samsungRegistrarClass != null) {
                            XposedBridge.hookAllMethods(samsungRegistrarClass, "constructPossiblePhoneAccounts", object : XC_MethodHook() {
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    val list = (param.result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return
                                    if (list.none { it.id?.contains("CMC") == true }) {
                                        param.result = ArrayList(list).apply { add(cmcHandle) }
                                    }
                                }
                            })
                        }

                        val samsungNotifierClass = XposedHelpers.findClassIfExists(
                            "com.samsung.server.telecom.basiccall.callsmanager.phoneaccount.SamsungPhoneAccountHandleNotifier",
                            classLoader
                        )
                        if (samsungNotifierClass != null) {
                            XposedBridge.hookAllMethods(samsungNotifierClass, "constructPossiblePhoneAccounts", object : XC_MethodHook() {
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    val list = (param.result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return
                                    if (list.none { it.id?.contains("CMC") == true }) {
                                        param.result = ArrayList(list).apply { add(cmcHandle) }
                                    }
                                }
                            })
                        }

                        XposedBridge.hookAllMethods(callsManagerClass, "onCallRedirectionComplete", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val callObj = param.args.firstOrNull { it != null && it.javaClass.name.endsWith(".Call") }
                                val callInitHandle = callObj?.let {
                                    try {
                                        XposedHelpers.callMethod(it, "getInitiatingAccountHandle") as? PhoneAccountHandle
                                            ?: XposedHelpers.callMethod(it, "getTargetPhoneAccount") as? PhoneAccountHandle
                                    } catch (_: Throwable) { null }
                                }
                                if (callInitHandle?.id?.contains("CMC") == true && param.args.size > 2) {
                                    if ((param.args[2] as? PhoneAccountHandle)?.id?.contains("CMC") != true) {
                                        param.args[2] = callInitHandle
                                    }
                                }
                            }
                        })
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: CallsManager hooks failed", t)
                }

                // 3.3 拦截 Call.setTargetPhoneAccount: 防止 CMC 目标账号被强行篡改为本地实体 SIM
                try {
                    val callClass = XposedHelpers.findClassIfExists("com.android.server.telecom.Call", classLoader)
                    if (callClass != null) {
                        XposedBridge.hookAllMethods(callClass, "setTargetPhoneAccount", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val newHandle = param.args.firstOrNull() as? PhoneAccountHandle
                                val target = try {
                                    XposedHelpers.callMethod(param.thisObject, "getTargetPhoneAccount") as? PhoneAccountHandle
                                } catch (_: Throwable) { null }
                                val init = try {
                                    XposedHelpers.callMethod(param.thisObject, "getInitiatingAccountHandle") as? PhoneAccountHandle
                                } catch (_: Throwable) { null }

                                val isCmc = (target?.id?.contains("CMC") == true) || (init?.id?.contains("CMC") == true)
                                if (isCmc && newHandle?.id?.contains("CMC") != true) {
                                    // 强行将目标账号固定为 CMC 账号，阻断回退篡改
                                    param.args[0] = target ?: init ?: cmcHandle
                                }
                            }
                        })

                        XposedBridge.hookAllMethods(callClass, "getIntentExtras", object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val target = try { XposedHelpers.callMethod(param.thisObject, "getTargetPhoneAccount") as? PhoneAccountHandle } catch (_: Throwable) { null }
                                val init = try { XposedHelpers.callMethod(param.thisObject, "getInitiatingAccountHandle") as? PhoneAccountHandle } catch (_: Throwable) { null }
                                if (target?.id?.contains("CMC") == true || init?.id?.contains("CMC") == true) {
                                    val extras = (param.result as? Bundle) ?: Bundle()
                                    extras.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                    val oemExtras = extras.getBundle("android.telephony.ims.extra.OEM_EXTRAS") ?: Bundle()
                                    oemExtras.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                    extras.putBundle("android.telephony.ims.extra.OEM_EXTRAS", oemExtras)
                                    param.result = extras
                                }
                            }
                        })
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: Call hooks failed", t)
                }

                // 3.4 让 Telecom 的 SamsungMultiDeviceCallInfo 识别 CMC_TYPE = 2
                try {
                    val multiDeviceInfoClass = XposedHelpers.findClassIfExists("com.samsung.server.telecom.basiccall.call.info.SamsungMultiDeviceCallInfo", classLoader)
                    if (multiDeviceInfoClass != null) {
                        XposedBridge.hookAllMethods(multiDeviceInfoClass, "getCmcType", object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val call = XposedHelpers.getObjectField(param.thisObject, "call")
                                val target = try { XposedHelpers.callMethod(call, "getTargetPhoneAccount") as? PhoneAccountHandle } catch (_: Throwable) { null }
                                val init = try { XposedHelpers.callMethod(call, "getInitiatingAccountHandle") as? PhoneAccountHandle } catch (_: Throwable) { null }
                                if (target?.id?.contains("CMC") == true || init?.id?.contains("CMC") == true) {
                                    param.result = 2
                                }
                            }
                        })
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: SamsungMultiDeviceCallInfo.getCmcType failed", t)
                }
            }

            // 4. 在电信连接服务 (com.android.phone) 中允许为手机注册副设备 CMC 账户并放行呼叫
            if (loadPackageParam.packageName == Package.PHONE) {
                // 4.1 防止 PhoneUtils 与 SamsungPhoneAccountHandleUtil 将 CMC 账号映射为本地卡 1 (SIM 1)
                try {
                    val phoneUtilsClass = XposedHelpers.findClassIfExists("com.android.phone.PhoneUtils", classLoader)
                    val phoneFactoryClass = XposedHelpers.findClassIfExists("com.android.internal.telephony.PhoneFactory", classLoader)
                    if (phoneUtilsClass != null) {
                        findAndHookMethod(phoneUtilsClass, "getSubIdForPhoneAccountHandle", PhoneAccountHandle::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val handle = param.args[0] as? PhoneAccountHandle ?: return
                                if (handle.id?.contains("CMC") == true) {
                                    param.result = -1
                                }
                            }
                        })

                        findAndHookMethod(phoneUtilsClass, "getPhoneForPhoneAccountHandle", PhoneAccountHandle::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val handle = param.args[0] as? PhoneAccountHandle ?: return
                                if (handle.id?.contains("CMC") == true) {
                                    try {
                                        param.result = XposedHelpers.callStaticMethod(phoneFactoryClass, "getDefaultPhone")
                                    } catch (_: Throwable) {}
                                }
                            }
                        })
                    }

                    val accountUtilClass = XposedHelpers.findClassIfExists(
                        "com.samsung.telephony.phone.basic.phoneaccounthandle.SamsungPhoneAccountHandleUtil",
                        classLoader
                    )
                    if (accountUtilClass != null) {
                        XposedBridge.hookAllMethods(accountUtilClass, "getPhoneFromSubId", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val subId = param.args.firstOrNull { it is String } as? String
                                if (subId?.contains("CMC") == true) {
                                    try {
                                        param.result = XposedHelpers.callStaticMethod(phoneFactoryClass, "getDefaultPhone")
                                    } catch (_: Throwable) {}
                                }
                            }
                        })
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: PhoneUtils hook failed", t)
                }

                // 4.2 欺骗 SamsungSdPstnAccountRegistry 允许副机 CMC 账户注册
                try {
                    val registryClass = XposedHelpers.findClassIfExists("com.samsung.telephony.services.advanced.cmc.SamsungSdPstnAccountRegistry", classLoader)
                    if (registryClass != null) {
                        findAndHookMethod(registryClass, "isVoiceCapable", returnConstant(false))
                        findAndHookMethod(registryClass, "ensureCmcPhoneAccountRegistered", returnConstant(true))
                        findAndHookMethod(registryClass, "isPdCmcAvailable", returnConstant(true))
                        findAndHookMethod(registryClass, "hasActivatedSimSlotOnPd", returnConstant(true))
                        findAndHookMethod(registryClass, "shouldRemovePstnCapabilities", returnConstant(false))
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: SamsungSdPstnAccountRegistry failed", t)
                }

                // 4.3 拦截 TelephonyConnectionService.adjustAccountHandle 防止账号被篡改
                try {
                    val serviceClass = XposedHelpers.findClassIfExists("com.android.services.telephony.TelephonyConnectionService", classLoader)
                    if (serviceClass != null) {
                        XposedBridge.hookAllMethods(serviceClass, "adjustAccountHandle", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val handle = param.args.getOrNull(1) as? PhoneAccountHandle
                                if (handle?.id?.contains("CMC") == true) {
                                    param.result = handle
                                }
                            }
                        })

                        // 4.4 注入 CMC_TYPE=2 到 ConnectionRequest 与 Bundle 中，引导通话路由至 IMS PS 域
                        XposedBridge.hookAllMethods(serviceClass, "onCreateOutgoingConnection", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val handle = param.args.firstOrNull { it is PhoneAccountHandle } as? PhoneAccountHandle
                                val request = param.args.firstOrNull { it != null && it.javaClass.name.endsWith("ConnectionRequest") }
                                val reqHandle = request?.let {
                                    try { XposedHelpers.callMethod(it, "getAccountHandle") as? PhoneAccountHandle } catch (_: Throwable) { null }
                                }
                                if (handle?.id?.contains("CMC") == true || reqHandle?.id?.contains("CMC") == true) {
                                    val extras = request?.let {
                                        try { XposedHelpers.callMethod(it, "getExtras") as? Bundle } catch (_: Throwable) { null }
                                    }
                                    if (extras != null) {
                                        extras.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        val oem = extras.getBundle("android.telephony.ims.extra.OEM_EXTRAS") ?: Bundle()
                                        oem.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        extras.putBundle("android.telephony.ims.extra.OEM_EXTRAS", oem)
                                    }
                                }
                            }
                        })

                        XposedBridge.hookAllMethods(serviceClass, "placeOutgoingConnection", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val bundle = param.args.filterIsInstance<Bundle>().firstOrNull()
                                val conn = param.args.firstOrNull { it != null && it.javaClass.name.contains("TelephonyConnection") }
                                val connHandle = conn?.let {
                                    try { XposedHelpers.callMethod(it, "getPhoneAccountHandle") as? PhoneAccountHandle } catch (_: Throwable) { null }
                                }
                                val isCmc = connHandle?.id?.contains("CMC") == true ||
                                            bundle?.containsKey("com.samsung.telephony.extra.CMC_TYPE") == true
                                if (isCmc && bundle != null) {
                                    bundle.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                    val oem = bundle.getBundle("android.telephony.ims.extra.OEM_EXTRAS") ?: Bundle()
                                    oem.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                    bundle.putBundle("android.telephony.ims.extra.OEM_EXTRAS", oem)
                                }
                            }
                        })
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: TelephonyConnectionService hooks failed", t)
                }

                // 4.5 强制 SemCallTrackerHelper.useMdecEnabled 返回 true
                try {
                    val helperClass = XposedHelpers.findClassIfExists("com.android.internal.telephony.SemCallTrackerHelper", classLoader)
                    if (helperClass != null) {
                        findAndHookMethod(helperClass, "useMdecEnabled", returnConstant(true))
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: SemCallTrackerHelper.useMdecEnabled failed", t)
                }

                // 4.6 拦截 GsmCdmaPhone.dial 确保包含 CMC_TYPE=2，使拨号直接走 IMS PS 域
                try {
                    val phoneClass = XposedHelpers.findClassIfExists("com.android.internal.telephony.GsmCdmaPhone", classLoader)
                    if (phoneClass != null) {
                        XposedBridge.hookAllMethods(phoneClass, "dial", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val dialArgs = param.args.getOrNull(1) ?: return
                                val intentExtras = try {
                                    XposedHelpers.getObjectField(dialArgs, "intentExtras") as? Bundle
                                } catch (_: Throwable) { null }
                                if (intentExtras != null) {
                                    val oem = intentExtras.getBundle("android.telephony.ims.extra.OEM_EXTRAS")
                                    if (intentExtras.containsKey("com.samsung.telephony.extra.CMC_TYPE") || oem?.containsKey("com.samsung.telephony.extra.CMC_TYPE") == true) {
                                        intentExtras.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        val newOem = oem ?: Bundle()
                                        newOem.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        intentExtras.putBundle("android.telephony.ims.extra.OEM_EXTRAS", newOem)
                                    }
                                }
                            }
                        })
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: GsmCdmaPhone.dial failed", t)
                }

                // 4.7 拦截 SamsungCmcRepositoryImpl.isCmcTypeSd 返回 true
                try {
                    val cmcRepoClass = XposedHelpers.findClassIfExists("com.samsung.telephony.model.cmc.SamsungCmcRepositoryImpl", classLoader)
                    if (cmcRepoClass != null) {
                        XposedBridge.hookAllMethods(cmcRepoClass, "isCmcTypeSd", object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val arg = param.args.firstOrNull()
                                if (arg is Bundle) {
                                    val oem = arg.getBundle("android.telephony.ims.extra.OEM_EXTRAS")
                                    if (arg.getInt("com.samsung.telephony.extra.CMC_TYPE") == 2 || oem?.getInt("com.samsung.telephony.extra.CMC_TYPE") == 2) {
                                        param.result = true
                                    }
                                } else if (arg != null) {
                                    val connExtras = try { XposedHelpers.callMethod(arg, "getConnectionExtras") as? Bundle } catch (_: Throwable) { null }
                                    val oem = connExtras?.getBundle("android.telephony.ims.extra.OEM_EXTRAS")
                                    if (connExtras?.getInt("com.samsung.telephony.extra.CMC_TYPE") == 2 || oem?.getInt("com.samsung.telephony.extra.CMC_TYPE") == 2) {
                                        param.result = true
                                    }
                                }
                            }
                        })
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: SamsungCmcRepositoryImpl.isCmcTypeSd failed", t)
                }
            }

            // 5. 在 IMS 服务 (com.sec.imsservice) 中放行副设备 (SD) CMC 呼叫建立
            if (loadPackageParam.packageName == Package.IMS_SERVICE) {
                try {
                    val volteNotifierClass = XposedHelpers.findClassIfExists(
                        "com.sec.internal.ims.servicemodules.volte2.VolteNotifier",
                        classLoader
                    )
                    if (volteNotifierClass != null) {
                        XposedBridge.hookAllMethods(volteNotifierClass, "getCmcCallEventListenerSize", object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val size = (param.result as? Int) ?: 0
                                if (size <= 0) {
                                    param.result = 1
                                }
                            }
                        })
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: VolteNotifier hook failed", t)
                }
            }
        }
    }

    @Serializable
    private data class CallHookConfig(
        override val versionCode: Long,
        val geoCodedLocationField: String,
        val subTextField: String,
        val setSubTextMethod: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): CallHookConfig? {
        val exclusions = listOf(
            "android",
            "androidx",
            "appfunctions_aggregated_deps",
            "com",
            "dagger",
            "kotlin",
            "kotlinx"
        )

        fun geoCodedLocation(bridge: DexKitBridge): Field? {
            val baseCallLogClassData = bridge.findClass {
                excludePackages(exclusions)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    superClass = "java.lang.Object"
                    usingStrings("BaseCallLog(id=")
                }
            }.singleOrNull() ?: return null
            val baseCallLogClass = baseCallLogClassData.getInstance(classLoader)
            val instance = XposedHelpers.newInstance(baseCallLogClass)

            val tokenToField = mutableMapOf<String, Field>()

            baseCallLogClass.declaredFields.forEach { field ->
                if (field.type == String::class.java && !Modifier.isStatic(field.modifiers)) {
                    field.isAccessible = true
                    val token = "TOKEN_${field.name}"

                    // 将 token 注入到实例中
                    field.set(instance, token)
                    tokenToField[token] = field
                }
            }

            // 调用 toString() 方法
            // 目标代码： ... + ", geoCodedLocation=" + this.p + ...
            val result = instance.toString()

            // 分析结果
            val keyword = "geoCodedLocation="
            val index = result.indexOf(keyword)
            if (index != -1) {
                // 截取 geoCodedLocation= 之后的内容
                val after = result.substring(index + keyword.length)

                // 检查内容是以哪个 TOKEN 开头
                for (entry in tokenToField.entries) {
                    if (after.startsWith(entry.key)) {
                        // 找到了 geoCodedLocation 对应字段
                        return entry.value
                    }
                }
            }
            return null
        }

        fun subText(bridge: DexKitBridge): Field? {
            val callLogViewItemClassData = bridge.findClass {
                excludePackages(exclusions)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    superClass = "java.lang.Object"
                    usingStrings("CallLogViewItem {id=")
                }
            }.singleOrNull() ?: return null

            val toStingMethodData = callLogViewItemClassData.findMethod {
                matcher {
                    usingStrings("CallLogViewItem {id=")
                }
            }.singleOrNull() ?: return null

            val callLogGroupClassData = bridge.findClass {
                excludePackages(exclusions)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    superClass = "java.lang.Object"
                    usingStrings("CallLogGroup(isDateChanged=")
                }
            }.singleOrNull() ?: return null

            val callLogGroupClass = callLogGroupClassData.getInstance(classLoader)
            val callLogGroup = XposedHelpers.newInstance(callLogGroupClass, true, true)

            val callLogViewItemClass = callLogViewItemClassData.getInstance(classLoader)
            val callLogViewItem = XposedHelpers.newInstance(callLogViewItemClass, callLogGroup)

            val tokenToField = mutableMapOf<String, Field>()

            callLogViewItemClass.declaredFields.forEach { field ->
                if (field.type == String::class.java && !Modifier.isStatic(field.modifiers)) {
                    field.isAccessible = true
                    val token = "TOKEN_${field.name}"

                    // 将 token 注入到实例中
                    field.set(callLogViewItem, token)
                    tokenToField[token] = field
                }
            }

            val toStingMethodInstance = toStingMethodData.getMethodInstance(classLoader)

            val result = if (toStingMethodData.paramCount == 0) {
                toStingMethodInstance.invoke(callLogViewItem)
            } else {
                toStingMethodInstance.invoke(null, callLogViewItem)
            } as String

            // 分析结果
            val keyword = "subText="
            val index = result.indexOf(keyword)
            if (index != -1) {
                // 截取 geoCodedLocation= 之后的内容
                val after = result.substring(index + keyword.length)

                // 检查内容是以哪个 TOKEN 开头
                for (entry in tokenToField.entries) {
                    if (after.startsWith(entry.key)) {
                        // 找到了 geoCodedLocation 对应字段
                        return entry.value
                    }
                }
            }
            return null
        }

        fun setSubText(bridge: DexKitBridge, subText: Field): MethodData? {
            return bridge.findClass {
                excludePackages(exclusions)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    usingStrings("screencall;autopickupreply")
                }
            }.findMethod {
                matcher {
                    paramCount = 2
                    returnType = "void"
                    usingStrings("screencall;autopickupreply")
                    addUsingField {
                        name = subText.name
                        declaredClass(subText.declaringClass)
                    }
                }
            }.singleOrNull()
        }

        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val geoCodedLocation = geoCodedLocation(bridge) ?: return null
            val subText = subText(bridge) ?: return null
            val setSubText = setSubText(bridge, subText) ?: return null
            return CallHookConfig(
                versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode,
                geoCodedLocationField = DexField(geoCodedLocation).serialize(),
                subTextField = DexField(subText).serialize(),
                setSubTextMethod = setSubText.toDexMethod().serialize(),
            )
        }
    }
}
