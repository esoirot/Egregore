// Identity Hub: one per participant. Publishes its did:web document, keeps its verifiable
// credentials (the wallet), presents them on request (DCP), and issues its connector's tokens (STS).
dependencies {
    runtimeOnly(project(":jetty")) // keeps a path from answering 404 forever after a start
    runtimeOnly(project(":superuser")) // the super-user account of its Identity API
    runtimeOnly(libs.edc.bom.identityhub)
    runtimeOnly(libs.edc.bom.identityhub.sql) // state in Postgres
    runtimeOnly(libs.edc.vault.hashicorp) // keys and the connector's STS client secret
}
