package de.robv.android.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method

object XposedBridge {
    @Volatile
    private var moduleInstance: XposedModule? = null

    fun init(module: XposedModule) {
        moduleInstance = module
    }

    val currentModule: XposedModule?
        get() = moduleInstance

    fun hookMethod(hookMethod: Member, callback: XC_MethodHook): Any? {
        val module = moduleInstance ?: run {
            Log.e("[OneUIX]", "XposedBridge: currentModule is null when hooking $hookMethod")
            return null
        }
        return when (hookMethod) {
            is Method -> {
                module.hook(hookMethod).intercept { chain ->
                    handleHook(hookMethod, callback, chain)
                }
            }
            is Constructor<*> -> {
                module.hook(hookMethod).intercept { chain ->
                    handleHook(hookMethod, callback, chain)
                }
            }
            else -> {
                Log.e("[OneUIX]", "Cannot hook unsupported Member: $hookMethod")
                null
            }
        }
    }

    private fun handleHook(
        member: Member,
        callback: XC_MethodHook,
        chain: io.github.libxposed.api.XposedInterface.Chain
    ): Any? {
        val param = XC_MethodHook.MethodHookParam().apply {
            this.method = member
            this.thisObject = chain.thisObject
            this.args = chain.args
        }

        try {
            callback.beforeHookedMethod(param)
        } catch (t: Throwable) {
            log(t)
        }

        if (param.returnEarly) {
            if (param.hasThrowable) throw param.throwable!!
            return param.result
        }

        var result: Any? = null
        var thrown: Throwable? = null
        try {
            result = chain.proceed(param.args)
            param.result = result
        } catch (t: Throwable) {
            thrown = t
            param.throwable = t
        }

        try {
            callback.afterHookedMethod(param)
        } catch (t: Throwable) {
            log(t)
        }

        if (param.hasThrowable && param.throwable !== thrown) {
            throw param.throwable!!
        } else if (thrown != null && param.throwable === thrown) {
            throw thrown
        }
        return param.result
    }

    fun hookAllMethods(hookClass: Class<*>, methodName: String, callback: XC_MethodHook): Set<Any> {
        val results = mutableSetOf<Any>()
        for (m in hookClass.declaredMethods) {
            if (m.name == methodName) {
                hookMethod(m, callback)?.let { results.add(it) }
            }
        }
        return results
    }

    fun hookAllConstructors(hookClass: Class<*>, callback: XC_MethodHook): Set<Any> {
        val results = mutableSetOf<Any>()
        for (c in hookClass.declaredConstructors) {
            hookMethod(c, callback)?.let { results.add(it) }
        }
        return results
    }

    fun log(text: String) {
        val mod = moduleInstance
        if (mod != null) {
            mod.log(Log.INFO, null, text)
        } else {
            Log.i("[OneUIX]", text)
        }
    }

    fun log(t: Throwable) {
        val mod = moduleInstance
        if (mod != null) {
            mod.log(Log.ERROR, null, t.message ?: "", t)
        } else {
            Log.e("[OneUIX]", t.message, t)
        }
    }
}
