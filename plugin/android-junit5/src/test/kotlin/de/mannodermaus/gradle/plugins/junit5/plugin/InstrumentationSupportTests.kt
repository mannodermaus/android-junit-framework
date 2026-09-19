package de.mannodermaus.gradle.plugins.junit5.plugin

import com.google.common.truth.Truth.assertThat
import de.mannodermaus.Libraries.Instrumentation
import de.mannodermaus.Libraries.JUnit
import de.mannodermaus.Libraries.JUnit.JUnit5
import de.mannodermaus.gradle.plugins.junit5.dsl.UnsupportedDeviceBehavior
import de.mannodermaus.gradle.plugins.junit5.extensions.android
import de.mannodermaus.gradle.plugins.junit5.internal.artifact
import de.mannodermaus.gradle.plugins.junit5.internal.config.ANDROID_JUNIT_RUNNER_BUILDER_CLASS
import de.mannodermaus.gradle.plugins.junit5.internal.extensions.junitPlatform
import de.mannodermaus.gradle.plugins.junit5.util.assertThat
import de.mannodermaus.gradle.plugins.junit5.util.evaluate
import org.gradle.api.Project
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class InstrumentationSupportTests {

    @RegisterExtension @JvmField val projectExtension = TestProjectProviderExtension()

    private lateinit var project: Project

    @BeforeEach
    fun beforeEach() {
        project =
            projectExtension.newProject().asAndroidApplication().applyJUnit5Plugin(true).build()
    }

    /* RunnerBuilder */

    @Test
    fun `add the RunnerBuilder`() {
        project.addJUnit(JUnit5, "androidTest")
        project.evaluate()

        assertThat(
                project.android.defaultConfig.testInstrumentationRunnerArguments["runnerBuilder"]
            )
            .isEqualTo(ANDROID_JUNIT_RUNNER_BUILDER_CLASS)
    }

    @Test
    fun `maintain any existing RunnerBuilder`() {
        project.addJUnit(JUnit5, "androidTest")
        project.android.defaultConfig.testInstrumentationRunnerArguments["runnerBuilder"] =
            "something.else"
        project.evaluate()

        assertThat(
                project.android.defaultConfig.testInstrumentationRunnerArguments["runnerBuilder"]
            )
            .isEqualTo("something.else,$ANDROID_JUNIT_RUNNER_BUILDER_CLASS")
    }

    @Test
    fun `do not add the RunnerBuilder when disabled`() {
        project.addJUnit(JUnit5, "androidTest")
        project.junitPlatform.instrumentationTests.enabled.set(false)
        project.evaluate()

        assertThat(
                project.android.defaultConfig.testInstrumentationRunnerArguments["runnerBuilder"]
            )
            .isNull()
    }

    @Test
    fun `do not add the RunnerBuilder when Jupiter is not added`() {
        project.evaluate()

        assertThat(
                project.android.defaultConfig.testInstrumentationRunnerArguments["runnerBuilder"]
            )
            .isNull()
    }

    /* Configuration parameters */

    @Test
    fun `always includes config param for unsupported device behavior`() {
        project.addJUnit(JUnit5, "androidTest")
        project.evaluate()

        val runnerArgs =
            project.android.defaultConfig.testInstrumentationRunnerArguments[
                    "configurationParameters"]
        assertThat(runnerArgs).contains("de.mannodermaus.junit.unsupported.behavior=fail")
    }

    @Test
    fun `can configure the unsupported device behavior via dsl`() {
        project.addJUnit(JUnit5, "androidTest")
        project.junitPlatform.instrumentationTests.behaviorForUnsupportedDevices.set(
            UnsupportedDeviceBehavior.Skip
        )
        project.evaluate()

        val runnerArgs =
            project.android.defaultConfig.testInstrumentationRunnerArguments[
                    "configurationParameters"]
        assertThat(runnerArgs).contains("de.mannodermaus.junit.unsupported.behavior=skip")
    }

    @Test
    fun `copy configuration parameters to test runner arguments`() {
        project.addJUnit(JUnit5, "androidTest")
        with(project.junitPlatform) {
            configurationParameter("my.parameter1", "true")
            configurationParameter("my.parameter2", "1234")
        }
        project.evaluate()

        val runnerArgs =
            project.android.defaultConfig.testInstrumentationRunnerArguments[
                    "configurationParameters"]
        assertThat(runnerArgs).contains("my.parameter1=true")
        assertThat(runnerArgs).contains("my.parameter2=1234")
    }

    @Test
    fun `do not copy configuration parameters if disabled via dsl`() {
        project.addJUnit(JUnit5, "androidTest")
        with(project.junitPlatform) {
            configurationParameter("my.parameter1", "true")
            configurationParameter("my.parameter2", "1234")
            instrumentationTests.useConfigurationParameters.set(false)
        }
        project.evaluate()

        val runnerArgs =
            project.android.defaultConfig.testInstrumentationRunnerArguments[
                    "configurationParameters"]
        assertThat(runnerArgs).doesNotContain("my.parameter1=true")
        assertThat(runnerArgs).doesNotContain("my.parameter2=1234")
    }

    /* Dependencies */

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `add only the main dependencies (test)`(junit: JUnit) {
        project.addJUnit(junit, "test")
        project.evaluate()

        assertThat(project).configuration("testImplementation").hasDependency(coreLibrary(junit))
        assertThat(project)
            .configuration("testRuntimeOnly")
            .doesNotHaveDependency(runnerLibrary(junit))

        assertThat(project)
            .configuration("testImplementation")
            .doesNotHaveDependency(extensionsLibrary(junit))
        assertThat(project)
            .configuration("testImplementation")
            .doesNotHaveDependency(composeLibrary(junit))
    }

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `add only the main dependencies (androidTest)`(junit: JUnit) {
        project.addJUnit(junit, "androidTest")
        project.evaluate()

        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(coreLibrary(junit))
        assertThat(project)
            .configuration("androidTestRuntimeOnly")
            .hasDependency(runnerLibrary(junit))

        assertThat(project)
            .configuration("androidTestImplementation")
            .doesNotHaveDependency(extensionsLibrary(junit))
        assertThat(project)
            .configuration("androidTestImplementation")
            .doesNotHaveDependency(composeLibrary(junit))
    }

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `allow overriding the version`(junit: JUnit) {
        project.addJUnit(junit, "androidTest")
        project.junitPlatform.instrumentationTests.version.set("1.3.3.7")
        project.evaluate()

        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(coreLibrary(junit, "1.3.3.7"))
        assertThat(project)
            .configuration("androidTestRuntimeOnly")
            .hasDependency(runnerLibrary(junit, "1.3.3.7"))
    }

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `allow overriding the dependencies`(junit: JUnit) {
        project.addJUnit(junit, "androidTest")
        val addedCore = junit.artifact(Instrumentation.core, "0.1.3.3.7")
        val addedRunner = junit.artifact(Instrumentation.runner, "0.1.3.3.7")
        project.dependencies.add("androidTestImplementation", addedCore)
        project.dependencies.add("androidTestRuntimeOnly", addedRunner)
        project.evaluate()

        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(coreLibrary(junit, "0.1.3.3.7"))
        assertThat(project)
            .configuration("androidTestRuntimeOnly")
            .hasDependency(runnerLibrary(junit, "0.1.3.3.7"))
    }

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `do not add the dependencies when disabled`(junit: JUnit) {
        project.addJUnit(junit, "androidTest")
        project.junitPlatform.instrumentationTests.enabled.set(false)
        project.evaluate()

        assertThat(project)
            .configuration("androidTestImplementation")
            .doesNotHaveDependency(coreLibrary(junit))
        assertThat(project)
            .configuration("androidTestRuntimeOnly")
            .doesNotHaveDependency(runnerLibrary(junit))
    }

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `do not add the dependencies when Jupiter is not added`(junit: JUnit) {
        project.evaluate()

        assertThat(project)
            .configuration("androidTestImplementation")
            .doesNotHaveDependency(coreLibrary(junit))
        assertThat(project)
            .configuration("androidTestRuntimeOnly")
            .doesNotHaveDependency(runnerLibrary(junit))
    }

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `do not add the dependencies when Jupiter is not added, even if extension is configured to be added`(
        junit: JUnit
    ) {
        project.junitPlatform.instrumentationTests.includeExtensions.set(true)
        project.evaluate()

        assertThat(project)
            .configuration("androidTestImplementation")
            .doesNotHaveDependency(coreLibrary(junit))
        assertThat(project)
            .configuration("androidTestImplementation")
            .doesNotHaveDependency(extensionsLibrary(junit))
        assertThat(project)
            .configuration("androidTestRuntimeOnly")
            .doesNotHaveDependency(runnerLibrary(junit))
    }

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `add the extension library if configured`(junit: JUnit) {
        project.addJUnit(junit, "androidTest")
        project.junitPlatform.instrumentationTests.includeExtensions.set(true)
        project.evaluate()

        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(coreLibrary(junit))
        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(extensionsLibrary(junit))
        assertThat(project)
            .configuration("androidTestRuntimeOnly")
            .hasDependency(runnerLibrary(junit))
    }

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `add the compose library if configured`(junit: JUnit) {
        project.addJUnit(junit, "test")
        project.addJUnit(junit, "androidTest")
        project.addCompose("test")
        project.addCompose("androidTest")
        project.evaluate()

        assertThat(project).configuration("testImplementation").hasDependency(coreLibrary(junit))
        assertThat(project).configuration("testImplementation").hasDependency(composeLibrary(junit))
        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(coreLibrary(junit))
        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(composeLibrary(junit))
        assertThat(project)
            .configuration("androidTestRuntimeOnly")
            .hasDependency(runnerLibrary(junit))
    }

    @EnumSource(JUnit::class)
    @ParameterizedTest
    fun `add the extensions and compose libraries if configured`(junit: JUnit) {
        project.addJUnit(junit, "test")
        project.addJUnit(junit, "androidTest")
        project.addCompose("test")
        project.addCompose("androidTest")
        project.junitPlatform.instrumentationTests.includeExtensions.set(true)
        project.evaluate()

        assertThat(project).configuration("testImplementation").hasDependency(coreLibrary(junit))
        assertThat(project).configuration("testImplementation").hasDependency(composeLibrary(junit))
        assertThat(project)
            .configuration("testImplementation")
            .hasDependency(extensionsLibrary(junit))
        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(coreLibrary(junit))
        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(composeLibrary(junit))
        assertThat(project)
            .configuration("androidTestImplementation")
            .hasDependency(extensionsLibrary(junit))
        assertThat(project)
            .configuration("androidTestRuntimeOnly")
            .hasDependency(runnerLibrary(junit))
    }

    @Test
    fun `register the filter-write tasks`() {
        project.addJUnit(JUnit5, "androidTest")
        project.evaluate()

        // AGP only registers androidTest tasks for the debug build type
        assertThat(project).task("writeFiltersDebugAndroidTest").exists()
        assertThat(project).task("writeFiltersReleaseAndroidTest").doesNotExist()
    }

    /* Private */

    private fun Project.addJUnit(junit: JUnit, prefix: String) {
        val configuration = if (prefix.isEmpty()) "implementation" else "${prefix}Implementation"
        dependencies.add(configuration, "org.junit.jupiter:junit-jupiter-api:${junit.fullVersion}")
    }

    private fun Project.addCompose(prefix: String) {
        val configuration = if (prefix.isEmpty()) "implementation" else "${prefix}Implementation"
        dependencies.add(configuration, "androidx.compose.ui:ui-test-android:+")
    }

    private fun composeLibrary(junit: JUnit, withVersion: String? = Instrumentation.version) =
        library(Instrumentation.compose, junit, withVersion)

    private fun coreLibrary(junit: JUnit, withVersion: String? = Instrumentation.version) =
        library(Instrumentation.core, junit, withVersion)

    private fun extensionsLibrary(junit: JUnit, withVersion: String? = Instrumentation.version) =
        library(Instrumentation.extensions, junit, withVersion)

    private fun runnerLibrary(junit: JUnit, withVersion: String? = Instrumentation.version) =
        library(Instrumentation.runner, junit, withVersion)

    private fun library(artifactId: String, junit: JUnit, version: String?) =
        junit.artifact(artifactId, version)
}
