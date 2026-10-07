rootProject.name = "connector"

// One runtime per data space component; each becomes its own image (see Dockerfile).
include("controlplane", "dataplane", "identityhub", "issuerservice")
// Code shared by runtimes (a library, not an image).
include("superuser")
// Gaia-X compliance client: a command-line program (just gaiax; a Kubernetes Job later).
include("gaiax")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
