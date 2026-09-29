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

// The MCP launcher is a shell script kept outside this Gradle project, in scripts/ of the repository. Its tests (McpLaunchScriptTest) find it through this system property, and declaring it as an input makes Gradle rerun them when only the script changes. inputs.files, unlike inputs.file, tolerates a missing file, so a missing launcher fails the tests rather than the build configuration.
val mcpLaunchScript = rootProject.projectDir.parentFile.resolve("scripts/mcp-launch")
tasks.named<Test>("test") {
    inputs.files(mcpLaunchScript).withPropertyName("mcpLaunchScript").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("aitools.mcpLaunch", mcpLaunchScript.absolutePath)
}
