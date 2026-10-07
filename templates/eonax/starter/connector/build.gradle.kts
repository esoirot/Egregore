// Shared settings: Java 21 everywhere, and one runnable jar (build/libs/runtime.jar) per runtime.
plugins {
    alias(libs.plugins.shadow) apply false
}

val runtimes = setOf("controlplane", "dataplane", "identityhub", "issuerservice")

subprojects {
    apply(plugin = "java")
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
    }
}

configure(subprojects.filter { it.name in runtimes }) {
    apply(plugin = "application")
    apply(plugin = "com.gradleup.shadow")

    extensions.configure<JavaApplication> {
        // EDC's runtime: loads every extension found on the classpath.
        mainClass = "org.eclipse.edc.boot.system.runtime.BaseRuntime"
    }

    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        // Extensions register through META-INF/services: merge those files across jars
        // (INCLUDE keeps every copy for the merge; the default drops them before it).
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
        archiveFileName = "runtime.jar"
    }
}
