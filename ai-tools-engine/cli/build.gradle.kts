plugins {
    alias(libs.plugins.cleanship.kotlin.library)
    application
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":utils"))
    implementation(project(":telemetry"))
    implementation(libs.clikt)
}

application {
    mainClass = "cz.cleanship.aitools.cli.AiToolsCliKt"
}
