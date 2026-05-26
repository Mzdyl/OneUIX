package io.github.soclear.oneuix.hook.systemui.powermenu

import android.app.AndroidAppHelper
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.samsung.android.globalactions.presentation.SamsungGlobalActions
import com.samsung.android.globalactions.presentation.viewmodel.ActionInfo
import com.samsung.android.globalactions.presentation.viewmodel.ActionViewModel
import com.samsung.android.globalactions.presentation.viewmodel.ViewType
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.data.PowerMenuAction

class RestartSystemUIActionViewModel(
    private val globalActions: SamsungGlobalActions,
) : ActionViewModel {
    private val actionInfo = ActionInfo().apply {
        val context = AndroidAppHelper.currentApplication()
        name = PowerMenuAction.RESTART_SYSTEMUI
        viewType = ViewType.CENTER_ICON_3P_VIEW
        icon = R.drawable.power_settings_new
        label = context.getString(R.string.restartSystemUI)
    }

    override fun getActionInfo(): ActionInfo = actionInfo

    override fun onPress() {
        if (!globalActions.isActionConfirming()) {
            globalActions.confirmAction(this)
            return
        }

        globalActions.dismissDialog(false)
        Handler(Looper.getMainLooper()).postDelayed({
            Process.killProcess(Process.myPid())
        }, 100L)
    }

    override fun setActionInfo(actionInfo: ActionInfo) {}
}
