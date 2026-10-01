package de.mannodermaus.junit5.internal.discovery

import android.os.Bundle
import de.mannodermaus.junit5.internal.runners.AndroidJUnitFramework
import java.lang.reflect.Method
import org.junit.jupiter.api.TestTemplate
import org.junit.platform.commons.support.AnnotationSupport
import org.junit.platform.engine.DiscoverySelector
import org.junit.platform.engine.discovery.DiscoverySelectors

/**
 * Holder object for the selectors of a test plan. It converts the arguments handed to the Runner by
 * the Android instrumentation into JUnit Platform [DiscoverySelector] objects for the
 * [AndroidJUnitFramework] runner.
 */
internal object ParsedSelectors {
    /**
     * Name of the instrumentation argument that selects individual methods and invocations without
     * going through AndroidX's own `class` filter.
     *
     * Format: comma-separated `com.Foo`, `com.Foo#method` or `com.Foo#method[n]`, where `n` is the
     * 1-based invocation index of a parameterized or repeated test, as shown in its display name.
     * Classes that are not mentioned run nothing.
     */
    const val ARG_JUNIT_SELECTORS = "junitSelectors"

    fun fromBundle(
        testClass: Class<*>,
        arguments: Bundle,
        junitSelectors: String? = null,
    ): List<DiscoverySelector> {
        // An explicit selector list takes precedence over the "class" argument
        if (junitSelectors != null) {
            return fromSelectorsArgument(testClass, junitSelectors)
        }

        // Check if specific class arguments were given to the Runner
        arguments.getString("class", null)?.let { classArg ->
            val testClassName = testClass.name
            val methods = testClass.declaredMethods

            val selectors = mutableListOf<DiscoverySelector>()

            // Separate the provided argument into methods, if any are given
            // (Format: class=com.package1.FirstTest#method1,com.package1.SecondTest#method2).
            // For each component in this string that applies to the test class at hand,
            // consider it a method filter if the name is appended to the component, using a pound
            // sign (#).
            // Finally, if at least one of these method filters can be found, construct JUnit
            // selectors from it
            classArg.split(",").forEach { component ->
                if (!component.startsWith(testClassName)) {
                    // Not the desired class
                    return@forEach
                }

                // Try extracting an appended method name
                var methodName = component.replace(testClassName, "")
                if (!methodName.startsWith("#")) {
                    return@forEach
                }
                methodName = methodName.substring(1)

                // Find all methods with the given name
                val eligibleMethods =
                    methods
                        .filter { it.name == methodName }
                        .map { method -> DiscoverySelectors.selectMethod(testClass, method) }

                selectors += eligibleMethods
            }

            if (selectors.isNotEmpty()) {
                // Restrictions to specific methods apply
                return selectors
            }
        }

        // If nothing else was specified to the runner, assume that all classes should be run
        return listOf(DiscoverySelectors.selectClass(testClass))
    }

    private val METHOD_COMPONENT = Regex("""([^\[\]]+)(?:\[(\d+)])?""")

    // Unlike the "class" argument, this argument is not read by AndroidX's TestRequestBuilder,
    // so several methods of the same class can be selected in one run: their reported names
    // never have to match the raw method names that AndroidX's method filter compares against.
    private fun fromSelectorsArgument(testClass: Class<*>, arg: String): List<DiscoverySelector> {
        val testClassName = testClass.name
        val selectors = mutableListOf<DiscoverySelector>()

        arg.split(",").map(String::trim).filter(String::isNotEmpty).forEach { component ->
            val hashIndex = component.indexOf('#')
            val className = if (hashIndex < 0) component else component.substring(0, hashIndex)

            // Entries apply to the class itself, or to a @Nested class inside of it
            // (AndroidX does not hand nested classes to the runner on its own)
            val targetClass =
                when {
                    className == testClassName -> testClass
                    className.startsWith(testClassName + "\$") ->
                        loadNestedClass(testClass, className)
                    else -> null
                } ?: return@forEach
            val enclosingClasses = enclosingClassesOf(targetClass, testClass)

            if (hashIndex < 0) {
                selectors +=
                    if (enclosingClasses.isEmpty()) {
                        DiscoverySelectors.selectClass(targetClass)
                    } else {
                        DiscoverySelectors.selectNestedClass(enclosingClasses, targetClass)
                    }
                return@forEach
            }

            val match =
                METHOD_COMPONENT.matchEntire(component.substring(hashIndex + 1)) ?: return@forEach
            val methodName = match.groupValues[1]
            val invocation = match.groupValues[2].toIntOrNull()

            targetClass.declaredMethods
                .filter { it.name == methodName }
                .forEach { method ->
                    selectors +=
                        if (invocation != null && invocation > 0 && method.isTestTemplate()) {
                            // Iteration selectors take a plain method selector as their parent;
                            // Jupiter resolves the enclosing classes of a @Nested class itself
                            DiscoverySelectors.selectIteration(
                                DiscoverySelectors.selectMethod(targetClass, method),
                                invocation - 1,
                            )
                        } else if (enclosingClasses.isEmpty()) {
                            DiscoverySelectors.selectMethod(targetClass, method)
                        } else {
                            DiscoverySelectors.selectNestedMethod(
                                enclosingClasses,
                                targetClass,
                                method,
                            )
                        }
                }
        }

        return selectors.distinct()
    }

    private fun loadNestedClass(testClass: Class<*>, className: String): Class<*>? =
        try {
            Class.forName(className, false, testClass.classLoader)
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: LinkageError) {
            null
        }

    // Outermost first, ending with the direct enclosing class of the given nested class
    private fun enclosingClassesOf(nestedClass: Class<*>, testClass: Class<*>): List<Class<*>> {
        val chain = mutableListOf<Class<*>>()
        var current = nestedClass.enclosingClass
        while (current != null) {
            chain.add(0, current)
            if (current == testClass) break
            current = current.enclosingClass
        }
        return if (nestedClass == testClass) emptyList() else chain
    }

    private fun Method.isTestTemplate(): Boolean =
        AnnotationSupport.isAnnotated(this, TestTemplate::class.java)
}
