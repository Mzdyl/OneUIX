package de.robv.android.xposed

abstract class XC_MethodReplacement(priority: Int = PRIORITY_DEFAULT) : XC_MethodHook(priority) {
    abstract fun replaceHookedMethod(param: MethodHookParam): Any?

    final override fun beforeHookedMethod(param: MethodHookParam) {
        try {
            val result = replaceHookedMethod(param)
            param.result = result
        } catch (t: Throwable) {
            param.throwable = t
        }
    }

    final override fun afterHookedMethod(param: MethodHookParam) {}

    companion object {
        @JvmField
        val DO_NOTHING: XC_MethodReplacement = object : XC_MethodReplacement(PRIORITY_HIGHEST) {
            override fun replaceHookedMethod(param: MethodHookParam): Any? = null
        }

        @JvmStatic
        fun returnConstant(result: Any?): XC_MethodReplacement = object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? = result
        }

        @JvmStatic
        fun returnConstant(priority: Int, result: Any?): XC_MethodReplacement = object : XC_MethodReplacement(priority) {
            override fun replaceHookedMethod(param: MethodHookParam): Any? = result
        }
    }
}
