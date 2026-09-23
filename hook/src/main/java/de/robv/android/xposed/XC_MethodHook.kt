package de.robv.android.xposed

import java.lang.reflect.Member

open class XC_MethodHook(val priority: Int = PRIORITY_DEFAULT) {
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}

    class MethodHookParam {
        @JvmField var method: Member? = null
        @JvmField var thisObject: Any? = null
        @JvmField var args: Array<Any?> = emptyArray()

        private var _result: Any? = null
        private var _throwable: Throwable? = null
        @JvmField var returnEarly: Boolean = false

        val hasThrowable: Boolean get() = _throwable != null

        var result: Any?
            get() = _result
            set(value) {
                _result = value
                _throwable = null
                returnEarly = true
            }

        var throwable: Throwable?
            get() = _throwable
            set(value) {
                _throwable = value
                _result = null
                returnEarly = true
            }

        fun setResult(result: Any?) {
            this.result = result
        }

        fun setThrowable(throwable: Throwable?) {
            this.throwable = throwable
        }

        fun getResultOrThrowable(): Any? {
            if (_throwable != null) throw _throwable!!
            return _result
        }
    }

    companion object {
        const val PRIORITY_DEFAULT = 50
        const val PRIORITY_LOWEST = -10000
        const val PRIORITY_HIGHEST = 10000
    }
}
