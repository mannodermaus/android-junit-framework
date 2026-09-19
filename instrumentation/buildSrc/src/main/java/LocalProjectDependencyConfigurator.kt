import extensions.library
import extensions.libs
import org.gradle.api.Project

private val applicableConfigurationNameRegex =
    Regex("[a-z]+[A-Z][a-z]+((Android)|(Unit))Test[A-Z][a-z]+Classpath")

private val instrumentationLibraryRegex =
    Regex("de\\.mannodermaus\\.junit5:android-test-([a-z0-9]+)(-.+)?:")

fun Project.replaceAndroidTestLibsWithLocalProjectDependencies() {
    val self = this

    configurations.all {
        val configuration = this

        if (!name.matches(applicableConfigurationNameRegex)) {
            return@all
        }

        resolutionStrategy {
            // Force a specific version of the JUnit BOM, depending on the actual configuration
            SupportedJUnit.values().forEach { junit ->
                if (name.startsWith(junit.variant)) {
                    force(
                        when (junit) {
                            SupportedJUnit.JUnit5 -> libs.library("junit-framework-bom5")
                            SupportedJUnit.JUnit6 -> libs.library("junit-framework-bom6")
                        }
                    )
                }
            }

            // Use local project dependencies on android-test instrumentation libraries
            // for the modules inside this repository, instead of relying on their Maven coords.
            // This is not needed for normal consumers, but we need it to test our own code.
            dependencySubstitution.all {
                instrumentationLibraryRegex.find(requested.toString())?.let { result ->
                    val replacement = project(":${result.groupValues[1]}")
                    logger.lifecycle(
                        "$self will replace dependency on '$requested' " +
                                "with $replacement in $configuration"
                    )
                    useTarget(replacement, "Use $replacement to substitute dependency '$requested'")
                }
            }
        }
    }
}
