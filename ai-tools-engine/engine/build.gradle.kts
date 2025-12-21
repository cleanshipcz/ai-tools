plugins {
    alias(libs.plugins.cleanship.kotlin.library)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.kaml)

    implementation(project(":telemetry"))
    implementation(project(":utils"))

    testImplementation(kotlin("test"))
}
