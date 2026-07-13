package cz.cleanship.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for Kotlin JVM library projects that includes telemetry support.
 * This plugin applies the base KotlinJvmConventionPlugin and adds the telemetry module as a dependency.
 */
class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply the base convention plugin
            apply<KotlinJvmConventionPlugin>()

            // Add telemetry as a dependency (using api to expose transitive dependencies)
            dependencies {
                add("api", project(":telemetry"))
            }
        }
    }
}
