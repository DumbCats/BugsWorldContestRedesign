// HTTP + WebSocket backend that wraps the engine.
plugins {
    application
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.javalin)
    implementation(libs.jackson.databind)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// A tiny entry point compiled for Java 8, so an outdated Java gets a clear
// "please install Java 21" message instead of a class-version crash.
val launcher by sourceSets.creating
tasks.named<JavaCompile>("compileLauncherJava") {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options") // "release 8 is obsolete" is expected
}
tasks.jar { from(launcher.output) }
dependencies { testImplementation(launcher.output) }

application {
    mainClass = "edu.osu.bugsworld.server.Launcher"
}

tasks.named<JavaExec>("run") {
    mainClass = "edu.osu.bugsworld.server.Main" // from source, Java 21 is a given
    workingDir = rootProject.projectDir
}

// The example bugs from samples/ go into the jar (with an index, since a jar
// can't be listed) for the dashboard's "Add example programs" button.
val exampleResources = layout.buildDirectory.dir("generated/example-resources")
val bundleExamples by tasks.registering(Sync::class) {
    from(rootProject.file("samples")) { include("*.bl") }
    into(exampleResources.map { it.dir("examples") })
    doLast {
        val names = destinationDir.list()!!.filter { it.endsWith(".bl") }.sorted()
        destinationDir.resolve("index.txt").writeText(names.joinToString("\n") + "\n")
    }
}
sourceSets.main { resources.srcDir(exampleResources) }
tasks.processResources { dependsOn(bundleExamples) }

// The download: bin/, lib/, plus src/dist (START-HERE.txt and double-click
// starters) and an examples/ folder of .bl files to try uploading.
distributions {
    main {
        contents {
            from(rootProject.file("samples")) {
                include("*.bl")
                into("examples")
            }
            filesMatching("*.command") { permissions { unix("rwxr-xr-x") } }
        }
    }
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
