repositories {
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    // The Kotlin DSL plugin provides a convenient way to develop convention plugins.
    // Convention plugins are located in `src/main/kotlin`, with the file extension `.gradle.kts`,
    // and are applied in the project's `build.gradle.kts` files as required.
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Add a dependency on the Kotlin Gradle plugin, so that convention plugins can apply it.
    implementation(libs.kotlinGradlePlugin)
    implementation("org.jetbrains.kotlin:kotlin-serialization:${libs.versions.kotlin.get()}")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:${libs.versions.ktlintGradle.get()}")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("kotlinJvmConvention") {
            id = libs.plugins.cleanship.kotlin.convention.get().pluginId
            implementationClass = "cz.cleanship.plugin.KotlinJvmConventionPlugin"
        }
        register("kotlinLibrary") {
            id = libs.plugins.cleanship.kotlin.library.get().pluginId
            implementationClass = "cz.cleanship.plugin.KotlinLibraryConventionPlugin"
        }
    }
}
