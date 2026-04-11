package io.github.soclear.oneuix.data

import kotlinx.serialization.Serializable

@Serializable
data class Preference(
    val android: Android = Android(),
    val systemUI: SystemUI = SystemUI(),
    val settings: Settings = Settings(),
    val call: Call = Call(),
    val camera: Camera = Camera(),
    val gallery: Gallery = Gallery(),
    val notes: Notes = Notes(),
    val calendar: Calendar = Calendar(),
    val messaging: Messaging = Messaging(),
    val browser: Browser = Browser(),
    val video: Video = Video(),
    val weather: Weather = Weather(),
    val themeCenter: ThemeCenter = ThemeCenter(),
    val launcher: Launcher = Launcher(),
    val dualApp: DualApp = DualApp(),
    val photoRetouching: PhotoRetouching = PhotoRetouching(),
    val watchPairing: WatchPairing = WatchPairing(),
    val healthMonitor: HealthMonitor = HealthMonitor(),
    val galaxyStore: GalaxyStore = GalaxyStore(),
    val sPen: SPen = SPen(),
    val other: Other = Other(),
) {
    @Serializable
    data class Android(
        val disablePinVerifyPer72h: Boolean = false,
        val modifyMaxNeverKilledAppNum: Boolean = false,
        val maxNeverKilledAppNum: Int = 5,
        val setBlockableNotificationChannel: Boolean = false,
        val supportAppJumpBlock: Boolean = false,
        val disableAsksRestriction: Boolean = false,
        val allowGms: Boolean = false,
        val fcmFix: Boolean = false,
        val hideNavigationBarGestureHint: Boolean = false,
        val enableGoogleSearch: Boolean = false,
    )

    @Serializable
    data class SystemUI(
        val statusBar: StatusBar = StatusBar(),
        val qs: QS = QS(),
        val aod: AOD = AOD(),
        val other: Other = Other(),
    ) {
        @Serializable
        data class StatusBar(
            val modifyStatusBarLeftPadding: Boolean = false,
            val statusBarLeftPaddingDp: Float = 0f,
            val modifyStatusBarRightPadding: Boolean = false,
            val statusBarRightPaddingDp: Float = 0f,
            val hideBatteryPercentageSign: Boolean = false,
            val supportRealTimeNetworkSpeed: Boolean = true,
            val showSeparateUpDownNetworkSpeeds: Boolean = false,
            val setStatusBarClockFormat: Boolean = false,
            val statusBarClockFormat: String = "HH:mm",
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
        )

        @Serializable
        data class QS(
            val setQsClockMonospaced: Boolean = false,
            val hideDeviceControlQsTile: Boolean = false,
            val turnOn5gQsTile: Boolean = false,
            val hideQsBarMediaPlayer: Boolean = false,
            val hideQsBarNearbyDevicesAndDeviceControl: Boolean = false,
            val hideQsBarSecurityFooter: Boolean = false,
            val hideQsBarSmartViewAndModes: Boolean = false,
            val alwaysExpandQsTileChunk: Boolean = false,
            val alwaysShowTimeDateOnQs: Boolean = false,
            val addBrightnessProgressToQsBar: Boolean = false,
            val addVolumeProgressToQsBar: Boolean = false,
            val showTraditionalChineseDateOnQS: Boolean = false,
            val modifyQSClockTextSize: Boolean = false,
            val qsClockTextSize: Float = 32f,
        )

        @Serializable
        data class AOD(
            val hideAODStatusBar: Boolean = false,
            val aodLockSupportLunar: Boolean = false,
        )

        @Serializable
        data class Other(
            val disableScreenshotCaptureSound: Boolean = false,
        )
    }

    @Serializable
    data class Settings(
        val showForcePeakRefreshRatePreference: Boolean = true,
        val showMoreBatteryInfo: Boolean = true,
        val showPackageInfo: Boolean = true,
        val showWiFiLinkSpeed: Boolean = false,
        val supportAnyFont: Boolean = true,
        val supportAutoPowerOnOff: Boolean = false,
        val showNotificationCategory: Boolean = false,
    )

    @Serializable
    data class Call(
        val supportVoiceCallRecording: Boolean = true,
        val preferRecordingButton: Boolean = true,
        val showGeocodedLocationInRecentCall: Boolean = false,
        val isOpStyleCHN: Boolean = false,
        val supportCallAndTextOnOtherDevices: Boolean = false,
    )

    @Serializable
    data class Camera(
        val supportAllCameraMenu: Boolean = true,
        val disableCameraTemperatureCheck: Boolean = false,
        val supportFrameWatermark: Boolean = false,
        val supportBodyBeauty: Boolean = false,
    )

    @Serializable
    data class Gallery(
        val supportAllGallerySettings: Boolean = true,
    )

    @Serializable
    data class Notes(
        val supportAllNotesFeatures: Boolean = true,
    )

    @Serializable
    data class Calendar(
        val enableChineseHolidayDisplay: Boolean = false,
    )

    @Serializable
    data class Messaging(
        val supportBlockMessage: Boolean = true,
    )

    @Serializable
    data class Browser(
        val showMorePlaybackSpeeds: Boolean = false,
        val spoofBrowserCountryCodeToUS: Boolean = false,
    )

    @Serializable
    data class Video(
        val showMorePlaybackSpeeds: Boolean = false,
    )

    @Serializable
    data class Weather(
        val setWeatherProviderCN: Boolean = false,
    )

    @Serializable
    data class ThemeCenter(
        val setThemeTrialNeverExpired: Boolean = true,
    )

    @Serializable
    data class Launcher(
        val showMemoryUsageInRecents: Boolean = false,
    )

    @Serializable
    data class DualApp(
        val makeAllUserAppsAvailable: Boolean = true,
    )

    @Serializable
    data class PhotoRetouching(
        val noAIWatermark: Boolean = true,
        val enableSketch: Boolean = false,
    )

    @Serializable
    data class WatchPairing(
        val bypassRegionCheck: Boolean = false,           // 绕过区域限制
        val connectionMode: Int = 0,                       // 0=自动，1=WearOS CN，2=WearOS Global
        val forceChinaGmsCore: Boolean = false,            // 强制安装国行 GMS
        val disableCscCheck: Boolean = false,              // 禁用 CSC 检查
    )

    @Serializable
    data class HealthMonitor(
        val bypassHealthMonitorCountryCheck: Boolean = false,
    )

    @Serializable
    data class GalaxyStore(
        val blockGalaxyStoreAds: Boolean = true,
        val changeRegion: Boolean = false,
        val regionCode: String = "US",
    )

    @Serializable
    data class SPen(
        val useGoogleTranslate: Boolean = false,
    )

    @Serializable
    data class Other(
        // 保留给模块自身设置或未分类功能
        val placeholder: Boolean = false
    )


}
