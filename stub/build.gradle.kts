plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.soclear.oneuix.stub"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
