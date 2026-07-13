plugins {
    alias(libs.plugins.cleanship.kotlin.convention)
}

dependencies {
    // Core - exposed as API so consumers can use OpenTelemetry
    api(libs.opentelemetryApi)
    implementation(libs.opentelemetrySdk)
    implementation(libs.opentelemetryExporterOtlp)
    implementation(libs.opentelemetryExporterLogging)
    implementation(libs.opentelemetryContext)
    implementation(libs.opentelemetryExtensionKotlin)

    // Metrics - exposed as API so consumers can use Micrometer
    api(libs.micrometerCore)
    implementation(libs.micrometerRegistryPrometheus)
    implementation(libs.micrometerRegistryOtlp)

    // Logging - exposed as API so consumers can use SLF4J/Logback
    api(libs.logbackClassic)
    api(libs.logstashLogbackEncoder)

    // Coroutines
    implementation(libs.kotlinxCoroutines)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(libs.opentelemetrySdkTesting)
    testImplementation(libs.kotlinxCoroutinesTest)
}
