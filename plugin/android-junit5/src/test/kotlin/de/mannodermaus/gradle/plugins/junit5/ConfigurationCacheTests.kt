package de.mannodermaus.gradle.plugins.junit5

import com.google.common.truth.Truth.assertWithMessage
import de.mannodermaus.gradle.plugins.junit5.annotations.DisabledOnCI
import de.mannodermaus.gradle.plugins.junit5.util.TestEnvironment
import de.mannodermaus.gradle.plugins.junit5.util.assertThat
import de.mannodermaus.gradle.plugins.junit5.util.prettyPrint
import de.mannodermaus.gradle.plugins.junit5.util.projects.FunctionalTestProjectCreator
import de.mannodermaus.gradle.plugins.junit5.util.withPrunedPluginClasspath
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir

@TestInstance(PER_CLASS)
@DisabledOnCI
class ConfigurationCacheTests {

    private val environment = TestEnvironment()
    private val agp = environment.supportedAgpVersions.last()
    private lateinit var projectCreator: FunctionalTestProjectCreator

    @BeforeAll
    fun beforeAll(@TempDir folder: File) {
        projectCreator = FunctionalTestProjectCreator(folder, environment)
        println("Running configuration cache tests against latest AGP ($agp)...")

        // Ensure that no ADB device is connected before starting these tests,
        // as they have a certain expectation of "no device connected" for asserting their output
        ensureNoAdbDeviceConnected()
    }

    @TestFactory
    fun `test instrumentation tasks`() =
        environment.supportedJUnitVersions.map { junit ->
            dynamicTest("JUnit ${junit.majorVersion}") {
                // Test configuration cache with one specific project and AGP version
                val spec = projectCreator.specNamed("instrumentation-tests")
                val project = projectCreator.createProject(spec, agp, junit)

                // Run it once; this is supposed to fail, but JUST because of 'no connected device',
                // not because of other errors including the configuration cache.
                runGradle(project, "connectedCheck", expectSuccess = false).assertWithLogging {
                    assertThat(it).task(":connectedDebugAndroidTest").hasOutcome(FAILED)
                    assertThat(it).output().contains("DeviceException: No connected devices!")
                }

                // Run it again, expecting to see a successful reuse of the configuration cache
                runGradle(project, "connectedCheck", expectSuccess = false).assertWithLogging {
                    assertThat(it).output().contains("Reusing configuration cache.")
                }
            }
        }

    @TestFactory
    fun `test unit tasks`() =
        environment.supportedJUnitVersions.map { junit ->
            val spec = projectCreator.specNamed("product-flavors")
            val project = projectCreator.createProject(spec, agp, junit)

            runGradle(project, "help", expectSuccess = true).assertWithLogging {
                assertThat(it).task(":help").hasOutcome(SUCCESS)
            }

            runGradle(project, "help", expectSuccess = true).assertWithLogging {
                assertThat(it).output().contains("Reusing configuration cache.")
            }
        }

    /* Private */

    private fun ensureNoAdbDeviceConnected() {
        val process = ProcessBuilder("adb", "devices").start()
        val output = process.inputStream.bufferedReader().use { it.lines().toList() }

        assertWithMessage(
                "This test requires no ADB device to be connected; disconnect all and retry."
            )
            .that(output)
            .containsExactly("List of devices attached", "")
    }

    private fun runGradle(project: File, task: String, expectSuccess: Boolean) =
        GradleRunner.create()
            .withProjectDir(project)
            .withGradleVersion(agp.requiresGradle)
            .withArguments("--configuration-cache", "--stacktrace", task)
            .withPrunedPluginClasspath(agp)
            .run { if (expectSuccess) build() else buildAndFail() }

    private fun BuildResult.assertWithLogging(block: (BuildResult) -> Unit) {
        try {
            block(this)
        } catch (e: Throwable) {
            this.prettyPrint()
            throw e
        }
    }
}
