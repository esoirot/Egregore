rootProject.name = "connector"

// One runtime per data space component; each becomes its own image (see Dockerfile).
include("controlplane", "dataplane", "identityhub", "issuerservice")
// Code shared by runtimes (a library, not an image).
include("superuser")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
