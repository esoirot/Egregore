// Control plane: catalog, contract negotiation, transfer process, management API, Dataspace Protocol.
dependencies {
    runtimeOnly(project(":jetty")) // keeps a path from answering 404 forever after a start
    // Our code (src/main/java): the member directory of the federated catalog crawler.
    implementation(libs.edc.crawler.spi)
    implementation(libs.edc.identity.did.spi)

    // DCP bundle: the base control plane plus identity through verifiable credentials
    // (it asks its Identity Hub for tokens and checks the other side's credentials).
    runtimeOnly(libs.edc.bom.controlplane.dcp) {
        // The BOM also ships EDC's new data plane signaling; this data plane speaks the classic one
        // (transfer-data-plane-signaling below). Same pairing as EDC's 0.18.1 transfer samples.
        exclude(group = "org.eclipse.edc", module = "data-plane-signaling-core")
        exclude(group = "org.eclipse.edc", module = "data-plane-signaling-oauth2")
    }
    runtimeOnly(libs.edc.bom.controlplane.sql) { // state in Postgres
        exclude(group = "org.eclipse.edc", module = "target-node-directory-sql") // ours resolves member DIDs instead
    }
    runtimeOnly(libs.edc.transfer.data.plane.signaling) // drives the data plane below
    runtimeOnly(libs.edc.vault.hashicorp) // secrets: received access tokens (EDR), keys
    // Policy rules as CEL expressions over the other side's credentials (management API /v5beta/celexpressions).
    runtimeOnly(libs.edc.cel.api)
    runtimeOnly(libs.edc.cel.core)
    runtimeOnly(libs.edc.cel.dcp)
    runtimeOnly(libs.edc.cel.store.sql)
}
