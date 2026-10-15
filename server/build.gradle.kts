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
}

tasks.named<JavaExec>("run") {
    // Keep the database next to the project, not inside server/.
    workingDir = rootProject.projectDir
}
