plugins {
    alias(libs.plugins.cleanship.kotlin.library)
    application
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":telemetry"))
    implementation(libs.clikt)

    testImplementation(kotlin("test"))
}

application {
    mainClass = "cz.cleanship.aitools.cli.AiToolsCliKt"
}
