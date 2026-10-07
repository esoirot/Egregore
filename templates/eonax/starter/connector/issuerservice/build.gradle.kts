// Issuer Service: the data space authority. Registers members (holders) and issues them
// a MembershipCredential when they ask (DCP issuance).
dependencies {
    runtimeOnly(project(":superuser")) // the super-user account of its APIs
    implementation(libs.edc.issuance.spi) // our code: the "membership" attestation (src/main/java)
    runtimeOnly(libs.edc.bom.issuerservice)
    runtimeOnly(libs.edc.bom.issuerservice.sql) // state in Postgres
    runtimeOnly(libs.edc.did.api)
    runtimeOnly(libs.edc.participant.context.api)
    runtimeOnly(libs.edc.vault.hashicorp)
    runtimeOnly(libs.edc.participant.context.config)
    runtimeOnly(libs.edc.participant.context.config.store.sql)
}
