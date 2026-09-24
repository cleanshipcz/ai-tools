plugins {
    alias(libs.plugins.cleanship.kotlin.library)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.kaml)
    // A conformant TOML 1.0 parser, used only to verify every edit of a Codex config.toml before it is written; the edit itself stays textual so that comments and layout survive. It adds antlr4-runtime and checker-qual at runtime.
    implementation(libs.tomlj)

    implementation(project(":telemetry"))

    testImplementation(kotlin("test"))
}
