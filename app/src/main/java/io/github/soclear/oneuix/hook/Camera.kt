package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.log
import io.github.soclear.oneuix.hook.util.logError
import io.github.soclear.oneuix.hook.util.longVersionCode
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Modifier
import java.util.EnumMap

object Camera {
    private const val HOOK_CONFIG_FILE_NAME = "HookConfig.json"

    fun setBooleanFeature(
        loadPackageParam: LoadPackageParam,
        supportAllMenu: Boolean = true,
        disableTemperatureCheck: Boolean = false,
    ) {
        if (loadPackageParam.packageName != Package.CAMERA) {
            return
        }
        afterAttach {
            val file = File(filesDir, HOOK_CONFIG_FILE_NAME)
            val hookConfig = getHookConfig(file) { getHookConfigFromDexKit() }
            if (hookConfig != null) {
                setBooleanFeature(hookConfig, supportAllMenu, disableTemperatureCheck)
            }
        }
    }

    private fun Context.setBooleanFeature(
        hookConfig: CameraHookConfig,
        supportAllMenu: Boolean = true,
        disableTemperatureCheck: Boolean = false,
    ) {
        if (!supportAllMenu && !disableTemperatureCheck) {
            return
        }
        val enumValueOfMethod = DexMethod(hookConfig.booleanFeatureEnumValueOfMethod)
            .getMethodInstance(classLoader)
        val supportShutterSoundMenuEnum = enumValueOfMethod(
            null, BooleanFeatureEnum.SUPPORT_SHUTTER_SOUND_MENU.name
        )

        val callback = object : XC_MethodHook() {
            fun getBooleanFeatureMap(param: MethodHookParam): Any =
                hookConfig.booleanFeatureMapField
                    ?.let {
                        DexField(it)
                            .getFieldInstance(classLoader)
                            .get(param.thisObject)
                    }
                    ?: param.thisObject
                        .javaClass
                        .declaredFields
                        .filter { EnumMap::class.java.isAssignableFrom(it.type) }
                        .first {
                            it.isAccessible = true
                            val enumMap = it.get(param.thisObject) as EnumMap<*, *>
                            enumMap[supportShutterSoundMenuEnum] is Boolean
                        }
                        .also {
                            val newHookConfig =
                                hookConfig.copy(booleanFeatureMapField = DexField(it).serialize())
                            val string = Json.encodeToString(newHookConfig)
                            File(filesDir, HOOK_CONFIG_FILE_NAME).writeText(string)
                        }
                        .get(param.thisObject)

            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val booleanFeatureMap = getBooleanFeatureMap(param) as MutableMap<Any, Boolean>
                    if (supportAllMenu) {
                        val allMenuEnums = listOf(
                            BooleanFeatureEnum.SUPPORT_SHUTTER_SOUND_MENU,
                            BooleanFeatureEnum.SUPPORT_AUTO_HDR_MENU,
                            BooleanFeatureEnum.SUPPORT_LOG_VIDEO,
                            BooleanFeatureEnum.SUPPORT_FRONT_LOG_VIDEO,
                            BooleanFeatureEnum.SUPPORT_MOTION_PHOTO_CAPTURE_MODE,
                            BooleanFeatureEnum.SUPPORT_MOTION_PHOTO_BEFORE_AND_AFTER_AS_DEFAULT_CAPTURE_MODE,
                            BooleanFeatureEnum.SUPPORT_FRAME_WATERMARK,
                            BooleanFeatureEnum.SUPPORT_WATERMARK_FONT_SAMSUNG_SHARP_SANS
                        )
                        for (myEnum in allMenuEnums) {
                            val enum = try {
                                enumValueOfMethod(null, myEnum.name)
                            } catch (_: Throwable) {
                                continue
                            }

                            booleanFeatureMap[enum] = true
                        }
                    }
                    if (disableTemperatureCheck) {
                        val supportThermistorTemperatureEnum = try {
                            enumValueOfMethod(null, BooleanFeatureEnum.SUPPORT_THERMISTOR_TEMPERATURE.name)
                        } catch (_: Throwable) { null }
                        if (supportThermistorTemperatureEnum != null) {
                            booleanFeatureMap[supportThermistorTemperatureEnum] = false
                        }
                    }
                } catch (t: Throwable) {
                    logError("setBooleanFeature callback failed", t)
                }
            }
        }
        try {
            if (hookConfig.initializeBooleanFeatureMapMethod.contains("<init>")) {
                val clazz =
                    DexClass(hookConfig.deviceFeatureClass).getInstance(classLoader)
                XposedHelpers.findAndHookConstructor(clazz, callback)
            } else {
                XposedBridge.hookMethod(
                    DexMethod(hookConfig.initializeBooleanFeatureMapMethod)
                        .getMethodInstance(classLoader),
                    callback
                )
            }
        } catch (t: Throwable) {
            logError("setBooleanFeature failed", t)
        }
    }

    @Serializable
    private data class CameraHookConfig(
        override val versionCode: Long,
        val deviceFeatureClass: String,
        val initializeBooleanFeatureMapMethod: String,
        val booleanFeatureMapField: String?,
        val booleanFeatureEnumValueOfMethod: String,
    ) : HookConfig

    private enum class BooleanFeatureEnum {
        SUPPORT_SHUTTER_SOUND_MENU,
        SUPPORT_AUTO_HDR_MENU,
        SUPPORT_THERMISTOR_TEMPERATURE,
        SUPPORT_LOG_VIDEO,
        SUPPORT_FRONT_LOG_VIDEO,
        SUPPORT_MOTION_PHOTO_CAPTURE_MODE,
        SUPPORT_MOTION_PHOTO_BEFORE_AND_AFTER_AS_DEFAULT_CAPTURE_MODE,
        SUPPORT_FRAME_WATERMARK,
        SUPPORT_WATERMARK_FONT_SAMSUNG_SHARP_SANS,
    }

    private const val WATERMARK_HOOK_CONFIG_FILE_NAME = "WatermarkHookConfig.json"

    fun supportFrameWatermark(
        loadPackageParam: LoadPackageParam,
        supportFrameWatermark: Boolean,
        supportBodyBeauty: Boolean,
    ) {
        if (loadPackageParam.packageName != Package.CAMERA) return

        afterAttach {
            val hookConfig = getHookConfig(File(filesDir, WATERMARK_HOOK_CONFIG_FILE_NAME)) {
                getWatermarkHookConfigFromDexKit()
            } ?: return@afterAttach

            // 根据用户选择构建启用列表
            val enableFeatures = mutableSetOf<String>()
            if (supportFrameWatermark) {
                enableFeatures.add("SUPPORT_FRAME_WATERMARK")
                enableFeatures.add("SUPPORT_WATERMARK_FONT_SAMSUNG_SHARP_SANS")
            }
            if (supportBodyBeauty) enableFeatures.add("SUPPORT_BODY_BEAUTY")

            if (enableFeatures.isEmpty()) return@afterAttach

            try {
                val deviceFeatureClass = XposedHelpers.findClass(hookConfig.className, classLoader)

                // 获取所有 Map 类型的字段
                val mapFields = deviceFeatureClass.declaredFields
                    .filter { it.type.name.contains("Map") || it.type.name.contains("HashMap") }
                    .onEach { it.isAccessible = true }

                // 设置 Map 中的特性值
                fun setFeatureInMap(map: MutableMap<*, *>, features: Set<String>) {
                    for (key in map.keys) {
                        if (key?.toString() in features) {
                            @Suppress("UNCHECKED_CAST")
                            (map as MutableMap<Any?, Boolean>)[key] = true
                        }
                    }
                }

                // Hook 初始化方法
                deviceFeatureClass.declaredMethods
                    .filter { it.returnType == Void.TYPE && it.parameterTypes.isEmpty() }
                    .forEach { method ->
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                mapFields.forEach { field ->
                                    (field.get(param.thisObject) as? MutableMap<*, *>)
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.let { setFeatureInMap(it, enableFeatures) }
                                }
                            }
                        })
                    }
            } catch (t: Throwable) {
                logError("supportFrameWatermark failed", t)
            }
        }
    }

    @Serializable
    private data class WatermarkHookConfig(
        override val versionCode: Long,
        val className: String,
    ) : HookConfig

    private fun Context.getWatermarkHookConfigFromDexKit(): WatermarkHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val excludes = listOf(
                "androidx", "android.support", "camera.samsung.smartscan",
                "co.polarr", "dagger.android", "vizinsight.atl", "kotlin"
            )

            // 通过 initializeBooleanFeatureMap 字符串查找 DeviceFeature 类
            return bridge.findClass {
                excludePackages(excludes)
                matcher { usingStrings("initializeBooleanFeatureMap") }
            }.firstOrNull()?.let {
                WatermarkHookConfig(longVersionCode, it.name)
            }
        }
    }



    private fun Context.getHookConfigFromDexKit(): CameraHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val excludes = listOf("androidx", "camera", "co", "com", "kotlin", "vizinsight")
            val usingString = "initializeBooleanFeatureMap : Tag size = "

            val deviceFeatureClassData = bridge.findClass {
                // class DeviceFeature
                excludePackages(excludes)
                matcher {
                    usingStrings(usingString)
                }
            }.singleOrNull() ?: return null

            val initializeBooleanFeatureMapMethodData = deviceFeatureClassData.findMethod {
                matcher {
                    returnType = "void"
                    paramCount = 0
                    usingStrings(usingString)
                }
            }.singleOrNull() ?: return null

            val booleanFeatureMapFieldData = deviceFeatureClassData.findField {
                matcher {
                    modifiers = Modifier.FINAL
                    type(EnumMap::class.java)
                    addReadMethod(initializeBooleanFeatureMapMethodData.descriptor)
                }
            }.singleOrNull()

            val booleanTagValueOfMethodData = bridge.findClass {
                // enum BooleanTag
                excludePackages(excludes)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    superClass = "java.lang.Enum"
                    usingStrings(
                        BooleanFeatureEnum.SUPPORT_SHUTTER_SOUND_MENU.name,
                        BooleanFeatureEnum.SUPPORT_AUTO_HDR_MENU.name,
                    )
                }
            }.findMethod {
                matcher { name = "valueOf" }
            }.singleOrNull() ?: return null

            return CameraHookConfig(
                versionCode = longVersionCode,
                deviceFeatureClass = deviceFeatureClassData.toDexClass().serialize(),
                initializeBooleanFeatureMapMethod = initializeBooleanFeatureMapMethodData.toDexMethod()
                    .serialize(),
                booleanFeatureMapField = booleanFeatureMapFieldData?.toDexField()?.serialize(),
                booleanFeatureEnumValueOfMethod = booleanTagValueOfMethodData.toDexMethod()
                    .serialize(),
            )
        }
    }
}
