package de.mannodermaus.junit5.internal.runners

import com.google.common.truth.Truth.assertThat
import de.mannodermaus.junit5.testutil.AndroidBuildUtils.withMockedInstrumentation
import de.mannodermaus.junit5.testutil.CollectingRunListener
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.runner.notification.RunNotifier

class JUnitSelectorsTests {
    private val cls = Sample_SelectorTests::class.java.name

    @Test
    fun `several methods of one class run in one request, with their normal names`() {
        val names = run("$cls#displayed,$cls#repeated")

        assertThat(names)
            .containsExactly(
                "pretty name",
                "repeated - repetition 1 of 2",
                "repeated - repetition 2 of 2",
            )
    }

    @Test
    fun `a single invocation of a parameterized test can be selected`() {
        assertThat(run("$cls#param[2]")).containsExactly("param[String] - [2] b")
    }

    @Test
    fun `a single invocation of a repeated test can be selected`() {
        assertThat(run("$cls#repeated[1]")).containsExactly("repeated - repetition 1 of 2")
    }

    @Test
    fun `a single method keeps the normal name format`() {
        assertThat(run("$cls#displayed")).containsExactly("pretty name")
    }

    @Test
    fun `an index on a non-template method selects the whole method`() {
        assertThat(run("$cls#displayed[3]")).containsExactly("pretty name")
    }

    @Test
    fun `dynamic tests are selected through their factory method`() {
        assertThat(run("$cls#factory")).containsExactly("factory - one", "factory - two")
    }

    @Test
    fun `a class name alone selects the whole class`() {
        assertThat(run(cls)).hasSize(10)
    }

    @Test
    fun `a class that is not mentioned runs nothing`() {
        assertThat(run("com.example.Other#displayed")).isEmpty()
    }

    @Test
    fun `methods of a nested class are selected through the outer class`() {
        assertThat(run("$cls\$Group#inner,$cls#plain")).containsExactly("plain", "inner")
    }

    @Test
    fun `a nested class name alone selects only that nested class`() {
        assertThat(run("$cls\$Group")).containsExactly("inner", "innerParam[String] - [1] x")
    }

    @Test
    fun `a single invocation inside a nested class can be selected`() {
        assertThat(run("$cls\$Group#innerParam[1]")).containsExactly("innerParam[String] - [1] x")
    }

    @Test
    fun `whitespace and duplicates are tolerated`() {
        assertThat(run(" $cls#displayed , $cls#displayed ,")).containsExactly("pretty name")
    }

    /* Private */

    private fun run(junitSelectors: String): List<String> {
        val result = AtomicReference<List<String>>()
        withMockedInstrumentation {
            val runner =
                AndroidJUnitFramework(
                    testClass = Sample_SelectorTests::class.java,
                    params = JUnitFrameworkRunnerParams(junitSelectors = junitSelectors),
                )
            val listener = CollectingRunListener()
            runner.run(RunNotifier().also { it.addListener(listener) })
            result.set(listener.getResults().successfulTests.mapNotNull { it.methodName })
        }

        return result.get()
    }
}

/* Data */

@Suppress("ClassName")
internal class Sample_SelectorTests {
    @Test @DisplayName("pretty name") fun displayed() {}

    @Test fun plain() {}

    @RepeatedTest(2) fun repeated() {}

    @ParameterizedTest @ValueSource(strings = ["a", "b"]) fun param(value: String) {}

    @TestFactory fun factory() = listOf(dynamicTest("one") {}, dynamicTest("two") {})

    @Nested
    inner class Group {
        @Test fun inner() {}

        @ParameterizedTest @ValueSource(strings = ["x"]) fun innerParam(value: String) {}
    }
}
