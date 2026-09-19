@file:Suppress("unused")

package de.mannodermaus.gradle.plugins.junit5.internal.config

import com.android.build.api.AndroidPluginVersion
import org.gradle.util.GradleVersion

// When updating this, check buildSrc/Tasks.kt and update it there, too
internal val MIN_REQUIRED_GRADLE_VERSION = GradleVersion.version("8.2")
internal val MIN_REQUIRED_AGP_VERSION = AndroidPluginVersion(8, 2)

internal const val EXTENSION_NAME = "junitPlatform"

internal const val ANDROID_JUNIT_RUNNER_BUILDER_CLASS =
    "de.mannodermaus.junit5.AndroidJUnitFrameworkBuilder"
internal const val INSTRUMENTATION_RUNNER_LIBRARY_GROUP = "de.mannodermaus.junit5"
internal const val INSTRUMENTATION_RUNNER_LIBRARY_ARTIFACT = "android-test-runner"

// Android doesn't allow '.' in resource file names,
// saying that it is not a valid file-based resource name character:
// File-based resource names must contain only lowercase a-z, 0-9, or underscore
internal const val INSTRUMENTATION_FILTER_RES_FILE_NAME = "de_mannodermaus_junit5_filters"
