@file:Suppress("UnstableApiUsage", "unused")

package de.mannodermaus.gradle.plugins.junit5.internal

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.Variant
import com.android.builder.core.ComponentType.Companion.UNIT_TEST_PREFIX
import com.android.builder.core.ComponentType.Companion.UNIT_TEST_SUFFIX
import de.mannodermaus.Libraries
import de.mannodermaus.Libraries.Instrumentation
import de.mannodermaus.gradle.plugins.junit5.dsl.AndroidJUnitPlatformExtension
import de.mannodermaus.gradle.plugins.junit5.internal.config.ANDROID_JUNIT_RUNNER_BUILDER_CLASS
import de.mannodermaus.gradle.plugins.junit5.internal.config.EXTENSION_NAME
import de.mannodermaus.gradle.plugins.junit5.internal.config.JUnitPlatformTaskConfig
import de.mannodermaus.gradle.plugins.junit5.internal.config.PluginConfig
import de.mannodermaus.gradle.plugins.junit5.internal.extensions.getAsList
import de.mannodermaus.gradle.plugins.junit5.internal.extensions.getTaskName
import de.mannodermaus.gradle.plugins.junit5.internal.extensions.instrumentationTestVariant
import de.mannodermaus.gradle.plugins.junit5.internal.extensions.junit5Warn
import de.mannodermaus.gradle.plugins.junit5.internal.extensions.namedOrNull
import de.mannodermaus.gradle.plugins.junit5.internal.extensions.usesJUnitJupiterIn
import de.mannodermaus.gradle.plugins.junit5.internal.usage.DependencyUsageDetector
import de.mannodermaus.gradle.plugins.junit5.tasks.AndroidJUnit5JacocoReport
import de.mannodermaus.gradle.plugins.junit5.tasks.AndroidJUnit5WriteFilters
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

internal fun Project.configureJUnitFramework(config: PluginConfig) {
    val project = this
    val extension = extensions.create(EXTENSION_NAME, AndroidJUnitPlatformExtension::class.java)

    with(extension) {
        // General-purpose filters
        filters(qualifier = null)

        config.finalizeDsl { android ->
            prepareBuildTypeDsl(android)
            prepareUnitTests(project, android)
            prepareInstrumentationTests(project, android)
        }

        val variants = mutableSetOf<Variant>()
        config.onVariants { variant ->
            prepareVariantDsl(variant)
            variants.add(variant)
        }

        project.afterEvaluate {
            variants.forEach { variant ->
                configureUnitTests(it, variant)
                configureJacoco(it, config, variant)
                configureInstrumentationTests(it, variant)
            }
        }
    }
}

/* Private */

private typealias AndroidExtension = CommonExtension<*, *, *, *, *>

private fun AndroidJUnitPlatformExtension.prepareBuildTypeDsl(android: AndroidExtension) {
    // This will add filters for build types (e.g. "debug" or "release")
    android.buildTypes.all { buildType ->
        // "debugFilters"
        // "releaseFilters"
        filters(qualifier = buildType.name)
    }
}

private fun AndroidJUnitPlatformExtension.prepareVariantDsl(variant: Variant) {
    // Attach DSL objects for all permutations of variants available.
    // As an example, assume the incoming `variant` to be:
    // Name:                    "brandADevelopmentDebug"
    // Dimension "brand":       "brandA"
    // Dimension "environment": "development"
    // Build Type Name:         "debug"
    //
    // The following DSL objects have to be generated from this:
    // 1) brandADevelopmentDebugFilters
    // 2) brandAFilters
    // 3) developmentFilters
    // ----------------

    // 1) Fully-specialized variant ("brandADevelopmentDebugFilters")
    filters(qualifier = variant.name)

    variant.productFlavors.forEach { flavor ->
        // 2) & 3) Single flavors ("brandAFilters" & "developmentFilters")
        filters(qualifier = flavor.second)
    }
}

private fun AndroidJUnitPlatformExtension.prepareUnitTests(
    project: Project,
    android: AndroidExtension,
) {
    // Add default ignore rules for JUnit 5 metadata files to the packaging options of the plugin,
    // so that consumers don't need to do this explicitly
    android.packaging.resources.excludes.addAll(
        listOf("/META-INF/LICENSE.md", "/META-INF/LICENSE-notice.md")
    )
    attachDependencies(project, "testImplementation")
}

private fun AndroidJUnitPlatformExtension.prepareInstrumentationTests(
    project: Project,
    android: AndroidExtension,
) {
    // Automatically configure instrumentation tests when JUnit 5 is detected in that configuration
    if (!instrumentationTests.enabled.get()) return
    if (!project.usesJUnitJupiterIn("androidTestImplementation")) return

    val runnerArgs = android.defaultConfig.testInstrumentationRunnerArguments

    // Attach the JUnit 5 RunnerBuilder to the list, unless it's already added
    val runnerBuilders = runnerArgs.getAsList("runnerBuilder")
    if (ANDROID_JUNIT_RUNNER_BUILDER_CLASS !in runnerBuilders) {
        runnerArgs["runnerBuilder"] =
            runnerBuilders
                .toMutableList()
                .also { it.add(ANDROID_JUNIT_RUNNER_BUILDER_CLASS) }
                .joinToString(",")
    }

    // Copy over configuration parameters to instrumentation tests
    if (instrumentationTests.useConfigurationParameters.get()) {
        runnerArgs.addConfigurationParameters(this.configurationParameters.get())
    }

    // Supply behavior value for unsupported devices (i.e. how to react when running on old devices)
    runnerArgs.addConfigurationParameter(
        "de.mannodermaus.junit.unsupported.behavior",
        instrumentationTests.behaviorForUnsupportedDevices.get().value,
    )

    attachDependencies(project, "androidTestImplementation")
}

private fun MutableMap<String, String>.addConfigurationParameters(values: Map<String, String>) {
    if (values.isEmpty()) return

    val instrumentationParams = this.getAsList("configurationParameters").toMutableList()
    values.forEach { (key, value) -> instrumentationParams.add("$key=$value") }
    this["configurationParameters"] = instrumentationParams.joinToString(",")
}

private fun MutableMap<String, String>.addConfigurationParameter(key: String, value: String) {
    addConfigurationParameters(mapOf(key to value))
}

/**
 * Construct an artifact ID (i.e. `de.mannodermaus:hoge:1.2.3`) compatible with the given supported
 * JUnit version. Depending on the JUnit version, the artifact ID may include a version-specific
 * suffix string as well.
 */
internal fun Libraries.JUnit.artifact(base: String, version: String?) = buildString {
    append(base)
    this@artifact.artifactIdSuffix?.let { suffix -> append('-').append(suffix) }
    version?.let {
        append(':')
        append(version)
    }
}

private fun AndroidJUnitPlatformExtension.attachDependencies(
    project: Project,
    configurationName: String,
) {
    val detector = DependencyUsageDetector(project)

    detector.isUsingJUnit(configurationName)?.let { usage ->
        val includeRunner = "android" in configurationName
        val runtimeOnly = configurationName.replace("Implementation", "RuntimeOnly")
        val version = instrumentationTests.version.get()

        // First, apply the core library
        project.dependencies.add(
            configurationName,
            usage.junit.artifact(Instrumentation.core, version),
        )

        // Add some runtime dependencies, including a reference to the JUnit BOM
        project.dependencies.add(
            runtimeOnly,
            project.dependencies.platform("org.junit:junit-bom:${usage.junit.fullVersion}"),
        )
        project.dependencies.add(runtimeOnly, Libraries.junitPlatformLauncher)

        if (includeRunner) {
            project.dependencies.add(
                runtimeOnly,
                usage.junit.artifact(Instrumentation.runner, version),
            )
        }

        // Add optional artifacts
        if (instrumentationTests.includeExtensions.get()) {
            project.dependencies.add(
                configurationName,
                usage.junit.artifact(Instrumentation.extensions, version),
            )
        }

        if (detector.isUsingCompose(configurationName)) {
            project.dependencies.add(
                configurationName,
                usage.junit.artifact(Instrumentation.compose, version),
            )
        }
    }
}

private fun AndroidJUnitPlatformExtension.configureUnitTests(project: Project, variant: Variant) {
    val taskName = variant.getTaskName(prefix = UNIT_TEST_PREFIX, suffix = UNIT_TEST_SUFFIX)
    project.tasks.namedOrNull<Test>(taskName)?.configure { testTask ->
        val taskConfig = JUnitPlatformTaskConfig(variant, this)

        testTask.useJUnitPlatform { options ->
            options.includeTags(*taskConfig.combinedIncludeTags)
            options.excludeTags(*taskConfig.combinedExcludeTags)
            options.includeEngines(*taskConfig.combinedIncludeEngines)
            options.excludeEngines(*taskConfig.combinedExcludeEngines)
        }

        testTask.include(*taskConfig.combinedIncludePatterns)
        testTask.exclude(*taskConfig.combinedExcludePatterns)

        // From the User Guide:
        // "The standard Gradle test task currently does not provide a dedicated DSL
        // to set JUnit Platform configuration parameters to influence test discovery and execution.
        // However, you can provide configuration parameters within the build script via system
        // properties"
        testTask.systemProperties(configurationParameters.get())
    }
}

private fun AndroidJUnitPlatformExtension.configureJacoco(
    project: Project,
    config: PluginConfig,
    variant: Variant,
) {
    // Connect a Code Coverage report to it if Jacoco is enabled
    if (jacocoOptions.taskGenerationEnabled.get() && config.hasJacocoPlugin) {
        val taskName = variant.getTaskName(prefix = UNIT_TEST_PREFIX, suffix = UNIT_TEST_SUFFIX)
        project.tasks.namedOrNull<Test>(taskName)?.get()?.let { testTask ->
            // Create a Jacoco friend task
            val enabledVariants = jacocoOptions.onlyGenerateTasksForVariants.get()
            if (enabledVariants.isEmpty() || variant.name in enabledVariants) {
                // Capture an empty return value here and highlight
                // the unavailability of Jacoco integration on certain AGP versions
                // (namely, AGP 9.0.0+ with the new DSL). This feature is effectively deprecated
                val directoryProviders = config.directoryProvidersOf(variant)
                val registeredTask =
                    AndroidJUnit5JacocoReport.register(
                        project = project,
                        variant = variant,
                        testTask = testTask,
                        directoryProviders = directoryProviders,
                    )

                if (directoryProviders.isNotEmpty()) {
                    // Log a warning if Jacoco tasks already existed
                    if (registeredTask == null) {
                        project.logger.junit5Warn(
                            "Jacoco task for variant '${variant.name}' already exists." +
                                "Disabling customization for JUnit 5..."
                        )
                    }
                } else {
                    // Disable any task that may have been registered above
                    registeredTask?.configure { it.enabled = false }

                    project.logger.junit5Warn(
                        buildString {
                            append(
                                "Cannot configure Jacoco for this project because directory providers cannot be found."
                            )

                            if (config.currentAgpVersion.major >= 9) {
                                append(
                                    " This integration is deprecated from AGP 9.0.0 onwards because of the new DSL."
                                )
                                append(" Please consult the link below for more information: ")
                                append("https://developer.android.com/build/releases/agp-preview")
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun AndroidJUnitPlatformExtension.configureInstrumentationTests(
    project: Project,
    variant: Variant,
) {
    if (!instrumentationTests.enabled.get()) return

    variant.instrumentationTestVariant?.sources?.res?.let { sourceDirs ->
        AndroidJUnit5WriteFilters.register(project, variant, sourceDirs)
    }
}
