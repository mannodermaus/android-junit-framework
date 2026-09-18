plugins {
    alias(libs.plugins.android.junit)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.mannodermaus.junit5.runner"

    defaultConfig { minSdk = Android.testRunnerMinSdkVersion }
}

configurations.all {
    // The Instrumentation Test Runner uses the plugin,
    // which in turn provides the Instrumentation Test Runner again -
    // that's kind of deep.
    // To avoid conflicts, prefer using the local classes
    // and exclude the dependency from being pulled in externally.
    exclude(module = Artifacts.Instrumentation.Runner.artifactId)
}

dependencies {
    implementation(libs.androidx.test.monitor)
    implementation(libs.androidx.test.runner)
    implementation(libs.kotlin.stdlib)
    implementation(libs.junit.vintage.api)

    // This module's JUnit 5 dependencies cannot be present on the runtime classpath,
    // since that would prematurely raise the minSdkVersion requirement for target applications,
    // even though not all product flavors might want to use JUnit 5.
    // Therefore, only compile against those APIs, and have them provided at runtime
    // by the "instrumentation" companion library instead.
    compileOnly(libs.junit.jupiter.api)
    compileOnly(libs.junit.jupiter.params)
    compileOnly(libs.junit.platform.launcher)

    testImplementation(project(":testutil"))
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

project.configureDeployment(Artifacts.Instrumentation.Runner)
