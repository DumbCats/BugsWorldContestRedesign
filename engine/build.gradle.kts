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
    // The jar's game classes build Swing panels; headless mode lets them load.
    systemProperty("java.awt.headless", "true")
    systemProperty("bugsworld.samplesDir", rootProject.file("samples").absolutePath)
    inputs.dir(rootProject.file("samples"))
    // e.g. ./gradlew :engine:test -Dbugsworld.fuzzCases=50000 -Dbugsworld.fuzzSeed=7
    for (key in listOf("bugsworld.fuzzCases", "bugsworld.fuzzSeed", "bugsworld.referenceGames")) {
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
}

// ./gradlew -q :engine:simulate --args="samples/hunter.bl samples/wanderer.bl --show 100"
tasks.register<JavaExec>("simulate") {
    group = "application"
    description = "Runs BugsWorld games in the terminal (see Simulate.java for options)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "edu.osu.bugsworld.engine.Simulate"
    workingDir = rootProject.projectDir
}
