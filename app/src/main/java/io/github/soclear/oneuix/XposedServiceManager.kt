package io.github.soclear.oneuix

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

object XposedServiceManager {
    private val xposedServiceFlow = MutableStateFlow<XposedService?>(null)

    val xposedService: XposedService?
        get() = xposedServiceFlow.value

    val isModuleActive: Boolean
        get() = xposedServiceFlow.value != null

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedServiceFlow.value = service
            }

            override fun onServiceDied(service: XposedService) {
                if (xposedServiceFlow.value == service) {
                    xposedServiceFlow.value = null
                }
            }
        })
    }

    // 覆盖安装后 LSPosed 可能还没把 Binder 交给本模块（APK 路径变化）。
    // 这里必须带上限，否则 DataStore 的 readFrom 会一直挂起，设置界面只能显示默认值。
    suspend fun awaitXposedService(timeoutMillis: Long = 1500L): XposedService? =
        withTimeoutOrNull(timeoutMillis) {
            xposedServiceFlow.filterNotNull().first()
        }
}
