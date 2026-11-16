plugins {
    // Auto-provisions the JDK 21 toolchain required by build.gradle.kts when it isn't installed locally
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "certificate-service"
