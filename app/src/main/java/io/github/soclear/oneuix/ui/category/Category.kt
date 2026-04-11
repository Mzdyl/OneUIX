package io.github.soclear.oneuix.ui.category

import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.data.Package

enum class Category(val packageName: String) {
    Android(Package.ANDROID),
    SystemUI(Package.SYSTEMUI),
    Settings(Package.SETTINGS),
    Call(Package.DIALER),
    Camera(Package.CAMERA),
    Gallery(Package.GALLERY),
    Notes(Package.NOTES),
    Calendar(Package.CALENDAR),
    Messaging(Package.MESSAGING),
    Browser(Package.BROWSER),
    Video(Package.VIDEO),
    Weather(Package.WEATHER),
    ThemeCenter(Package.THEME_CENTER),
    Launcher(Package.LAUNCHER),
    DualApp(Package.DUAL_APP),
    PhotoRetouching(Package.PHOTO_RETOUCHING),
    HealthMonitor(Package.HEALTH_MONITOR),
    GalaxyStore(Package.STORE),
    SPen(Package.SPEN),
    WatchPairing(Package.WATCH_MANAGER),
    Other(BuildConfig.APPLICATION_ID);
}
