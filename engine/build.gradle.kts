// Compiler, VM, and simulation. Pure Java with no runtime dependencies so it
// can be unit tested in isolation and reused by any front end.
plugins {
    `java-library`
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    // ReferenceOracleTest compares our compiler with the OSU one in lib/
    // (gitignored). Without the jar the test is skipped.
    val referenceJar = rootProject.file("lib/BWClient.jar")
    systemProperty("bugsworld.referenceJar", referenceJar.absolutePath)
    inputs.property("referenceJarPresent", referenceJar.exists())
    // e.g. ./gradlew :engine:test -Dbugsworld.fuzzCases=50000 -Dbugsworld.fuzzSeed=7
    for (key in listOf("bugsworld.fuzzCases", "bugsworld.fuzzSeed")) {
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
}
