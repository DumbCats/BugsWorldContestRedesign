// HTTP + WebSocket backend that wraps the engine.
plugins {
    application
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.javalin)
    implementation(libs.jackson.databind)
    implementation(libs.sqlite.jdbc)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass = "edu.osu.bugsworld.server.Main"
    // SQLite loads a native library; allow it so Java doesn't print warnings.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// Name the distribution "bugsworld": ./gradlew :server:distZip produces
// server/build/distributions/bugsworld.zip with bin/bugsworld(.bat).
application { applicationName = "bugsworld" }
tasks.named<Zip>("distZip") { archiveFileName = "bugsworld.zip" }
tasks.named<Tar>("distTar") { enabled = false }

// Browser JavaScript tests (server/src/test/js), run with Node when it is
// installed. Node is only a development convenience: building and running
// BugsWorld needs Java alone, so without Node this is skipped.
fun hasNode(): Boolean = System.getenv("PATH").orEmpty().split(File.pathSeparator)
    .any { File(it, "node").canExecute() || File(it, "node.exe").canExecute() }

val webTest by tasks.registering(Exec::class) {
    description = "Runs the browser JavaScript unit tests with Node, if available."
    group = "verification"
    val nodeAvailable = hasNode()
    onlyIf("Node.js is installed") { nodeAvailable }
    workingDir = projectDir
    commandLine("node", "--test", "src/test/js/*.test.mjs")
    inputs.dir("src/test/js")
    inputs.dir("src/main/resources/public/js")
    outputs.upToDateWhen { true }
}
tasks.check { dependsOn(webTest) }
