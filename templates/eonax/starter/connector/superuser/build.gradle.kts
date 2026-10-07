// Shared by the Identity Hubs and the Issuer Service: creates the super-user account their APIs need.
plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.participant.context.spi)
}
