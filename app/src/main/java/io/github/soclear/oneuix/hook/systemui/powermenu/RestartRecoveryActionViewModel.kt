package io.github.soclear.oneuix.hook.systemui.powermenu

import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.samsung.android.globalactions.presentation.SamsungGlobalActions
import com.samsung.android.globalactions.presentation.viewmodel.ActionInfo
import com.samsung.android.globalactions.presentation.viewmodel.ActionViewModel
import com.samsung.android.globalactions.presentation.viewmodel.ViewType
import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.data.PowerMenuAction
import io.github.soclear.oneuix.data.RebootRequest

class RestartRecoveryActionViewModel(
    private val globalActions: SamsungGlobalActions,
) : ActionViewModel {
    private val actionInfo = ActionInfo().apply {
        val context = AndroidAppHelper.currentApplication()
        name = PowerMenuAction.RESTART_RECOVERY
        viewType = ViewType.CENTER_ICON_3P_VIEW
        icon = R.drawable.power_settings_new
        label = context.getString(R.string.restartRecovery)
    }

    override fun getActionInfo(): ActionInfo = actionInfo

    override fun onPress() {
        if (!globalActions.isActionConfirming()) {
            globalActions.confirmAction(this)
            return
        }

        globalActions.dismissDialog(false)
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(RebootRequest.ACTION)
                .setComponent(ComponentName(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}.RebootReceiver"))
                .putExtra(RebootRequest.EXTRA_REASON, RebootRequest.RECOVERY)
            AndroidAppHelper.currentApplication().sendBroadcast(intent)
        }, 100L)
    }

    override fun setActionInfo(actionInfo: ActionInfo) {}
}
