package de.mannodermaus.gradle.plugins.junit5

import de.mannodermaus.gradle.plugins.junit5.internal.config.MIN_REQUIRED_AGP_VERSION
import de.mannodermaus.gradle.plugins.junit5.internal.config.PluginConfig
import de.mannodermaus.gradle.plugins.junit5.internal.configureJUnitFramework
import de.mannodermaus.gradle.plugins.junit5.internal.extensions.whenAndroidPluginAdded
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Android JUnit Platform plugin for Gradle. Configures JUnit 5 tasks on all unit-tested variants of
 * an Android project.
 */
public class AndroidJUnitPlatformPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit =
        with(project) {
            whenAndroidPluginAdded { plugin ->
                PluginConfig.find(this, plugin)?.let { config ->
                    require(config.currentAgpVersion >= MIN_REQUIRED_AGP_VERSION) {
                        "android-junit plugin requires Android Gradle Plugin $MIN_REQUIRED_AGP_VERSION or later"
                    }

                    configureJUnitFramework(config)
                }
            }
        }
}
