plugins {
    // Lets Gradle download the JDK toolchain on machines that don't have it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "bugsworld-web-lab"

include("engine", "server")
