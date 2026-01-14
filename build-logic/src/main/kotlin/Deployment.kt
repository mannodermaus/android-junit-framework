@file:Suppress("UNCHECKED_CAST")

import groovy.lang.Closure
import groovy.util.Node
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.internal.metadata.ModuleMetadataSpec
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.Try
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.maybeCreate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Configure deployment tasks and properties for a project using the provided [deployConfig].
 */
fun Project.configureDeployment(deployConfig: Deployed) {
    check(this != rootProject) { "This method can not be called on the root project" }

    val credentials = DeployedCredentials(this)

    // Configure root project (this only happens once and will raise an error on inconsistent data)
    rootProject.configureRootDeployment(deployConfig, credentials)

    // Apply deployment config for each type of project
    plugins.withId("com.android.library") { configureAndroidDeployment(deployConfig, credentials) }
    plugins.withId("java-gradle-plugin") { configurePluginDeployment(deployConfig, credentials) }
}

/* Private */

private fun Project.configureRootDeployment(
    deployConfig: Deployed,
    credentials: DeployedCredentials
) {
    check(this == rootProject) { "This method can only be called on the root project" }

    // Validate the integrity of published versions
    // (all subprojects must use the same group ID and version number or else an error is raised)
    if (version != "unspecified") {
        check(version == deployConfig.currentVersion && group == deployConfig.groupId) {
            "A subproject tried to set '${deployConfig.groupId}:${deployConfig.currentVersion}' " +
                    "as the coordinates for the artifacts of the repository, but '$group:$version' was already set " +
                    "previously by a different subproject. As per the requirements of the Nexus Publishing plugin, " +
                    "all subprojects must use the same version number! Please check Environment.kt for inconsistencies!"
        }

        // Already configured and correct
        return
    }

    // One-time initialization beyond this point
    group = deployConfig.groupId
    version = deployConfig.currentVersion

    centralPublishing(
        packageGroup = deployConfig.groupId,
        stagingProfileId = credentials.sonatypeStagingProfileId,
        username = credentials.centralUsername,
        password = credentials.centralPassword
    )
}

private fun Project.configureCommonDeployment(
    deployConfig: Deployed,
    credentials: DeployedCredentials
) {
    apply {
        plugin("maven-publish")
        plugin("signing")
        plugin("org.jetbrains.dokka")
    }

    // Setup publication details
    group = deployConfig.groupId
    version = deployConfig.currentVersion

    // Setup code signing
    ext["signing.keyId"] = credentials.signingKeyId
    ext["signing.password"] = credentials.signingPassword
    ext["signing.secretKeyRingFile"] = credentials.signingKeyRingFile
    signing {
        sign(publishing.publications)
    }
}

private fun Project.configureAndroidDeployment(
    deployConfig: Deployed,
    credentials: DeployedCredentials
) {
    val android = AndroidDsl(this)
    configureCommonDeployment(deployConfig, credentials)

    // Create a publication for each variant
    SupportedJUnit.values().forEach { junit ->
        val variantName = "${junit.variant}Release"

        android.publishing.singleVariant(variantName) {
            withSourcesJar()
            withJavadocJar()
        }

        afterEvaluate {
            publishing {
                publications {
                    // Declare an empty 'main' publication and mark the actual publication
                    // for each variant as an 'alias'. This is done to work around limitations
                    // of the Maven publication process
                    maybeCreate<MavenPublication>("main")

                    register<MavenPublication>(junit.variant) {
                        from(components.getByName(variantName))

                        applyPublicationDetails(
                            project = this@afterEvaluate,
                            deployConfig = deployConfig,
                            junit = junit
                        )

                        (this as PublicationInternal<MavenArtifact>).isAlias = true

                        // We have to write parts of the POM file ourselves, so that
                        // project dependencies between instrumentation libraries use the correct
                        // coordinates for each variant (e.g. "junit5" vs "junit6")
                        configurePom(deployConfig)
                    }
                }
            }
        }
    }

    // Disable main publication
    tasks.withType<AbstractPublishToMaven> {
        isEnabled = "Main" !in name
    }

    // Hook into Gradle module metadata generation
    // and replace project references (e.g. "core") with the correct
    // Maven dependency coordinates ("android-test-core")
    tasks.withType<GenerateModuleMetadata> {
        val junit = SupportedJUnit.values()
            .firstOrNull { name.contains(it.variant, ignoreCase = true) }

        if (junit != null) {
            val modifier = ReflectiveModuleMetadataModifier(this, junit)

            doFirst {
                modifier.run()
            }
        }
    }
}

private fun Project.configurePluginDeployment(
    deployConfig: Deployed,
    credentials: DeployedCredentials
) {
    configureCommonDeployment(deployConfig, credentials)

    // Create artifact tasks
    val sourcesJar = tasks.register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")

        // This declaration includes Kotlin & Groovy source directories
        from(sourceSets.main.allJava.srcDirs)
    }

    // Create javadoc artifact
    val javadocJar = tasks.register<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")

        // Connect to Dokka for generation of docs
        from(layout.buildDirectory.dir("dokka/html"))
        dependsOn("dokkaGenerate")
    }

    artifacts {
        add("archives", sourcesJar)
        add("archives", javadocJar)
    }

    // Connect signing task to the JAR produced by the artifact-producing task
    tasks.withType<Sign>().configureEach {
        dependsOn("assemble")
    }

    publishing {
        publications {
            all {
                if (this is MavenPublication) {
                    if (name == "pluginMaven") {
                        // Attach artifacts
                        artifacts.clear()
                        artifact(layout.buildDirectory.file("libs/${project.name}-$version.jar"))
                        artifact(sourcesJar)
                        artifact(javadocJar)

                        applyPublicationDetails(
                            project = this@configurePluginDeployment,
                            deployConfig = deployConfig
                        )
                    }

                    // Always extend POM details to satisfy Maven Central's POM validation
                    // (they require a bunch of metadata that isn't filled out by default)
                    configurePom(deployConfig)
                }
            }
        }
    }
}

private fun MavenPublication.applyPublicationDetails(
    project: Project,
    deployConfig: Deployed,
    junit: SupportedJUnit? = null
) = also {
    groupId = deployConfig.groupId
    artifactId = suffixedArtifactId(deployConfig.artifactId, junit)
    version = deployConfig.currentVersion

    // Attach dependency information, rewriting it to work around certain inadequacies
    // with the built-in maven publish POM generation
    pom {
        withXml {
            with(asNode()) {
                // Replace an existing node or just append a new one.
                val dependenciesNode = replaceNode("dependencies")

                val compileDeps = project.configurations.getByName("api").allDependencies
                val runtimeDeps =
                    project.configurations.getByName("implementation").allDependencies +
                            project.configurations.getByName("runtimeOnly").allDependencies -
                            compileDeps

                val dependencies = mapOf(
                    "runtime" to runtimeDeps,
                    "compile" to compileDeps
                )

                dependencies
                    .mapValues { entry -> entry.value.filter { it.name != "unspecified" } }
                    .forEach { (scope, dependencies) ->
                        dependencies.forEach { dep ->
                            // Do not allow BOM dependencies for our own packaged libraries,
                            // instead its artifact versions should be unrolled explicitly
                            require("-bom" !in dep.name) {
                                "Found a BOM declaration in the dependencies of project" +
                                        "${project.path}: $dep. Prefer declaring its " +
                                        "transitive artifacts explicitly by " +
                                        "adding a version constraint to them."
                            }

                            with(dependenciesNode.appendNode("dependency")) {
                                if (dep is ProjectDependency) {
                                    appendProjectDependencyCoordinates(dep, junit)
                                } else {
                                    appendExternalDependencyCoordinates(dep)
                                }

                                appendNode("scope", scope)
                            }
                        }
                    }
            }
        }
    }
}

private fun Node.appendProjectDependencyCoordinates(
    dep: ProjectDependency,
    junit: SupportedJUnit?
) {
    // Find the external coordinates for the given project dependency
    val projectName = dep.name

    val config = Artifacts.Instrumentation::class.java
        .getMethod("get${projectName.uppercaseFirstChar()}")
        .invoke(Artifacts.Instrumentation)
            as Deployed

    appendNode("groupId", config.groupId)
    appendNode("artifactId", suffixedArtifactId(config.artifactId, junit))
    appendNode("version", config.currentVersion)
}

private fun suffixedArtifactId(base: String, junit: SupportedJUnit? = null) = buildString {
    append(base)

    // Attach optional suffix to Android artifacts
    // to distinguish between different JUnit targets
    junit?.artifactIdSuffix?.let {
        append('-')
        append(it)
    }
}

private fun Node.findNode(name: String): Node? =
    // This method uses "endsWith()" to ignore XMLNS prefixes
    children().firstOrNull { it is Node && it.name().toString().endsWith(name) } as? Node

private fun Node.replaceNode(name: String): Node {
    findNode(name)?.let { remove(it) }
    return appendNode(name)
}

private fun Node.appendExternalDependencyCoordinates(dep: Dependency) {
    appendNode("groupId", dep.group)
    appendNode("artifactId", dep.name)
    dep.version?.let { appendNode("version", it) }
}

private fun MavenPublication.configurePom(deployConfig: Deployed) = also {
    pom {
        // Name and description cannot be set directly through the property, since they somehow aren't applied
        // to Gradle Plugin Marker's POM file (maybe that plugin removes them somehow). Therefore,
        // use the XML builder for this node as these properties are still required by Maven Central
        withXml {
            with(asNode()) {
                appendNode("name").setValue(deployConfig.artifactId)
                appendNode("description").setValue(deployConfig.description)
            }
        }

        url.set(Artifacts.GITHUB_URL)

        licenses {
            license {
                name.set(Artifacts.LICENSE)
                url.set("${Artifacts.GITHUB_URL}/blob/main/LICENSE")
            }
        }

        developers {
            developer {
                id.set("mannodermaus")
                name.set("Marcel Schnelle")
            }
        }

        scm {
            connection.set("scm:git:${Artifacts.GITHUB_REPO}.git")
            developerConnection.set("scm:git:ssh://github.com/${Artifacts.GITHUB_REPO}.git")
            url.set("${Artifacts.GITHUB_URL}/tree/main")
        }
    }
}

/*
 * Type-safe accessor hacks for Kotlin's strictness, accessing properties without having
 * access to these external plugins. This is ridiculously ugly, so readers beware.
 */

// Properties

private val Project.ext: ExtraPropertiesExtension
    get() = extensions.getByName("ext") as ExtraPropertiesExtension

/**
 * Allows us to access certain APIs without access to the actual plugins. Brittle, but works.
 */
private class AndroidDsl(project: Project) {
    private val delegate = project.extensions.getByName("android") as ExtensionAware

    val publishing = PublishingDsl(delegate)

    class PublishingDsl(android: ExtensionAware) {
        private val delegate = android.javaClass.getDeclaredMethod("getPublishing")
            .also { it.isAccessible = true }
            .invoke(android)

        fun singleVariant(name: String, block: SingleVariantDsl.() -> Unit = {}) {
            delegate.javaClass
                .getDeclaredMethod("singleVariant", String::class.java, Closure::class.java)
                .also { it.isAccessible = true }
                .invoke(delegate, name, closureOf<Any> {
                    SingleVariantDsl(this).block()
                })
        }

        class SingleVariantDsl(private val delegate: Any) {
            fun withSourcesJar() {
                delegate.javaClass.declaredMethods
                    .first { it.name.startsWith("withSourcesJar") }
                    .also { it.isAccessible = true }
                    .invoke(delegate, true)
            }

            fun withJavadocJar() {
                delegate.javaClass.declaredMethods
                    .first { it.name.startsWith("withJavadocJar") }
                    .also { it.isAccessible = true }
                    .invoke(delegate, true)
            }
        }
    }
}

private class SourceSetDsl(project: Project) {
    private val delegate = project.extensions.getByName("sourceSets") as SourceSetContainer

    val main: SourceSet = delegate.named("main").get()
}

private val Project.sourceSets
    get() = SourceSetDsl(this)

// Publishing Plugin facade

private fun Project.publishing(action: PublishingExtension.() -> Unit) {
    extensions.configure("publishing", action)
}

private val Project.publishing: PublishingExtension
    get() = extensions.getByName("publishing") as PublishingExtension

// Signing Plugin facade

private fun Project.signing(action: SigningExtension.() -> Unit) {
    extensions.configure("signing", action)
}

// Nexus Staging & Publishing Plugins facade

private fun Project.centralPublishing(
    packageGroup: String,
    stagingProfileId: String?,
    username: String?,
    password: String?
) {
    extensions.getByName("nexusPublishing").withGroovyBuilder {
        setProperty("packageGroup", packageGroup)

        "repositories" {
            "sonatype" {
                // 🤮
                val cls = delegate.javaClass

                cls.getDeclaredMethod("setStagingProfileId", Any::class.java)
                    .also { it.isAccessible = true }
                    .invoke(delegate, stagingProfileId)

                cls.getDeclaredMethod("setUsername", Any::class.java)
                    .also { it.isAccessible = true }
                    .invoke(delegate, username)

                cls.getDeclaredMethod("setPassword", Any::class.java)
                    .also { it.isAccessible = true }
                    .invoke(delegate, password)

                cls.getDeclaredMethod("setNexusUrl", Any::class.java)
                    .also { it.isAccessible = true }
                    .invoke(
                        delegate,
                        uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
                    )

                cls.getDeclaredMethod("setSnapshotRepositoryUrl", Any::class.java)
                    .also { it.isAccessible = true }
                    .invoke(
                        delegate,
                        uri("https://central.sonatype.com/repository/maven-snapshots/")
                    )
            }
        }
    }
}

/**
 * Gradle module metadata modifier, replacing references to instrumentation libraries
 * with the correct artifact ID in each module's JSON file (build/publications/.../module.json).
 */
private class ReflectiveModuleMetadataModifier(
    private val task: GenerateModuleMetadata,
    private val junit: SupportedJUnit
) {
    @Suppress("UNCHECKED_CAST")
    private companion object {
        private val reflectiveMethodCache = mutableMapOf<Class<*>, MutableMap<String, Method>>()
        private val reflectiveFieldCache = mutableMapOf<Class<*>, MutableMap<String, Field>>()

        fun <R : Any> method(cls: Class<*>, named: String, receiver: Any, vararg args: Any): R {
            val methods = reflectiveMethodCache.getOrPut(cls, ::mutableMapOf)
            val method = methods.getOrPut(named) {
                cls.getDeclaredMethod(named).also { it.isAccessible = true }
            }
            return method.invoke(receiver, *args) as R
        }

        fun field(cls: Class<*>, named: String): Field {
            val fields = reflectiveFieldCache.getOrPut(cls, ::mutableMapOf)
            val field = fields.getOrPut(named) {
                cls.getDeclaredField(named).also { it.isAccessible = true }
            }
            return field
        }

        fun <R : Any> Any.field(named: String): R = field(this.javaClass, named).get(this) as R
    }

    fun run() {
        // Access the inputs of the task, then crawl through to the dependencies
        // of each variant inside its module metadata. Rewrite the coordinates of each
        // instrumentation lib found inside there.
        val inputState = method<Any>(GenerateModuleMetadata::class.java, "inputState", task)
        val metadataSpecTry = inputState.field<Try<ModuleMetadataSpec>>("moduleMetadataSpec")
        val metadataSpec = metadataSpecTry.get()
        val variants = metadataSpec.field<List<Any>>("variants")

        for (variant in variants) {
            val variantDependencies =
                runCatching { variant.field<List<Any>>("dependencies") }
                    .getOrNull()
                    ?: continue

            for (variantDependency in variantDependencies) {
                val depCoordinates = variantDependency.field<Any>("coordinates")
                val depGroup = depCoordinates.field<String>("group")
                val depName = depCoordinates.field<String>("name")

                if (depGroup == Artifacts.Instrumentation.groupId) {
                    Artifacts.from(depName)?.let { replacement ->
                        field(depCoordinates.javaClass, "name").set(
                            depCoordinates,
                            suffixedArtifactId(replacement.artifactId, junit)
                        )
                    }
                }
            }
        }
    }
}
