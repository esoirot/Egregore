// Gaia-X compliance client: talks to whatever GXDCH the GAIAX_* settings name (dataspace/gaiax sets them).
plugins {
    application
}

dependencies {
    implementation(libs.nimbus.jose.jwt) // JWS signing (VC-JWT, VP-JWT)
    implementation(libs.bouncycastle.pkix) // reads the signing key from PEM
}

application {
    mainClass = "org.eonax.gaiax.GaiaxCompliance"
}

tasks.named<JavaExec>("run") {
    jvmArgs("-Dstdout.encoding=UTF-8") // its ✓ marks
}
