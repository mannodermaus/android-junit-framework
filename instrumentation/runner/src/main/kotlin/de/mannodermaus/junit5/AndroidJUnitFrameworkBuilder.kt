package de.mannodermaus.junit5

import android.util.Log
import de.mannodermaus.junit5.internal.LOG_TAG
import de.mannodermaus.junit5.internal.LibcoreAccess
import de.mannodermaus.junit5.internal.runners.JUnitFrameworkRunnerParams
import de.mannodermaus.junit5.internal.runners.tryCreateJUnitFrameworkRunner
import org.junit.runner.Runner
import org.junit.runners.model.RunnerBuilder

/**
 * Custom RunnerBuilder hooked into the main Test Instrumentation Runner provided by the Android
 * Test Support Library, which allows to run the JUnit Platform for instrumented tests. With this,
 * the default JUnit 4-based Runner for Android instrumented tests is, in a way, tricked into
 * detecting JUnit Jupiter tests as well.
 *
 * The RunnerBuilder is added to the instrumentation runner through a custom
 * "testInstrumentationRunnerArgument" in the build.gradle script:
 * <pre>
 *   android {
 *     defaultConfig {
 *       testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 *       testInstrumentationRunnerArgument("runnerBuilder", "de.mannodermaus.junit5.AndroidJUnitFrameworkBuilder")
 *     }
 *   }
 * </pre>
 *
 * (Suppressing unused, since this is hooked into the project configuration via a Test
 * Instrumentation Runner Argument.)
 */
public open class AndroidJUnitFrameworkBuilder internal constructor() : RunnerBuilder() {
    private val junitFrameworkAvailable by lazy {
        try {
            // The verification order of this block is quite important.
            // Do not change it without thorough testing of potential consequences!
            // After tampering with this, verify that integration
            // with applications using JUnit 5 for UI tests still works,
            // AND that integration with applications NOT using JUnit 5 for UI tests still works.
            //
            // First, verify the existence of junit-jupiter-api on the classpath.
            // Then, verify that the Android JUnit Framework Runner is available.
            Class.forName("org.junit.jupiter.api.Test")
            Class.forName("de.mannodermaus.junit5.internal.runners.AndroidJUnitFramework")
            true
        } catch (_: Throwable) {
            false
        }
    }

    // One-time parsing setup for runner params, taken from instrumentation arguments
    private val params by lazy {
        JUnitFrameworkRunnerParams.create().also { params ->
            // Apply all environment variables & system properties to the running process
            params.registerEnvironmentVariables()
            params.registerSystemProperties()
        }
    }

    @Throws(Throwable::class)
    override fun runnerForClass(testClass: Class<*>): Runner? {
        try {
            return if (junitFrameworkAvailable) {
                tryCreateJUnitFrameworkRunner(testClass, params)
            } else {
                null
            }
        } catch (e: NoClassDefFoundError) {
            Log.e(LOG_TAG, "JUnitPlatform not found on runtime classpath")
            throw IllegalStateException(
                "junit-platform-runner not found on runtime classpath of instrumentation tests; " +
                    "please review your androidTest dependencies or raise an issue.",
                e,
            )
        } catch (e: Throwable) {
            Log.e(LOG_TAG, "Error constructing runner", e)
            throw e
        }
    }

    /* Private */

    private fun JUnitFrameworkRunnerParams.registerEnvironmentVariables() {
        environmentVariables.forEach { (key, value) ->
            try {
                LibcoreAccess.setenv(key, value)
            } catch (t: Throwable) {
                Log.w(LOG_TAG, "Error while setting up environment variables.", t)
            }
        }
    }

    private fun JUnitFrameworkRunnerParams.registerSystemProperties() {
        systemProperties.forEach { (key, value) ->
            try {
                System.setProperty(key, value)
            } catch (t: Throwable) {
                Log.w(LOG_TAG, "Error while setting up system properties.", t)
            }
        }
    }
}

/**
 * Custom RunnerBuilder hooked into the main Test Instrumentation Runner provided by the Android
 * Test Support Library, which allows to run the JUnit Platform for instrumented tests. With this,
 * the default JUnit 4-based Runner for Android instrumented tests is, in a way, tricked into
 * detecting JUnit Jupiter tests as well.
 *
 * The RunnerBuilder is added to the instrumentation runner through a custom
 * "testInstrumentationRunnerArgument" in the build.gradle script:
 * <pre>
 *   android {
 *     defaultConfig {
 *       testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 *       testInstrumentationRunnerArgument("runnerBuilder", "de.mannodermaus.junit5.AndroidJUnitFrameworkBuilder")
 *     }
 *   }
 * </pre>
 *
 * (Suppressing unused, since this is hooked into the project configuration via a Test
 * Instrumentation Runner Argument.)
 */
@Deprecated(
    message = "Renamed to AndroidJUnitFrameworkBuilder",
    replaceWith = ReplaceWith("AndroidJUnitFrameworkBuilder"),
)
public class AndroidJUnit5Builder : AndroidJUnitFrameworkBuilder()
