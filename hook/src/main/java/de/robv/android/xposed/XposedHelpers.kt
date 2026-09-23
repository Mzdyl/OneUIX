package de.robv.android.xposed

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

object XposedHelpers {
    private val additionalFields = WeakHashMap<Any, MutableMap<String, Any?>>()

    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        return Class.forName(className, false, classLoader ?: ClassLoader.getSystemClassLoader())
    }

    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? {
        return try {
            findClass(className, classLoader)
        } catch (_: Throwable) {
            null
        }
    }

    fun findField(clazz: Class<*>, fieldName: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("${clazz.name}#$fieldName")
    }

    fun findFieldIfExists(clazz: Class<*>, fieldName: String): Field? {
        return try {
            findField(clazz, fieldName)
        } catch (_: Throwable) {
            null
        }
    }

    fun getObjectField(obj: Any, fieldName: String): Any? {
        return findField(obj.javaClass, fieldName).get(obj)
    }

    fun getBooleanField(obj: Any, fieldName: String): Boolean {
        return findField(obj.javaClass, fieldName).getBoolean(obj)
    }

    fun getIntField(obj: Any, fieldName: String): Int {
        return findField(obj.javaClass, fieldName).getInt(obj)
    }

    fun getLongField(obj: Any, fieldName: String): Long {
        return findField(obj.javaClass, fieldName).getLong(obj)
    }

    fun setObjectField(obj: Any, fieldName: String, value: Any?) {
        findField(obj.javaClass, fieldName).set(obj, value)
    }

    fun setBooleanField(obj: Any, fieldName: String, value: Boolean) {
        findField(obj.javaClass, fieldName).setBoolean(obj, value)
    }

    fun setIntField(obj: Any, fieldName: String, value: Int) {
        findField(obj.javaClass, fieldName).setInt(obj, value)
    }

    fun setLongField(obj: Any, fieldName: String, value: Long) {
        findField(obj.javaClass, fieldName).setLong(obj, value)
    }

    fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any? {
        return findField(clazz, fieldName).get(null)
    }

    fun setStaticObjectField(clazz: Class<*>, fieldName: String, value: Any?) {
        findField(clazz, fieldName).set(null, value)
    }

    fun findMethodExact(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val method = current.getDeclaredMethod(methodName, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        throw NoSuchMethodException("${clazz.name}#$methodName(${parameterTypes.joinToString { it.name }})")
    }

    fun findMethodExactIfExists(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? {
        return try {
            findMethodExact(clazz, methodName, *parameterTypes)
        } catch (_: Throwable) {
            null
        }
    }

    fun findConstructorExact(clazz: Class<*>, vararg parameterTypes: Class<*>): Constructor<*> {
        val constructor = clazz.getDeclaredConstructor(*parameterTypes)
        constructor.isAccessible = true
        return constructor
    }

    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypesAndCallback: Any?
    ): Any? {
        require(parameterTypesAndCallback.isNotEmpty()) { "parameterTypesAndCallback cannot be empty" }
        val callback = parameterTypesAndCallback.last() as? XC_MethodHook
            ?: throw IllegalArgumentException("Last argument must be an XC_MethodHook")
        val paramTypes = parameterTypesAndCallback.dropLast(1).map { param ->
            when (param) {
                is Class<*> -> param
                is String -> findClass(param, clazz.classLoader)
                else -> throw IllegalArgumentException("Parameter type must be Class or String, got $param")
            }
        }.toTypedArray()

        val method = findMethodExact(clazz, methodName, *paramTypes)
        return XposedBridge.hookMethod(method, callback)
    }

    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        vararg parameterTypesAndCallback: Any?
    ): Any? {
        val clazz = findClass(className, classLoader)
        return findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
    }

    fun findAndHookConstructor(
        clazz: Class<*>,
        vararg parameterTypesAndCallback: Any?
    ): Any? {
        require(parameterTypesAndCallback.isNotEmpty()) { "parameterTypesAndCallback cannot be empty" }
        val callback = parameterTypesAndCallback.last() as? XC_MethodHook
            ?: throw IllegalArgumentException("Last argument must be an XC_MethodHook")
        val paramTypes = parameterTypesAndCallback.dropLast(1).map { param ->
            when (param) {
                is Class<*> -> param
                is String -> findClass(param, clazz.classLoader)
                else -> throw IllegalArgumentException("Parameter type must be Class or String, got $param")
            }
        }.toTypedArray()

        val constructor = findConstructorExact(clazz, *paramTypes)
        return XposedBridge.hookMethod(constructor, callback)
    }

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        val clazz = obj.javaClass
        val method = findBestMethod(clazz, methodName, *args)
        method.isAccessible = true
        return method.invoke(obj, *args)
    }

    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        val method = findBestMethod(clazz, methodName, *args)
        method.isAccessible = true
        return method.invoke(null, *args)
    }

    fun newInstance(clazz: Class<*>, vararg args: Any?): Any? {
        val constructor = findBestConstructor(clazz, *args)
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    private fun findBestMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            for (m in current.declaredMethods) {
                if (m.name == methodName && isMatch(m.parameterTypes, args)) {
                    return m
                }
            }
            current = current.superclass
        }
        throw NoSuchMethodException("${clazz.name}#$methodName with args ${args.map { it?.javaClass?.name }}")
    }

    private fun findBestConstructor(clazz: Class<*>, vararg args: Any?): Constructor<*> {
        for (c in clazz.declaredConstructors) {
            if (isMatch(c.parameterTypes, args)) {
                return c
            }
        }
        throw NoSuchMethodException("${clazz.name} constructor with args ${args.map { it?.javaClass?.name }}")
    }

    private fun isMatch(paramTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
        if (paramTypes.size != args.size) return false
        for (i in paramTypes.indices) {
            val arg = args[i] ?: continue
            val paramType = paramTypes[i]
            val boxed = when (paramType) {
                java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
                java.lang.Byte.TYPE -> java.lang.Byte::class.java
                java.lang.Character.TYPE -> java.lang.Character::class.java
                java.lang.Short.TYPE -> java.lang.Short::class.java
                java.lang.Integer.TYPE -> java.lang.Integer::class.java
                java.lang.Long.TYPE -> java.lang.Long::class.java
                java.lang.Float.TYPE -> java.lang.Float::class.java
                java.lang.Double.TYPE -> java.lang.Double::class.java
                else -> paramType
            }
            if (!boxed.isInstance(arg)) return false
        }
        return true
    }

    @Synchronized
    fun getAdditionalInstanceField(obj: Any, key: String): Any? {
        return additionalFields[obj]?.get(key)
    }

    @Synchronized
    fun setAdditionalInstanceField(obj: Any, key: String, value: Any?) {
        val map = additionalFields.getOrPut(obj) { mutableMapOf() }
        map[key] = value
    }
}
