// Control plane: catalog, contract negotiation, transfer process, management API, Dataspace Protocol.
dependencies {
    runtimeOnly(libs.edc.bom.controlplane)
    runtimeOnly(libs.edc.bom.controlplane.sql) // state in Postgres
    runtimeOnly(libs.edc.transfer.data.plane.signaling) // drives the data plane below
    // Identity: accepts every participant for now. Replaced by credentials (DCP) in the trust chapter.
    runtimeOnly(libs.edc.iam.mock)
}
