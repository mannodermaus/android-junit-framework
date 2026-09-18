plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.mannodermaus.junit5.testutil"

    defaultConfig {
        minSdk = 19
        multiDexEnabled = true
    }
}

dependencies {
    implementation(project(":testutil-reflect"))
    implementation(libs.androidx.multidex)

    api(libs.androidx.test.monitor)
    api(libs.truth.core)
    api(libs.truth.extensions)
    api(libs.mockito.core)
    api(libs.mockito.kotlin)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    api(libs.junit.platform.launcher)
}
