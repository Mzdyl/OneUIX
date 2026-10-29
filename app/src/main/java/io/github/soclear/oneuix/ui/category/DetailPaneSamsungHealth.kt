package io.github.soclear.oneuix.ui.category

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SelectItem
import io.github.soclear.oneuix.ui.component.SwitchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun DetailPaneSamsungHealth(
    uiState: Preference.SamsungHealth,
    otherUiState: Preference.Other,
    onEvent: (SamsungHealthEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.health_metrics),
            title = stringResource(id = R.string.hideSamsungHealthDiscover_title),
            checked = otherUiState.hideSamsungHealthDiscover,
            onCheckedChange = { onEvent(SamsungHealthEvent.HideDiscover(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.lock_open),
            title = stringResource(id = R.string.samsungHealthBypassAccountCountry_title),
            summary = stringResource(id = R.string.samsungHealthBypassAccountCountry_summary),
            checked = uiState.bypassAccountCountryCheck,
            onCheckedChange = { onEvent(SamsungHealthEvent.BypassAccountCountryCheck(it)) }
        )
        SelectItem(
            icon = ImageVector.vectorResource(id = R.drawable.globe),
            title = stringResource(id = R.string.samsungHealthServerRegion_title),
            summary = stringResource(id = R.string.samsungHealthServerRegion_summary),
            entries = listOf(
                stringResource(id = R.string.samsungHealthServerRegion_default),
                stringResource(id = R.string.samsungHealthServerRegion_china),
                stringResource(id = R.string.samsungHealthServerRegion_global)
            ),
            selectedIndex = uiState.serverRegion,
            onSelectedIndexChange = { onEvent(SamsungHealthEvent.ServerRegion(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.health_metrics),
            title = stringResource(id = R.string.samsungHealthUnlockCountryFeatures_title),
            summary = stringResource(id = R.string.samsungHealthUnlockCountryFeatures_summary),
            checked = uiState.unlockCountryFeatures,
            onCheckedChange = { onEvent(SamsungHealthEvent.UnlockCountryFeatures(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.bluetooth_connected),
            title = stringResource(id = R.string.samsungHealthUnlockAccessories_title),
            summary = stringResource(id = R.string.samsungHealthUnlockAccessories_summary),
            checked = uiState.unlockAccessoryProfiles,
            onCheckedChange = { onEvent(SamsungHealthEvent.UnlockAccessoryProfiles(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.watch_pairing),
            title = stringResource(id = R.string.samsungHealthFixWearableSync_title),
            summary = stringResource(id = R.string.samsungHealthFixWearableSync_summary),
            checked = uiState.fixWearableSync,
            onCheckedChange = { onEvent(SamsungHealthEvent.FixWearableSync(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.logo_dev),
            title = stringResource(id = R.string.samsungHealthUnlockFeatureList_title),
            summary = stringResource(id = R.string.samsungHealthUnlockFeatureList_summary),
            checked = uiState.unlockFeatureList,
            onCheckedChange = { onEvent(SamsungHealthEvent.UnlockFeatureList(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.lock_open),
            title = stringResource(id = R.string.samsungHealthDataBridge_title),
            summary = stringResource(id = R.string.samsungHealthDataBridge_summary),
            checked = uiState.dataBridge,
            onCheckedChange = { onEvent(SamsungHealthEvent.DataBridge(it)) }
        )
        if (uiState.dataBridge) {
            DataBridgeDetails()
        }
    }
}

/**
 * 打开开关后显示端点和令牌。令牌存在三星健康自己的私有目录里，这里用 su 读一次
 * —— OneUIX 是普通 app，没有别的途径跨进程拿到它。
 */
@Composable
private fun DataBridgeDetails() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var failMsg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        Text(
            text = stringResource(id = R.string.samsungHealthDataBridgeEndpoint_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(id = R.string.samsungHealthDataBridgeEndpoint_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(id = R.string.samsungHealthDataBridgeToken_title),
            style = MaterialTheme.typography.titleSmall
        )

        when {
            loading -> Text("…", style = MaterialTheme.typography.bodySmall)

            failMsg.isNotEmpty() -> {
                Text(
                    text = stringResource(id = R.string.samsungHealthDataBridgeToken_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = failMsg,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("cmd", "su -c 'cat $BRIDGE_TOKEN_PATH'"))
                    copied = true
                }) {
                    Text(
                        if (copied) stringResource(id = R.string.samsungHealthDataBridgeCopied)
                        else stringResource(id = R.string.samsungHealthDataBridgeCopyCmd)
                    )
                }
            }

            token == null -> TextButton(onClick = {
                loading = true
                failMsg = ""
                scope.launch {
                    val (t, msg) = withContext(Dispatchers.IO) { readBridgeToken() }
                    token = t
                    failMsg = msg
                    loading = false
                }
            }) {
                Text(stringResource(id = R.string.samsungHealthDataBridgeToken_summary))
            }

            else -> {
                Text(
                    text = token.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("token", token.orEmpty()))
                    copied = true
                }) {
                    Text(
                        if (copied) stringResource(id = R.string.samsungHealthDataBridgeCopied)
                        else stringResource(id = R.string.samsungHealthDataBridgeCopy)
                    )
                }
            }
        }
    }
}

/**
 * 与 DataBridgeServer.TOKEN_FILE 保持一致；两个模块不互相依赖，只能各自记一份路径。
 * 读取需要 root：三星健康的私有目录对别的 app 是 700，SELinux 也挡着。
 * 返回 (token, 失败原因)：失败原因原样带出去，否则只会看到一句无用的"读取失败"。
 */
private const val BRIDGE_TOKEN_PATH = "/data/data/com.sec.android.app.shealth/files/oneuix_bridge_token"

private val TOKEN_PATTERN = Regex("^[0-9a-f]{32}$")

private fun readBridgeToken(): Pair<String?, String> {
    // -mm 是必须的：KSU 的 su 默认继承 app 自己的 mount namespace，里面看不到别的 app 的
    // /data/data 目录，cat 会报 "No such file or directory"（看着像文件不存在，其实是 mount 隔离）。
    // 这和当初 healthbridge 要依次试四种 su 变体是同一个坑。
    val variants = listOf(
        arrayOf("/system/bin/su", "-mm", "-c", "cat $BRIDGE_TOKEN_PATH"),
        arrayOf("su", "-mm", "-c", "cat $BRIDGE_TOKEN_PATH"),
        arrayOf("/system/bin/su", "-c", "cat $BRIDGE_TOKEN_PATH"),
        arrayOf("su", "-c", "cat $BRIDGE_TOKEN_PATH"),
    )
    var last = ""
    for (cmd in variants) {
        try {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                last = "su 超时: ${cmd.joinToString(" ")}"
                continue
            }
            if (TOKEN_PATTERN.matches(out)) return out to ""
            last = "exit=${runCatching { p.exitValue() }.getOrDefault(-1)} out=${out.take(120)}"
        } catch (t: Throwable) {
            last = "${t.javaClass.simpleName}: ${t.message}"
        }
    }
    return null to last
}

sealed interface SamsungHealthEvent {
    @JvmInline
    value class BypassAccountCountryCheck(val value: Boolean) : SamsungHealthEvent

    @JvmInline
    value class ServerRegion(val value: Int) : SamsungHealthEvent

    @JvmInline
    value class UnlockCountryFeatures(val value: Boolean) : SamsungHealthEvent

    @JvmInline
    value class UnlockAccessoryProfiles(val value: Boolean) : SamsungHealthEvent

    @JvmInline
    value class FixWearableSync(val value: Boolean) : SamsungHealthEvent

    @JvmInline
    value class UnlockFeatureList(val value: Boolean) : SamsungHealthEvent

    @JvmInline
    value class DataBridge(val value: Boolean) : SamsungHealthEvent

    @JvmInline
    value class HideDiscover(val value: Boolean) : SamsungHealthEvent
}

fun SettingViewModel.onSamsungHealthEvent(event: SamsungHealthEvent) {
    updateData { preference ->
        when (event) {
            is SamsungHealthEvent.BypassAccountCountryCheck -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    bypassAccountCountryCheck = event.value
                )
            )

            is SamsungHealthEvent.ServerRegion -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    serverRegion = event.value.coerceIn(0, 2)
                )
            )

            is SamsungHealthEvent.UnlockCountryFeatures -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    unlockCountryFeatures = event.value
                )
            )

            is SamsungHealthEvent.UnlockAccessoryProfiles -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    unlockAccessoryProfiles = event.value
                )
            )

            is SamsungHealthEvent.FixWearableSync -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    fixWearableSync = event.value
                )
            )

            is SamsungHealthEvent.UnlockFeatureList -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    unlockFeatureList = event.value
                )
            )

            is SamsungHealthEvent.DataBridge -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    dataBridge = event.value
                )
            )

            is SamsungHealthEvent.HideDiscover -> preference.copy(
                other = preference.other.copy(
                    hideSamsungHealthDiscover = event.value
                )
            )
        }
    }
}
