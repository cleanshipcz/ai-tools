plugins {
    alias(libs.plugins.cleanship.kotlin.library)
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":utils"))
    implementation(project(":telemetry"))

    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)

    implementation(libs.bundles.kotlinxEcosystem)
}

application {
    mainClass = "cz.cleanship.aitools.server.ServerAppKt"
}
