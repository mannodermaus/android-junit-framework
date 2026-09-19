package de.mannodermaus.junit5.internal.runners

import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import de.mannodermaus.junit5.internal.discovery.EmptyTestPlan
import de.mannodermaus.junit5.internal.runners.notification.ParallelRunNotifier
import org.junit.platform.commons.JUnitException
import org.junit.platform.engine.discovery.MethodSelector
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier

/**
 * JUnit Runner implementation using the JUnit Platform as its backbone. Serves as an intermediate
 * solution to writing JUnit 5-based instrumentation tests until official support arrives for this.
 */
@RequiresApi(26)
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
internal class AndroidJUnitFramework(
    private val testClass: Class<*>,
    params: JUnitFrameworkRunnerParams,
) : Runner() {
    private companion object {
        private val launcher = LauncherFactory.create()

        // Fallback for irrelevant classes passed to JUnit 4's RunnerBuilder
        // (no test tree will be created for those, avoiding any potentially dangerous
        // runtime lookups that can cause issues like `mannodermaus/android-junit-framework/413`)
        private val emptyDescription = Description.createSuiteDescription("<empty>")
    }

    private val testTree by lazy { generateTestTree(params) }

    override fun getDescription(): Description = testTree?.suiteDescription ?: emptyDescription

    override fun run(notifier: RunNotifier) {
        testTree?.let { tree ->
            launcher.execute(
                tree.testPlan,
                AndroidJUnitPlatformRunnerListener(tree, tree.createNotifier(notifier)),
            )
        }
    }

    /* Private */

    private fun generateTestTree(
        params: JUnitFrameworkRunnerParams
    ): AndroidJUnitPlatformTestTree? {
        val selectors = params.createSelectors(testClass)
        val isIsolatedMethodRun = selectors.size == 1 && selectors.first() is MethodSelector
        val isUsingOrchestrator = params.isUsingOrchestrator
        val request = params.createDiscoveryRequest(selectors)

        // Validate if run can be executed
        if (isUsingOrchestrator && params.isParallelExecutionEnabled) {
            throw RuntimeException(
                """
                Running tests with the Android Test Orchestrator does not work with parallel tests,
                since some information must be retained across parallel test execution,
                and the isolated nature of the Android Test Orchestrator thwarts these efforts.
                Please disable either setting and try again.
                """
                    .trimIndent()
            )
        }

        val testPlan =
            try {
                launcher.discover(request)
            } catch (e: JUnitException) {
                // Each class in scope is given to the runner,
                // but some may fail to be loaded by the class loader
                // (e.g. when they are tailored to JVM work and reference sun.* classes
                // or anything else not present in the Android runtime).
                // Log those to console, but discard them from being considered at all
                e.printStackTrace()
                EmptyTestPlan
            }

        return if (testPlan.containsTests()) {
            AndroidJUnitPlatformTestTree(
                testPlan = testPlan,
                testClass = testClass,
                needLegacyFormat = isIsolatedMethodRun || isUsingOrchestrator,
                isParallelExecutionEnabled = params.isParallelExecutionEnabled,
            )
        } else {
            null
        }
    }

    private fun AndroidJUnitPlatformTestTree.createNotifier(nextNotifier: RunNotifier) =
        if (isParallelExecutionEnabled) {
            // Wrap the default notifier with a special handler for parallel test execution
            ParallelRunNotifier(nextNotifier)
        } else {
            nextNotifier
        }
}
