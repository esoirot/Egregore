// Control plane: catalog, contract negotiation, transfer process, management API, Dataspace Protocol.
dependencies {
    runtimeOnly(libs.edc.bom.controlplane) {
        // The BOM also ships EDC's new data plane signaling; this data plane speaks the classic one
        // (transfer-data-plane-signaling below). Same pairing as EDC's 0.18.1 transfer samples.
        exclude(group = "org.eclipse.edc", module = "data-plane-signaling-core")
        exclude(group = "org.eclipse.edc", module = "data-plane-signaling-oauth2")
    }
    runtimeOnly(libs.edc.bom.controlplane.sql) // state in Postgres
    runtimeOnly(libs.edc.transfer.data.plane.signaling) // drives the data plane below
    runtimeOnly(libs.edc.vault.hashicorp) // secrets: received access tokens (EDR), keys
    // Identity: accepts every participant for now. Replaced by credentials (DCP) in the trust chapter.
    runtimeOnly(libs.edc.iam.mock)
}
