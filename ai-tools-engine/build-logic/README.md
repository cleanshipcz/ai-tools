# Shared build logic

## Dependency management

## Custom plugins

To create custom plugins (such as base for a kotlin library, or a kotlin application), create custom plugins

1. Define the plugin in `../gradle/libs.versions.toml`
   ```toml
   # <identifier, dash (-) will be converted to dot (.)> = { id = "<identifier>" }
   # e.g.:
   cleanship-kotlin-convention = { id = "cleanship.kotlin.convention" }
   ```
2. Define a `org.gradle.api.Plugin` class in the `convention/src/main/kotlin/cz/cleanship/plugin` directory
    * see e.g. `build-logic/convention/src/main/kotlin/cz/cleanship/plugin/KotlinJvmConventionPlugin.kt`
3. Register it in the `convention/build.gradle.kts`
   ```kotlin
    register("kotlinJvmLibrary") {                                              // unique identifier
        id = libs.plugins.cleanship.kotlin.convention.get().pluginId            // pre-existing id from the version catalog
        implementationClass = "cz.cleanship.plugin.KotlinJvmConventionPlugin"   // fully qualified class name
    }
   ```
4. Apply the plugin in the `build.gradle.kts` of the target module
   ```kotlin
   plugins {
       alias(libs.plugins.cleanship.kotlin.convention)  // libs.plugins.<identifier>
   }
   ```
