package io.github.soclear.oneuix.hook.bixby

import java.lang.reflect.Modifier

internal fun reconcileResourceResult(result: Int, resourceState: Int?): Int =
    if (result == -1 && resourceState == 100) 1 else result

internal fun capturedPresenter(callback: Any, presenterClass: Class<*>): Any? =
    callback.javaClass.declaredFields.asSequence()
        .filter { !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive }
        .mapNotNull { field ->
            field.isAccessible = true
            field.get(callback)?.takeIf(presenterClass::isInstance)
        }
        .singleOrNull()
