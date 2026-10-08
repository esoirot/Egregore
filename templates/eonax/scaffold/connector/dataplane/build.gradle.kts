// Data plane: moves the data (HTTP pull and push); registers itself with its control plane.
dependencies {
    runtimeOnly(project(":jetty")) // keeps a path from answering 404 forever after a start
    // Our own code (src/main/java): the public API consumers pull from.
    implementation(libs.edc.data.plane.spi)
    implementation(libs.edc.web.spi)

    runtimeOnly(libs.edc.bom.dataplane)
    runtimeOnly(libs.edc.bom.dataplane.sql) // state in Postgres
    runtimeOnly(libs.edc.vault.hashicorp) // secrets: the keys signing the access tokens it hands out
    runtimeOnly(libs.edc.participant.context.config) // per-participant settings the vault needs (the control plane BOM has it)
}
