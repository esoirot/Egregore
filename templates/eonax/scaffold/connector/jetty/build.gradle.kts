// Shared by every runtime: keeps one path from answering 404 forever after a start (JettyChainCacheExtension).
plugins {
    `java-library`
}

dependencies {
    compileOnly(libs.edc.jetty.core) // every runtime has it (its web server)
    compileOnly(libs.jetty.servlet)
    testImplementation(libs.edc.jetty.core)
    testImplementation(libs.jetty.servlet)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
