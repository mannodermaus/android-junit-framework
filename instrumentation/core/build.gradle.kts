plugins {
    alias(libs.plugins.android.junit)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.mannodermaus.junit5"

    defaultConfig {
        minSdk = Android.testCoreMinSdkVersion
        multiDexEnabled = true
    }
}

junitPlatform {
    filters {
        // See TaggedTests.kt for usage of this tag
        excludeTags("nope")
    }
}

replaceAndroidTestLibsWithLocalProjectDependencies()

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.junit.jupiter.api)
    api(libs.androidx.test.core)
    // This is required by the "instrumentation-runner" companion library,
    // since it can't provide any JUnit 5 runtime libraries itself
    // due to fear of prematurely incrementing the minSdkVersion requirement.
    runtimeOnly(libs.junit.platform.launcher)
    runtimeOnly(libs.junit.jupiter.engine)

    // This transitive dependency of JUnit 5 is required to be on the runtime classpath,
    // since otherwise ART will print noisy logs to console when trying to resolve any
    // of the annotations of JUnit 5 (see #291 for more info)
    runtimeOnly(libs.apiguardian)

    androidTestImplementation(libs.junit.jupiter.api)
    androidTestImplementation(libs.junit.jupiter.params)
    androidTestImplementation(libs.espresso)

    testImplementation(project(":testutil"))
}

project.configureDeployment(Artifacts.Instrumentation.Core)
