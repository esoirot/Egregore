// Data plane: moves the data (HTTP pull and push); registers itself with its control plane.
dependencies {
    runtimeOnly(libs.edc.bom.dataplane)
    runtimeOnly(libs.edc.bom.dataplane.sql) // state in Postgres
}
