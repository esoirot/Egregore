rootProject.name = "connector"

// One runtime per data space component; each becomes its own image (see Dockerfile).
include("controlplane", "dataplane")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
