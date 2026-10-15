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

application {
    mainClass = "edu.osu.bugsworld.server.Main"
}
