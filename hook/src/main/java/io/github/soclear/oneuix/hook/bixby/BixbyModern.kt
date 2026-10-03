package io.github.soclear.oneuix.hook.bixby

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Modifier

object BixbyModern {
    context(xposedModule: XposedModule)
    fun installAgent(context: Context, onText: (String) -> Unit): () -> String {
        val config = context.getHookConfig(File(context.filesDir, "OneUIX-Bixby-1.json")) {
            resolveHooks()
        } ?: error("Bixby wake-up methods could not be resolved uniquely")
        val loader = context.classLoader
        val getter = DexMethod(config.keywordGetter).getMethodInstance(loader).apply {
            isAccessible = true
        }
        val resourceState = DexMethod(config.resourceState).getMethodInstance(loader).apply {
            isAccessible = true
        }
        val callback = DexMethod(config.resourceCallback).getMethodInstance(loader)
        val presenterClass = loader.loadClass(config.presenterClass)
        check(Modifier.isStatic(getter.modifiers) && getter.parameterCount == 0 && getter.returnType == String::class.java)
        check(Modifier.isStatic(resourceState.modifiers) && resourceState.parameterCount == 0 && resourceState.returnType == Int::class.javaPrimitiveType)
        check(callback.parameterTypes.contentEquals(arrayOf(Any::class.java)))

        xposedModule.hook(getter).intercept { chain ->
            val result = chain.proceed()
            (result as? String)?.takeIf(String::isNotBlank)?.let(onText)
            result
        }
        xposedModule.hook(callback).intercept { chain ->
            val result = chain.args[0] as? Int ?: return@intercept chain.proceed()
            if (result != -1 || chain.thisObject == null) return@intercept chain.proceed()
            val corrected = try {
                if (capturedPresenter(chain.thisObject!!, presenterClass) != null) {
                    reconcileResourceResult(result, resourceState.invoke(null) as? Int)
                } else result
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to verify custom wake-up resource state", t)
                result
            }
            if (corrected != result) {
                Log.i(TAG, "Reconciled stale custom wake-up resource failure")
                val args = chain.args.toTypedArray()
                args[0] = corrected
                chain.proceed(args)
            } else {
                chain.proceed()
            }
        }
        Log.i(TAG, "Native enrollment retained; keyword=${config.keywordGetter}, resource=${config.resourceState}, callback=${config.resourceCallback}")
        return { (getter.invoke(null) as? String).orEmpty() }
    }

    @Serializable
    private data class Config(
        override val versionCode: Long,
        val keywordGetter: String,
        val resourceState: String,
        val resourceCallback: String,
        val presenterClass: String,
    ) : HookConfig

    private fun Context.resolveHooks(): Config? {
        System.loadLibrary("dexkit")
        return DexKitBridge.create(classLoader, true).use { bridge ->
            val keywordGetter = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "java.lang.String"
                    usingStrings("getCustomKeyword - bixbyLocale: ")
                }
            }.singleOrNull() ?: return null
            val resourceState = bridge.findMethod {
                matcher {
                    declaredClass(keywordGetter.toDexMethod().className)
                    paramCount = 0
                    returnType = "int"
                    usingStrings("customWakeupResourceState", "getCustomWakeupResourceState---")
                }
            }.singleOrNull() ?: return null
            val resourceCallback = bridge.findMethod {
                matcher {
                    paramTypes("java.lang.Object")
                    returnType = "java.lang.Object"
                    usingStrings("VoiceWakeupOptionsPresenter", "downloadCustomWakeupResource : ")
                }
            }.singleOrNull() ?: return null
            val selectPhrase = bridge.findMethod {
                matcher {
                    paramTypes("java.lang.String")
                    returnType = "void"
                    usingStrings("VoiceWakeupOptionsPresenter", "phrase")
                }
            }.singleOrNull() ?: return null
            Config(
                versionCode = longVersionCode,
                keywordGetter = keywordGetter.toDexMethod().serialize(),
                resourceState = resourceState.toDexMethod().serialize(),
                resourceCallback = resourceCallback.toDexMethod().serialize(),
                presenterClass = selectPhrase.toDexMethod().className,
            )
        }
    }

    private const val TAG = "OneUIX-Bixby"
}
