plugins {
    // Apply the shared build logic from a convention plugin (includes telemetry).
    alias(libs.plugins.cleanship.kotlin.library)
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
}
