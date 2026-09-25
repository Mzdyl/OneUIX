package io.github.soclear.oneuix.hook

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.logError
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.CompletableFuture

object Call {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportVoiceCallRecording(preferRecordingButton: Boolean) {
        if (param.packageName != Package.TELEPHONYUI &&
            param.packageName != Package.INCALLUI &&
            param.packageName != Package.DIALER
        ) return

        try {
            val semCscFeatureClass =
                param.classLoader.loadClass("com.samsung.android.feature.SemCscFeature")
            semCscFeatureClass.declaredMethods
                .filter { it.name == "getString" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        if (chain.args.firstOrNull() == "CscFeature_VoiceCall_ConfigRecording") {
                            "RecordingAllowed" + if (preferRecordingButton) "" else "ByMenu"
                        } else {
                            chain.proceed()
                        }
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showGeocodedLocationInRecentCall() {
        if (param.packageName != Package.DIALER) return
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttach
            try {
                val methodInstance =
                    DexMethod(hookConfig.setSubTextMethod).getMethodInstance(classLoader)
                val geoCodedLocationField =
                    DexField(hookConfig.geoCodedLocationField).getFieldInstance(classLoader)
                val subTextField =
                    DexField(hookConfig.subTextField).getFieldInstance(classLoader)

                xposedModule.hook(methodInstance).intercept { chain ->
                    val result = chain.proceed()
                    val baseCallLog = chain.args[0]
                    val callLogViewItem = chain.args[1]
                    val geocodedLocation = geoCodedLocationField.get(baseCallLog)
                    val subText = subTextField.get(callLogViewItem)
                    val subTextWithLocation = "$subText $geocodedLocation".trim()
                    subTextField.set(callLogViewItem, subTextWithLocation)
                    result
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun isOpStyleCHN() {
        if (param.packageName != Package.DIALER) return
        try {
            val clazz =
                param.classLoader.loadClass("com.samsung.android.dialtacts.util.CscFeatureUtil")
            val method = clazz.getDeclaredMethod("isOpStyleCHNImpl")
            xposedModule.hook(method).intercept { true }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setCallAndTextDeviceType(mdecDeviceType: Int) {
        setCallAndTextDeviceTypeImpl(param.packageName, param.classLoader, mdecDeviceType)
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun setCallAndTextDeviceType(mdecDeviceType: Int) {
        setCallAndTextDeviceTypeImpl(Package.ANDROID, param.classLoader, mdecDeviceType)
    }

    private fun ClassLoader.findClassOrNull(name: String): Class<*>? = try {
        loadClass(name)
    } catch (_: Throwable) {
        null
    }

    context(xposedModule: XposedModule)
    private fun setCallAndTextDeviceTypeImpl(packageName: String, classLoader: ClassLoader, mdecDeviceType: Int) {
        if (packageName != Package.DIALER &&
            packageName != Package.INCALLUI &&
            packageName != Package.PHONE &&
            packageName != Package.ANDROID &&
            packageName != Package.TELECOM &&
            packageName != Package.IMS_SERVICE
        ) return

        if (mdecDeviceType == 2) {
            // 1. 让拨号盘和通话框架识别到 CMC 呼叫账户
            try {
                val telecomManagerClass = classLoader.findClassOrNull("android.telecom.TelecomManager")
                telecomManagerClass?.declaredMethods
                    ?.filter { it.name == "getCallCapablePhoneAccounts" }
                    ?.forEach { method ->
                        xposedModule.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            val list = (result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return@intercept result
                            if (list.isEmpty()) return@intercept list
                            if (list.none { it.id?.contains("CMC") == true }) {
                                val cmcHandle = PhoneAccountHandle(
                                    ComponentName("com.android.phone", "com.android.services.telephony.TelephonyConnectionService"),
                                    "CMC_0"
                                )
                                ArrayList(list).apply { add(cmcHandle) }
                            } else {
                                list
                            }
                        }
                    }
            } catch (t: Throwable) {
                logError("setCallAndTextDeviceType: TelecomManager.getCallCapablePhoneAccounts failed", t)
            }

            try {
                val telecomManagerClass = classLoader.findClassOrNull("android.telecom.TelecomManager")
                telecomManagerClass?.declaredMethods
                    ?.filter { it.name == "getPhoneAccount" && it.parameterTypes.contentEquals(arrayOf(PhoneAccountHandle::class.java)) }
                    ?.forEach { method ->
                        xposedModule.hook(method).intercept { chain ->
                            val handle = chain.args.firstOrNull() as? PhoneAccountHandle
                            if (handle?.id?.contains("CMC") == true) {
                                try {
                                    val icon = android.graphics.drawable.Icon.createWithResource("com.samsung.android.dialer", android.R.drawable.sym_def_app_icon)
                                    val builder = PhoneAccount.builder(handle, "通过主设备拨打")
                                        .setCapabilities(
                                            PhoneAccount.CAPABILITY_CALL_PROVIDER or
                                            PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                                        )
                                        .setIcon(icon)
                                    builder.build()
                                } catch (t: Throwable) {
                                    logError("setCallAndTextDeviceType: mock PhoneAccount failed", t)
                                    chain.proceed()
                                }
                            } else {
                                chain.proceed()
                            }
                        }
                    }
            } catch (t: Throwable) {
                logError("setCallAndTextDeviceType: TelecomManager.getPhoneAccount failed", t)
            }

            // 2. 仅在拨号盘进程中使用 DexKit 动态匹配并放行底部跨设备切换栏
            if (packageName == Package.DIALER) {
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
                                }.firstOrNull()?.getMethodInstance(classLoader)
                                if (isSecondaryMethod != null) {
                                    xposedModule.hook(isSecondaryMethod).intercept { true }
                                }

                                val isCallAllowedMethod = bridge.findMethod {
                                    matcher {
                                        declaredClass(continuityClassData.name)
                                        usingStrings("isCallAllowedSdByPd : ")
                                    }
                                }.firstOrNull()?.getMethodInstance(classLoader)
                                if (isCallAllowedMethod != null) {
                                    xposedModule.hook(isCallAllowedMethod).intercept { true }
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
                                        xposedModule.hook(m).intercept { chain ->
                                            if (m.name == qMethodName) {
                                                try {
                                                    dMethod?.invoke(chain.thisObject)
                                                } catch (_: Throwable) {}
                                            }
                                            true
                                        }
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
                                    xposedModule.hook(createToggleMethod).intercept { chain ->
                                        val currentResult = chain.proceed()
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
                                                                f.get(chain.thisObject)
                                                            } catch (_: Throwable) { null }
                                                        }
                                                        .firstOrNull { paramType.isInstance(it) }
                                                    if (modelSet != null) {
                                                        ctor.newInstance(modelSet)
                                                    } else {
                                                        currentResult
                                                    }
                                                } else {
                                                    currentResult
                                                }
                                            } catch (t: Throwable) {
                                                logError("Replace toggle with CMC failed", t)
                                                currentResult
                                            }
                                        } else {
                                            currentResult
                                        }
                                    }
                                }
                            }

                            val callLogsModelClassData = bridge.findClass {
                                matcher {
                                    usingStrings("CallLogsModel")
                                }
                            }.firstOrNull()

                            if (callLogsModelClassData != null) {
                                val modelClass = callLogsModelClassData.getInstance(classLoader)
                                for (ctor in modelClass.declaredConstructors) {
                                    xposedModule.hook(ctor).intercept { chain ->
                                        val result = chain.proceed()
                                        for (f in modelClass.declaredFields) {
                                            if (!Modifier.isStatic(f.modifiers) && f.type == java.lang.Boolean.TYPE) {
                                                if (f.name == "y") {
                                                    f.isAccessible = true
                                                    f.setBoolean(chain.thisObject, true)
                                                }
                                            }
                                        }
                                        result
                                    }
                                }
                            }

                            val callLogHelperClassData = bridge.findClass {
                                matcher {
                                    usingStrings("CallLogHelperCommon", "mTelephonyModel.getVoiceMailAlphaTag is null!!!")
                                }
                            }.firstOrNull()

                            if (callLogHelperClassData != null) {
                                val cMethod = bridge.findMethod {
                                    matcher {
                                        declaredClass(callLogHelperClassData.name)
                                        usingStrings("mTelephonyModel.getVoiceMailAlphaTag is null!!!")
                                    }
                                }.firstOrNull()?.getMethodInstance(classLoader)

                                val vClass = runCatching {
                                    val itemLayout = classLoader.loadClass("com.samsung.android.dialer.calllog.view.widget.CallLogItemLayout")
                                    itemLayout.declaredMethods.firstOrNull { it.name == "setThirdIcon" }?.parameterTypes?.firstOrNull()
                                }.getOrNull() ?: runCatching {
                                    classLoader.loadClass("com.samsung.android.dialtacts.model.data.V")
                                }.getOrNull()

                                val vCtor = vClass?.declaredConstructors?.firstOrNull {
                                    it.parameterTypes.size == 2 && it.parameterTypes[0] == Int::class.javaPrimitiveType && it.parameterTypes[1] == Int::class.javaPrimitiveType
                                }?.apply { isAccessible = true }

                                val subBadgeFieldData = runCatching {
                                    bridge.findField {
                                        matcher {
                                            type("java.lang.String")
                                            readMethods {
                                                add {
                                                    addInvoke {
                                                        declaredClass("com.samsung.android.dialer.calllog.view.widget.CallLogItemLayout")
                                                        name("setSubTextBadge")
                                                    }
                                                }
                                            }
                                        }
                                    }.firstOrNull()
                                }.getOrNull()

                                val isBadgeVisibleFieldData = runCatching {
                                    bridge.findField {
                                        matcher {
                                            type("boolean")
                                            readMethods {
                                                add {
                                                    addInvoke {
                                                        declaredClass("com.samsung.android.dialer.calllog.view.widget.CallLogItemLayout")
                                                        name("setSubTextBadge")
                                                    }
                                                }
                                            }
                                        }
                                    }.firstOrNull()
                                }.getOrNull()

                                val thirdIconFieldData = runCatching {
                                    bridge.findField {
                                        matcher {
                                            if (vClass != null) type(vClass.name)
                                            readMethods {
                                                add {
                                                    addInvoke {
                                                        declaredClass("com.samsung.android.dialer.calllog.view.widget.CallLogItemLayout")
                                                        name("setThirdIcon")
                                                    }
                                                }
                                            }
                                        }
                                    }.firstOrNull()
                                }.getOrNull()

                                var cachedSubBadgeField: Field? = runCatching { subBadgeFieldData?.getFieldInstance(classLoader) }.getOrNull()
                                var cachedBadgeVisibleField: Field? = runCatching { isBadgeVisibleFieldData?.getFieldInstance(classLoader) }.getOrNull()
                                var cachedThirdIconField: Field? = runCatching { thirdIconFieldData?.getFieldInstance(classLoader) }.getOrNull()
                                var cachedCmcTabletField: Field? = null
                                var cachedLabeledField: Field? = null
                                var cachedExpandedField: Field? = null
                                var cachedActionDataField: Field? = null
                                var fieldsInitialized = false

                                xlog("OneUIX: callLogHelperClassData = ${callLogHelperClassData.name}, cMethod = ${cMethod?.name}")

                                if (cMethod != null) {
                                    xposedModule.hook(cMethod).intercept { chain ->
                                        val result = chain.proceed()
                                        val callLogViewItem = chain.args.getOrNull(0) ?: return@intercept result
                                        val callLogGroup = chain.args.getOrNull(1) ?: return@intercept result

                                        if (!fieldsInitialized) {
                                            val itemClass = callLogViewItem.javaClass
                                            if (cachedSubBadgeField == null) {
                                                cachedSubBadgeField = itemClass.declaredFields.firstOrNull { it.name == "j" || it.name == "f28950j" }
                                            }
                                            if (cachedBadgeVisibleField == null || cachedBadgeVisibleField?.name == "R" || cachedBadgeVisibleField?.name == "f28925R") {
                                                cachedBadgeVisibleField = itemClass.declaredFields.firstOrNull { it.name == "o" || it.name == "f28957o" } ?: cachedBadgeVisibleField
                                            }
                                            if (cachedCmcTabletField == null) {
                                                cachedCmcTabletField = itemClass.declaredFields.firstOrNull { it.name == "g0" || it.name == "f28946g0" }
                                            }
                                            if (cachedLabeledField == null) {
                                                cachedLabeledField = itemClass.declaredFields.firstOrNull { it.name == "n" || it.name == "f28956n" }
                                            }
                                            if (cachedExpandedField == null) {
                                                cachedExpandedField = itemClass.declaredFields.firstOrNull { it.name == "V" || it.name == "f28929V" }
                                            }
                                            if (cachedThirdIconField == null) {
                                                cachedThirdIconField = itemClass.declaredFields.firstOrNull { (it.name == "B" || it.name == "f28909B") && vClass?.isAssignableFrom(it.type) == true }
                                            }
                                            if (cachedActionDataField == null) {
                                                cachedActionDataField = itemClass.declaredFields.firstOrNull {
                                                    !Modifier.isStatic(it.modifiers) && runCatching {
                                                        it.isAccessible = true
                                                        val obj = it.get(callLogViewItem)
                                                        obj != null && (obj.javaClass.name.contains("ActionData") || obj.javaClass.interfaces.any { iface -> iface.name.contains("Action") } || obj.javaClass.name.endsWith(".f"))
                                                    }.getOrDefault(false)
                                                }
                                            }
                                            cachedSubBadgeField?.isAccessible = true
                                            cachedBadgeVisibleField?.isAccessible = true
                                            cachedCmcTabletField?.isAccessible = true
                                            cachedLabeledField?.isAccessible = true
                                            cachedExpandedField?.isAccessible = true
                                            cachedThirdIconField?.isAccessible = true
                                            cachedActionDataField?.isAccessible = true
                                            fieldsInitialized = true
                                            xlog("OneUIX: Resolved CallLogViewItem fields: badge=${cachedSubBadgeField?.name}, visible=${cachedBadgeVisibleField?.name}, cmc=${cachedCmcTabletField?.name}, labeled=${cachedLabeledField?.name}, thirdIcon=${cachedThirdIconField?.name}, actionData=${cachedActionDataField?.name}")
                                        }

                                        val baseCallLog = try {
                                            callLogGroup.javaClass.declaredMethods
                                                .firstOrNull { it.parameterTypes.isEmpty() && it.returnType != java.lang.Void.TYPE && it.returnType != java.lang.Boolean.TYPE }
                                                ?.invoke(callLogGroup)
                                        } catch (_: Throwable) { null }

                                        if (baseCallLog != null) {
                                            var isCmc = false
                                            for (field in baseCallLog.javaClass.declaredFields) {
                                                if (Modifier.isStatic(field.modifiers)) continue
                                                if (field.type != String::class.java) continue
                                                field.isAccessible = true
                                                val s = field.get(baseCallLog) as? String ?: continue
                                                if (s.isEmpty()) continue

                                                if (s.startsWith("CMC") || s.contains("mdecservice") || s.contains("cmc") || (field.name.contains("secCMC") && s.isNotEmpty())) {
                                                    isCmc = true
                                                    break
                                                }
                                            }

                                            if (isCmc) {
                                                cachedSubBadgeField?.set(callLogViewItem, "跨设备")
                                                cachedBadgeVisibleField?.setBoolean(callLogViewItem, true)
                                                cachedCmcTabletField?.setBoolean(callLogViewItem, true)
                                                cachedLabeledField?.setBoolean(callLogViewItem, false)
                                                cachedExpandedField?.setBoolean(callLogViewItem, false)

                                                if (cachedThirdIconField != null && vCtor != null) {
                                                    val helperInstance = chain.thisObject
                                                    val ctx = helperInstance?.javaClass?.declaredFields
                                                        ?.firstOrNull { Context::class.java.isAssignableFrom(it.type) }
                                                        ?.let {
                                                            it.isAccessible = true
                                                            it.get(helperInstance) as? Context
                                                        }
                                                    val iconPdId = ctx?.resources?.getIdentifier("icon_device_pd", "drawable", "com.samsung.android.dialer") ?: 0x7f0802da
                                                    val tintColorId = ctx?.resources?.getIdentifier("call_log_indicator_icon_tint_color", "color", "com.samsung.android.dialer") ?: 0x7f060168
                                                    val tintedIcon = vCtor.newInstance(iconPdId, tintColorId)
                                                    cachedThirdIconField?.set(callLogViewItem, tintedIcon)
                                                }

                                                val actionData = cachedActionDataField?.get(callLogViewItem)
                                                if (actionData != null) {
                                                    for (af in actionData.javaClass.declaredFields) {
                                                        if (af.type == java.lang.Boolean.TYPE && (af.name == "s" || af.name == "f14022s")) {
                                                            af.isAccessible = true
                                                            af.setBoolean(actionData, true)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        result
                                    }
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        logError("setCallAndTextDeviceType: DexKit resolution failed", t)
                    }
                }
            }

            // 3. 在系统核心服务 (system_server / Telecom) 中放行并识别 CMC 呼叫账户
            if (packageName == Package.ANDROID || packageName == Package.TELECOM) {
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
                    val registrarClass = classLoader.findClassOrNull("com.android.server.telecom.PhoneAccountRegistrar")
                    if (registrarClass != null) {
                        registrarClass.declaredMethods
                            .filter { it.name == "getCallCapablePhoneAccounts" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val result = chain.proceed()
                                    val list = (result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return@intercept result
                                    if (list.none { it.id?.contains("CMC") == true }) {
                                        ArrayList(list).apply { add(cmcHandle) }
                                    } else {
                                        list
                                    }
                                }
                            }

                        registrarClass.declaredMethods
                            .filter { it.name == "getPhoneAccount" || it.name == "getPhoneAccountUnchecked" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val handle = chain.args.firstOrNull() as? PhoneAccountHandle
                                    if (handle?.id?.contains("CMC") == true) {
                                        mockAccount(handle)
                                    } else {
                                        chain.proceed()
                                    }
                                }
                            }
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: PhoneAccountRegistrar hooks failed", t)
                }

                // 3.2 拦截 CallsManager: 找到外拨账户以及构建可能账户时包含 CMC
                try {
                    val callsManagerClass = classLoader.findClassOrNull("com.android.server.telecom.CallsManager")
                    if (callsManagerClass != null) {
                        callsManagerClass.declaredMethods
                            .filter { it.name == "findOutgoingCallPhoneAccount" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val callObj = chain.args.firstOrNull { it != null && it.javaClass.name.endsWith(".Call") }
                                    val callInitHandle = callObj?.let {
                                        try {
                                            it.reflect.callAs<PhoneAccountHandle>("getInitiatingAccountHandle")
                                                ?: it.reflect.callAs<PhoneAccountHandle>("getTargetPhoneAccount")
                                        } catch (_: Throwable) { null }
                                    }
                                    val handleArg = chain.args.filterIsInstance<PhoneAccountHandle>().firstOrNull()
                                    val cmc = listOfNotNull(handleArg, callInitHandle).firstOrNull { it.id?.contains("CMC") == true }
                                    if (cmc != null) {
                                        CompletableFuture.completedFuture(listOf(cmc))
                                    } else {
                                        chain.proceed()
                                    }
                                }
                            }

                        callsManagerClass.declaredMethods
                            .filter { it.name == "constructPossiblePhoneAccounts" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val result = chain.proceed()
                                    val list = (result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return@intercept result
                                    if (list.none { it.id?.contains("CMC") == true }) {
                                        ArrayList(list).apply { add(cmcHandle) }
                                    } else {
                                        list
                                    }
                                }
                            }

                        val samsungRegistrarClass = classLoader.findClassOrNull(
                            "com.samsung.server.telecom.basiccall.callsmanager.phoneaccount.SamsungPhoneAccountHandleRegistrar"
                        )
                        samsungRegistrarClass?.declaredMethods
                            ?.filter { it.name == "constructPossiblePhoneAccounts" }
                            ?.forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val result = chain.proceed()
                                    val list = (result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return@intercept result
                                    if (list.none { it.id?.contains("CMC") == true }) {
                                        ArrayList(list).apply { add(cmcHandle) }
                                    } else {
                                        list
                                    }
                                }
                            }

                        val samsungNotifierClass = classLoader.findClassOrNull(
                            "com.samsung.server.telecom.basiccall.callsmanager.phoneaccount.SamsungPhoneAccountHandleNotifier"
                        )
                        samsungNotifierClass?.declaredMethods
                            ?.filter { it.name == "constructPossiblePhoneAccounts" }
                            ?.forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val result = chain.proceed()
                                    val list = (result as? List<*>)?.filterIsInstance<PhoneAccountHandle>() ?: return@intercept result
                                    if (list.none { it.id?.contains("CMC") == true }) {
                                        ArrayList(list).apply { add(cmcHandle) }
                                    } else {
                                        list
                                    }
                                }
                            }

                        callsManagerClass.declaredMethods
                            .filter { it.name == "onCallRedirectionComplete" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val callObj = chain.args.firstOrNull { it != null && it.javaClass.name.endsWith(".Call") }
                                    val callInitHandle = callObj?.let {
                                        try {
                                            it.reflect.callAs<PhoneAccountHandle>("getInitiatingAccountHandle")
                                                ?: it.reflect.callAs<PhoneAccountHandle>("getTargetPhoneAccount")
                                        } catch (_: Throwable) { null }
                                    }
                                    if (callInitHandle?.id?.contains("CMC") == true && chain.args.size > 2) {
                                        if ((chain.args[2] as? PhoneAccountHandle)?.id?.contains("CMC") != true) {
                                            val newArgs = chain.args.toTypedArray()
                                            newArgs[2] = callInitHandle
                                            return@intercept chain.proceed(newArgs)
                                        }
                                    }
                                    chain.proceed()
                                }
                            }
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: CallsManager hooks failed", t)
                }

                // 3.3 拦截 Call.setTargetPhoneAccount: 防止 CMC 目标账号被强行篡改为本地实体 SIM
                try {
                    val callClass = classLoader.findClassOrNull("com.android.server.telecom.Call")
                    if (callClass != null) {
                        callClass.declaredMethods
                            .filter { it.name == "setTargetPhoneAccount" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val newHandle = chain.args.firstOrNull() as? PhoneAccountHandle
                                    val target = try {
                                        chain.thisObject.reflect.callAs<PhoneAccountHandle>("getTargetPhoneAccount")
                                    } catch (_: Throwable) { null }
                                    val init = try {
                                        chain.thisObject.reflect.callAs<PhoneAccountHandle>("getInitiatingAccountHandle")
                                    } catch (_: Throwable) { null }

                                    val isCmc = (target?.id?.contains("CMC") == true) || (init?.id?.contains("CMC") == true)
                                    if (isCmc && newHandle?.id?.contains("CMC") != true) {
                                        val newArgs = chain.args.toTypedArray()
                                        newArgs[0] = target ?: init ?: cmcHandle
                                        chain.proceed(newArgs)
                                    } else {
                                        chain.proceed()
                                    }
                                }
                            }

                        callClass.declaredMethods
                            .filter { it.name == "getIntentExtras" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val target = try { chain.thisObject.reflect.callAs<PhoneAccountHandle>("getTargetPhoneAccount") } catch (_: Throwable) { null }
                                    val init = try { chain.thisObject.reflect.callAs<PhoneAccountHandle>("getInitiatingAccountHandle") } catch (_: Throwable) { null }
                                    val result = chain.proceed()
                                    if (target?.id?.contains("CMC") == true || init?.id?.contains("CMC") == true) {
                                        val extras = (result as? Bundle) ?: Bundle()
                                        extras.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        val oemExtras = extras.getBundle("android.telephony.ims.extra.OEM_EXTRAS") ?: Bundle()
                                        oemExtras.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        extras.putBundle("android.telephony.ims.extra.OEM_EXTRAS", oemExtras)
                                        extras
                                    } else {
                                        result
                                    }
                                }
                            }
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: Call hooks failed", t)
                }

                // 3.4 让 Telecom 的 SamsungMultiDeviceCallInfo 识别 CMC_TYPE = 2
                try {
                    val multiDeviceInfoClass = classLoader.findClassOrNull("com.samsung.server.telecom.basiccall.call.info.SamsungMultiDeviceCallInfo")
                    multiDeviceInfoClass?.declaredMethods
                        ?.filter { it.name == "getCmcType" }
                        ?.forEach { m ->
                            xposedModule.hook(m).intercept { chain ->
                                val call = chain.thisObject.reflect.get("call")
                                val target = try { call?.reflect?.callAs<PhoneAccountHandle>("getTargetPhoneAccount") } catch (_: Throwable) { null }
                                val init = try { call?.reflect?.callAs<PhoneAccountHandle>("getInitiatingAccountHandle") } catch (_: Throwable) { null }
                                if (target?.id?.contains("CMC") == true || init?.id?.contains("CMC") == true) {
                                    2
                                } else {
                                    chain.proceed()
                                }
                            }
                        }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: SamsungMultiDeviceCallInfo.getCmcType failed", t)
                }
            }

            // 4. 在电信连接服务 (com.android.phone) 中允许为手机注册副设备 CMC 账户并放行呼叫
            if (packageName == Package.PHONE) {
                // 4.1 防止 PhoneUtils 与 SamsungPhoneAccountHandleUtil 将 CMC 账号映射为本地卡 1 (SIM 1)
                try {
                    val phoneUtilsClass = classLoader.findClassOrNull("com.android.phone.PhoneUtils")
                    val phoneFactoryClass = classLoader.findClassOrNull("com.android.internal.telephony.PhoneFactory")
                    if (phoneUtilsClass != null) {
                        phoneUtilsClass.declaredMethods
                            .filter { it.name == "getSubIdForPhoneAccountHandle" && it.parameterTypes.contentEquals(arrayOf(PhoneAccountHandle::class.java)) }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val handle = chain.args.firstOrNull() as? PhoneAccountHandle
                                    if (handle?.id?.contains("CMC") == true) {
                                        -1
                                    } else {
                                        chain.proceed()
                                    }
                                }
                            }

                        phoneUtilsClass.declaredMethods
                            .filter { it.name == "getPhoneForPhoneAccountHandle" && it.parameterTypes.contentEquals(arrayOf(PhoneAccountHandle::class.java)) }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val handle = chain.args.firstOrNull() as? PhoneAccountHandle
                                    if (handle?.id?.contains("CMC") == true) {
                                        try {
                                            phoneFactoryClass?.reflect?.call("getDefaultPhone") ?: chain.proceed()
                                        } catch (_: Throwable) {
                                            chain.proceed()
                                        }
                                    } else {
                                        chain.proceed()
                                    }
                                }
                            }
                    }

                    val accountUtilClass = classLoader.findClassOrNull(
                        "com.samsung.telephony.phone.basic.phoneaccounthandle.SamsungPhoneAccountHandleUtil"
                    )
                    accountUtilClass?.declaredMethods
                        ?.filter { it.name == "getPhoneFromSubId" }
                        ?.forEach { m ->
                            xposedModule.hook(m).intercept { chain ->
                                val subId = chain.args.firstOrNull { it is String } as? String
                                if (subId?.contains("CMC") == true) {
                                    try {
                                        phoneFactoryClass?.reflect?.call("getDefaultPhone") ?: chain.proceed()
                                    } catch (_: Throwable) {
                                        chain.proceed()
                                    }
                                } else {
                                    chain.proceed()
                                }
                            }
                        }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: PhoneUtils hook failed", t)
                }

                // 4.2 欺骗 SamsungSdPstnAccountRegistry 允许副机 CMC 账户注册
                try {
                    val registryClass = classLoader.findClassOrNull("com.samsung.telephony.services.advanced.cmc.SamsungSdPstnAccountRegistry")
                    if (registryClass != null) {
                        registryClass.declaredMethods.filter { it.name == "isVoiceCapable" }.forEach { xposedModule.hook(it).intercept { false } }
                        registryClass.declaredMethods.filter { it.name == "ensureCmcPhoneAccountRegistered" }.forEach { xposedModule.hook(it).intercept { true } }
                        registryClass.declaredMethods.filter { it.name == "isPdCmcAvailable" }.forEach { xposedModule.hook(it).intercept { true } }
                        registryClass.declaredMethods.filter { it.name == "hasActivatedSimSlotOnPd" }.forEach { xposedModule.hook(it).intercept { true } }
                        registryClass.declaredMethods.filter { it.name == "shouldRemovePstnCapabilities" }.forEach { xposedModule.hook(it).intercept { false } }
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: SamsungSdPstnAccountRegistry failed", t)
                }

                // 4.3 拦截 TelephonyConnectionService.adjustAccountHandle 防止账号被篡改
                try {
                    val serviceClass = classLoader.findClassOrNull("com.android.services.telephony.TelephonyConnectionService")
                    if (serviceClass != null) {
                        serviceClass.declaredMethods
                            .filter { it.name == "adjustAccountHandle" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val handle = chain.args.getOrNull(1) as? PhoneAccountHandle
                                    if (handle?.id?.contains("CMC") == true) {
                                        handle
                                    } else {
                                        chain.proceed()
                                    }
                                }
                            }

                        // 4.4 注入 CMC_TYPE=2 到 ConnectionRequest 与 Bundle 中，引导通话路由至 IMS PS 域
                        serviceClass.declaredMethods
                            .filter { it.name == "onCreateOutgoingConnection" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val handle = chain.args.firstOrNull { it is PhoneAccountHandle } as? PhoneAccountHandle
                                    val request = chain.args.firstOrNull { it != null && it.javaClass.name.endsWith("ConnectionRequest") }
                                    val reqHandle = request?.let {
                                        try { it.reflect.callAs<PhoneAccountHandle>("getAccountHandle") } catch (_: Throwable) { null }
                                    }
                                    if (handle?.id?.contains("CMC") == true || reqHandle?.id?.contains("CMC") == true) {
                                        val extras = request?.let {
                                            try { it.reflect.callAs<Bundle>("getExtras") } catch (_: Throwable) { null }
                                        }
                                        if (extras != null) {
                                            extras.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                            val oem = extras.getBundle("android.telephony.ims.extra.OEM_EXTRAS") ?: Bundle()
                                            oem.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                            extras.putBundle("android.telephony.ims.extra.OEM_EXTRAS", oem)
                                        }
                                    }
                                    chain.proceed()
                                }
                            }

                        serviceClass.declaredMethods
                            .filter { it.name == "placeOutgoingConnection" }
                            .forEach { m ->
                                xposedModule.hook(m).intercept { chain ->
                                    val bundle = chain.args.filterIsInstance<Bundle>().firstOrNull()
                                    val conn = chain.args.firstOrNull { it != null && it.javaClass.name.contains("TelephonyConnection") }
                                    val connHandle = conn?.let {
                                        try { it.reflect.callAs<PhoneAccountHandle>("getPhoneAccountHandle") } catch (_: Throwable) { null }
                                    }
                                    val isCmc = connHandle?.id?.contains("CMC") == true ||
                                                bundle?.containsKey("com.samsung.telephony.extra.CMC_TYPE") == true
                                    if (isCmc && bundle != null) {
                                        bundle.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        val oem = bundle.getBundle("android.telephony.ims.extra.OEM_EXTRAS") ?: Bundle()
                                        oem.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        bundle.putBundle("android.telephony.ims.extra.OEM_EXTRAS", oem)
                                    }
                                    chain.proceed()
                                }
                            }
                    }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: TelephonyConnectionService hooks failed", t)
                }

                // 4.5 强制 SemCallTrackerHelper.useMdecEnabled 返回 true
                try {
                    val helperClass = classLoader.findClassOrNull("com.android.internal.telephony.SemCallTrackerHelper")
                    helperClass?.declaredMethods
                        ?.filter { it.name == "useMdecEnabled" }
                        ?.forEach { xposedModule.hook(it).intercept { true } }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: SemCallTrackerHelper.useMdecEnabled failed", t)
                }

                // 4.6 拦截 GsmCdmaPhone.dial 确保包含 CMC_TYPE=2，使拨号直接走 IMS PS 域
                try {
                    val phoneClass = classLoader.findClassOrNull("com.android.internal.telephony.GsmCdmaPhone")
                    phoneClass?.declaredMethods
                        ?.filter { it.name == "dial" }
                        ?.forEach { m ->
                            xposedModule.hook(m).intercept { chain ->
                                val dialArgs = chain.args.getOrNull(1)
                                val intentExtras = dialArgs?.let {
                                    try { it.reflect.getAs<Bundle>("intentExtras") } catch (_: Throwable) { null }
                                }
                                if (intentExtras != null) {
                                    val oem = intentExtras.getBundle("android.telephony.ims.extra.OEM_EXTRAS")
                                    if (intentExtras.containsKey("com.samsung.telephony.extra.CMC_TYPE") || oem?.containsKey("com.samsung.telephony.extra.CMC_TYPE") == true) {
                                        intentExtras.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        val newOem = oem ?: Bundle()
                                        newOem.putInt("com.samsung.telephony.extra.CMC_TYPE", 2)
                                        intentExtras.putBundle("android.telephony.ims.extra.OEM_EXTRAS", newOem)
                                    }
                                }
                                chain.proceed()
                            }
                        }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: GsmCdmaPhone.dial failed", t)
                }

                // 4.7 拦截 SamsungCmcRepositoryImpl.isCmcTypeSd 返回 true
                try {
                    val cmcRepoClass = classLoader.findClassOrNull("com.samsung.telephony.model.cmc.SamsungCmcRepositoryImpl")
                    cmcRepoClass?.declaredMethods
                        ?.filter { it.name == "isCmcTypeSd" }
                        ?.forEach { m ->
                            xposedModule.hook(m).intercept { chain ->
                                val arg = chain.args.firstOrNull()
                                if (arg is Bundle) {
                                    val oem = arg.getBundle("android.telephony.ims.extra.OEM_EXTRAS")
                                    if (arg.getInt("com.samsung.telephony.extra.CMC_TYPE") == 2 || oem?.getInt("com.samsung.telephony.extra.CMC_TYPE") == 2) {
                                        return@intercept true
                                    }
                                } else if (arg != null) {
                                    val connExtras = try { arg.reflect.callAs<Bundle>("getConnectionExtras") } catch (_: Throwable) { null }
                                    val oem = connExtras?.getBundle("android.telephony.ims.extra.OEM_EXTRAS")
                                    if (connExtras?.getInt("com.samsung.telephony.extra.CMC_TYPE") == 2 || oem?.getInt("com.samsung.telephony.extra.CMC_TYPE") == 2) {
                                        return@intercept true
                                    }
                                }
                                chain.proceed()
                            }
                        }
                } catch (t: Throwable) {
                    logError("setCallAndTextDeviceType: SamsungCmcRepositoryImpl.isCmcTypeSd failed", t)
                }
            }

            // 5. 在 IMS 服务 (com.sec.imsservice) 中放行副设备 (SD) CMC 呼叫建立
            if (packageName == Package.IMS_SERVICE) {
                try {
                    val volteNotifierClass = classLoader.findClassOrNull(
                        "com.sec.internal.ims.servicemodules.volte2.VolteNotifier"
                    )
                    volteNotifierClass?.declaredMethods
                        ?.filter { it.name == "getCmcCallEventListenerSize" }
                        ?.forEach { m ->
                            xposedModule.hook(m).intercept { chain ->
                                val size = (chain.proceed() as? Int) ?: 0
                                if (size <= 0) 1 else size
                            }
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
            val instance = baseCallLogClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

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
            val callLogGroup = callLogGroupClass.reflect.new(true, true)

            val callLogViewItemClass = callLogViewItemClassData.getInstance(classLoader)
            val callLogViewItem = callLogViewItemClass.reflect.new(callLogGroup)

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
                versionCode = longVersionCode,
                geoCodedLocationField = DexField(geoCodedLocation).serialize(),
                subTextField = DexField(subText).serialize(),
                setSubTextMethod = setSubText.toDexMethod().serialize(),
            )
        }
    }
}
