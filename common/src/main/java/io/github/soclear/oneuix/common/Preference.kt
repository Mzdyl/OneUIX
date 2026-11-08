package io.github.soclear.oneuix.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Preference(
    val android: Android = Android(),
    val systemUI: SystemUI = SystemUI(),
    val settings: Settings = Settings(),
    val call: Call = Call(),
    val camera: Camera = Camera(),
    val other: Other = Other(),
    val samsungHealth: SamsungHealth = SamsungHealth(),
    val bixby: Bixby = Bixby(),
    val nfc: Nfc = Nfc(),
) {
    @Serializable
    data class Android(
        val disablePinVerifyPer72h: Boolean = false,
        val modifyMaxNeverKilledAppNum: Boolean = false,
        val maxNeverKilledAppNum: Int = 5,
        val setBlockableNotificationChannel: Boolean = false,
        val supportAppJumpBlock: Boolean = false,
        val allowAllRotation: Boolean = false,
        val liftFcmNetworkLimit: Boolean = false,
        val disableAsksRestriction: Boolean = false,
        val fcmFix: Boolean = false,
        val hideNavigationBarGestureHint: Boolean = false,
        val disableScreenWakeOnPowerUnplugged: Boolean = false,
        val disableLowBatteryDimming: Boolean = false,
        val disableFlagSecure: Boolean = false,
    )

    @Serializable
    data class SystemUI(
        val statusBar: StatusBar = StatusBar(),
        val qs: QS = QS(),
        val aod: AOD = AOD(),
        val notification: Notification = Notification(),
        val other: Other = Other(),
    ) {
        @Serializable
        data class StatusBar(
            val networkSpeed: NetWorkSpeed = NetWorkSpeed(),
            val modifyStatusBarLeftPadding: Boolean = false,
            val statusBarLeftPaddingDp: Float = 0f,
            val modifyStatusBarRightPadding: Boolean = false,
            val statusBarRightPaddingDp: Float = 0f,
            val alignStatusBarContentToTop: Boolean = false,
            val hideBatteryPercentageSign: Boolean = false,
            val hideBatteryIcon: Boolean = false,
            val addBatteryLevelText: Boolean = false,
            val hideBatteryLevelTextPercentageSign: Boolean = false,
            val hideBatteryLevelTextChargingIcon: Boolean = false,
            val setStatusBarClockFormat: Boolean = false,
            val statusBarClockFormat: String = "HH:mm",
            val setStatusBarClockTextScale: Boolean = false,
            val statusBarClockTextScale: Float = 1f,
            val hideSecureFolderStatusBarIcon: Boolean = false,
            val doubleTapStatusBarToSleep: Boolean = false,
            val modifyStatusBarMaxNotificationIcons: Boolean = false,
            val statusBarMaxNotificationIcons: Int = 4,
            val setBatteryIconWidthScale: Boolean = false,
            val batteryIconWidthScale: Float = 1f,
            val setBatteryIconHeightScale: Boolean = false,
            val batteryIconHeightScale: Float = 1f,
            val setCustomCarrierName: Boolean = false,
            val customCarrierName: String = "",
            val hideLockscreenStatusBar: Boolean = false,
            val updateStatusBarClockEverySecond: Boolean = false,
            val physicalEsimAdapterWorkaround: Boolean = false,
            val physicalEsimAdapterSimSlot: Int = 1,
            val restoreBluetoothStatusBarIcon: Boolean = false,
        ) {
            @Serializable
            data class NetWorkSpeed(
                val supportRealTime: Boolean = true,
                val layout: Layout = Layout.SYSTEM,
                val showArrows: Boolean = true,
                val marker: Marker = Marker.ARROW,
                val unit: Unit = Unit.BYTES_BINARY,
                val showPerSecond: Boolean = true,
                val textSizeSp: Float = 0f,
                val markerSizeSp: Float = 0f,
                val markerGap: Float = 1f,
                val lineSpacingDp: Float = 0f,
                val threshold: Int = 0,
                val refreshInterval: RefreshInterval = RefreshInterval.ONE_SECOND,
                val paddingEndDp: Float = 0f,
            ) {
                enum class Layout { SYSTEM, SPLIT_HORIZONTAL, SPLIT_VERTICAL, ACTIVE_DIRECTION }

                enum class Marker(val up: String, val down: String) {
                    ARROW("↑", "↓"),
                    TRIANGLE("▲", "▼"),
                    OUTLINE_TRIANGLE("△", "▽"),
                    OUTLINE_ARROW("⇧", "⇩"),
                    DASHED_ARROW("⇡", "⇣"),
                    CHEVRON("⌃", "⌄"),
                    BOTH_DIRECTIONS("↕", "↕"),
                    ASCII("^", "v"),
                    BLOCK_ARROW("⬆", "⬇"),
                }

                enum class Unit(val bits: Boolean, val base: Int, val kiloPrefix: String) {
                    BYTES_BINARY(false, 1024, "K"),
                    BYTES_DECIMAL(false, 1000, "K"),
                    BITS_DECIMAL(true, 1000, "Kb"),
                    BITS_BINARY(true, 1024, "Kib"),
                }

                enum class RefreshInterval(val milliseconds: Long) {
                    QUARTER_SECOND(250),
                    HALF_SECOND(500),
                    ONE_SECOND(1000),
                    TWO_SECONDS(2000),
                    THREE_SECONDS(3000),
                    FOUR_SECONDS(4000),
                    FIVE_SECONDS(5000),
                }
            }
        }

        @Serializable
        data class QS(
            val enableRealtimeBlur: Boolean = false,
            val quickQsTileCount: Int = 0,
            val setQsClockMonospaced: Boolean = false,
            val hideQuickQsTiles: Boolean = false,
            val hideDeviceControlQsTile: Boolean = false,
            val hideSmartViewQsTile: Boolean = false,
            val hideAudioEraserBanner: Boolean = false,
            val turnOn5gQsTile: Boolean = false,
            val hideQsBarMediaPlayer: Boolean = false,
            val hideQsBarNearbyDevicesAndDeviceControl: Boolean = false,
            val hideQsBarSecurityFooter: Boolean = false,
            val hideQsBarDataUsage: Boolean = false,
            val hideQsBarSmartViewAndModes: Boolean = false,
            val alwaysExpandQsTileChunk: Boolean = false,
            val alwaysShowTimeDateOnQs: Boolean = false,
            val disableHighBrightnessDialog: Boolean = false,
            val addBrightnessProgressToQsBar: Boolean = false,
            val addVolumeProgressToQsBar: Boolean = false,
            val showTraditionalChineseDateOnQS: Boolean = false,
            val modifyQSClockTextSize: Boolean = false,
            // 去掉 SystemUI 给 bar tile 自定义图套的 Modifier.alpha(0.5f)
            val removeQsBarTileAlpha: Boolean = true,
            // 无 UI 刷新 QS 主题：注册 QS_REFRESH 导出广播接收器。默认关闭 ——
            // 开启后 SystemUI 会替广播发送方 grantUriPermissions 并改写 Settings.Secure，
            // 属于特权原语，必须由用户显式开启。
            val enableQsRefreshReceiver: Boolean = false,
            val qsClockTextSize: Float = 32f,
        )

        @Serializable
        data class AOD(
            val hideAODStatusBar: Boolean = false,
            val hideAODChargingInfo: Boolean = false,
            val aodLockSupportLunar: Boolean = false,
        )

        @Serializable
        data class Notification(
            val hideOngoingActivityMedia: Boolean = false,
            val hideOngoingActivityMediaPackages: String = "",
            val disableNotificationGrouping: Boolean = false,
            val autoExpandNotifications: Boolean = false
        )

        @Serializable
        data class Other(
            val hideLockscreenEmergencyButton: Boolean = false,
            val disableScreenshotCaptureSound: Boolean = false,
            val allowLowBatteryFlashlight: Boolean = false,
            val customPowerMenu: Boolean = false,
            val powerMenuActions: List<PowerMenuAction> = PowerMenuAction.defaultPreferences()
        )
    }

    @Serializable
    data class Settings(
        val showForcePeakRefreshRatePreference: Boolean = true,
        val supportOutdoorMode: Boolean = false,
        val showMoreBatteryInfo: Boolean = true,
        val showPackageInfo: Boolean = true,
        val showWiFiLinkSpeed: Boolean = false,
        val supportAnyFont: Boolean = true,
        val supportAutoPowerOnOff: Boolean = false,
        val showNotificationCategory: Boolean = false,
        val spoofPhoneStatusAsOfficial: Boolean = false,
    )

    @Serializable
    data class Call(
        val supportVoiceCallRecording: Boolean = true,
        val preferRecordingButton: Boolean = true,
        val showGeocodedLocationInRecentCall: Boolean = false,
        val isOpStyleCHN: Boolean = false,
        val bypassSameWifiRestriction: Boolean = false,
        val unlockCmcMobileNetwork: Boolean = false,
        val bypassChinaSimRestriction: Boolean = false,
        val useChinaCmcServer: Boolean = false,
        val fixCmcPushToken: Boolean = false,
        val enableVirtualLanP2p: Boolean = false,
        val virtualLanPeerIp: String = "",
        val mdecDeviceType: Int = 0,
        val supportCallAndTextOnOtherDevices: Boolean = false,
        // 实验项：让副设备外拨真的走 CMC（用主设备的卡）。
        // 该固件的 CMC 音频管线目前不通，开启后拨出去会无声，所以默认关闭。
        val enableCmcOutgoingCall: Boolean = false,
    )

    @Serializable
    data class Camera(
        val supportAllCameraMenu: Boolean = true,
        val disableCameraTemperatureCheck: Boolean = false,
        val supportFrameWatermark: Boolean = false,
        val supportBodyBeauty: Boolean = false,
    )

    @Serializable
    data class Other(
        val nfcFilterMode: Int = 0, // 0: 关闭，1: 黑名单，2: 白名单
        val nfcCards: Map<String, String> = emptyMap(),
        val disableWritingToolkit: Boolean = false,
        val disableWritingToolkitPackages: Set<String> = emptySet(),
        val hideScreenshotDetection: Boolean = false,
        val hideScreenshotDetectionPackages: Set<String> = emptySet(),
        val unlockBixbyOfflineProcessing: Boolean = false,
        val supportBixbyCustomWakeup: Boolean = false,
        val bypassBixbyWakeWordRestrictions: Boolean = false,
        val blockGalaxyStoreAds: Boolean = true,
        val unlockSmartSuggestions: Boolean = true,
        val smartSuggestionsChatTranslation: Boolean = true,
        val smartSuggestionsGlobalCards: Boolean = true,
        val smartSuggestionsHealthCards: Boolean = true,
        val smartSuggestionsNowNudge: Boolean = true,
        val changeRegion: Boolean = false,
        val regionCode: String = "US",
        val hideGalaxyStoreDiscover: Boolean = false,
        val hideGalaxyStoreBenefits: Boolean = false,
        val setGalaxyStoreSimOperator: Boolean = false,
        val galaxyStoreSimOperator: String = "",
        val makeAllUserAppsAvailable: Boolean = true,
        val setWeatherProviderCN: Boolean = false,
        val showMemoryUsageInRecents: Boolean = false,
        val recentsGridThreeRows: Boolean = false,
        val hideRecentsCloseAllButton: Boolean = false,
        val showMorePlaybackSpeeds: Boolean = false,
        val redirectCustomTab: Boolean = false,
        val supportAllGallerySettings: Boolean = true,
        val supportSharedAlbumsInHide: Boolean = false,
        val hideVideoEditorStudio: Boolean = false,
        val supportGalleryGoogleSync: Boolean = true,
        val supportGalleryCloudSyncToolbar: Boolean = true,
        val enableGalleryPoi: Boolean = true,
        val enableGalleryFullAddress: Boolean = true,
        val unlockGalleryDeveloperLabs: Boolean = true,
        val enableGalleryUndoDelete: Boolean = true,
        val enableGalleryDualPhotoPreview: Boolean = true,
        val enableGalleryQuickSeek: Boolean = true,
        val enableGalleryHdrThumbnail: Boolean = true,
        val enableGalleryPhotoStripHighQuality: Boolean = false,
        val enableGalleryUnmuteAlways: Boolean = false,
        val supportAllNotesFeatures: Boolean = true,
        val enableChineseHolidayDisplay: Boolean = false,
        val supportBlockMessage: Boolean = true,
        val unlockRcs: Boolean = false,
        val setThemeTrialNeverExpired: Boolean = true,
        val spoofBrowserCountryCodeToUS: Boolean = false,
        val noAIWatermark: Boolean = true,
        val enableSketch: Boolean = false,
        val hideSamsungHealthDiscover: Boolean = false,
        val bypassHealthMonitorCountryCheck: Boolean = false,
        val sPenTranslationSource: SPenTranslationSource = SPenTranslationSource.DEFAULT,
        val mediaPagePackage: String = "",
        val embedAppInMediaPage: Boolean = false,
        val hideAppsSearchBar: Boolean = false,
        val removeShortcutBadge: Boolean = false,
        val modifyDockBottomPadding: Boolean = false,
        val dockBottomPaddingDp: Float = 0f,
        val bypassWatchPairingRegionCheck: Boolean = false,
        val watchPairingConnectionMode: Int = 0,
        val supplementChinaWearOsGms: Boolean = false,
        val bypassSamsungPayWearableCnCheck: Boolean = true,
        val bypassReminderWearableCnCheck: Boolean = true,
        val preventWatchPluginUnpair: Boolean = true,
        val unlockWatchPluginGlobalFeatures: Boolean = true,
        val forceShowAllWatchfaceComplications: Boolean = true,
        val enableWatchPluginHiddenMenuShortcut: Boolean = true,
        val enableGoogleQuickShare: Boolean = true,
        val unlockQuickShareLabs: Boolean = true,
    )

    @Serializable
    data class SamsungHealth(
        val bypassAccountCountryCheck: Boolean = false,
        val serverRegion: Int = 0,
        val unlockCountryFeatures: Boolean = false,
        val unlockAccessoryProfiles: Boolean = false,
        val fixWearableSync: Boolean = true,
        val unlockFeatureList: Boolean = true,
        /** 把活体数据库暴露成带 token 的 HTTP 查询口（:8099），默认关闭。 */
        val dataBridge: Boolean = false,
    )

    @Serializable
    data class Bixby(
        val injectModel: Boolean = false,
        val labsMgr: Boolean = false,
        val wwvBypass: Boolean = false,
    )

    @Serializable
    data class Nfc(
        val enableSimulation: Boolean = false,
        val bypassPrompt: Boolean = false,
        val activeUid: String = "",
        val activeCardName: String = "",
        val activeSak: String = "04",
        val activeAtqa: String = "00",
        val cards: List<NfcCard> = emptyList(),
    )

    @Serializable
    data class NfcCard(
        val id: String = "",
        val name: String = "",
        val uid: String = "",
        val sak: String = "04",
        val atqa: String = "00",
    )

    companion object {
        const val FILE_NAME = "preference.json"
        const val DATASTORE_SENTINEL_NAME = "whatever"
    }
}

val IgnoreUnknownKeysJson = Json {
    ignoreUnknownKeys = true
}
