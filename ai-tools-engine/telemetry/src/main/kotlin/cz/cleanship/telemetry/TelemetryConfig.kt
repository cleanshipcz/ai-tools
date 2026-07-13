package cz.cleanship.telemetry

/**
 * Configuration for the telemetry module.
 *
 * Values can be constructed from environment variables and/or system properties
 * using [fromEnvironment].
 *
 * @property serviceName logical service name reported to backends
 * @property tracesExporters selected trace exporters (multiple allowed)
 * @property metricsExporters selected metrics exporters (multiple allowed)
 * @property otlpEndpoint OTLP endpoint URL when OTLP exporters are used
 */
data class TelemetryConfig(
    val serviceName: String = System.getProperty("telemetry.service.name")
        ?: System.getenv("TELEMETRY_SERVICE_NAME")
        ?: "service-name-placeholder",
    val tracesExporters: Set<TracesExporter> = TracesExporter.fromList(
        System.getProperty("telemetry.traces.exporter") ?: System.getenv("TELEMETRY_EXPORTER_TRACES"),
    ),
    val metricsExporters: Set<MetricsExporter> = MetricsExporter.fromList(
        System.getProperty("telemetry.metrics.exporter") ?: System.getenv("TELEMETRY_EXPORTER_METRICS"),
    ),
    val otlpEndpoint: String? = System.getProperty("telemetry.otlp.endpoint")
        ?: System.getenv("TELEMETRY_OTLP_ENDPOINT"),
) {
    companion object {
        /**
         * Creates configuration from environment variables and system properties.
         *
         * @return a configuration instance
         */
        fun fromEnvironment(): TelemetryConfig = TelemetryConfig()
    }
}

/** Trace exporter selection. */
enum class TracesExporter(val id: String) {
    NONE("none"),
    LOGGING("logging"),
    OTLP("otlp"),
    INMEMORY_FOR_TESTS("inmemory"),
    ;

    companion object {
        /**
         * Parses a [value] to a [TracesExporter].
         *
         * @param value exporter id (case-insensitive)
         * @return the matching exporter or [NONE] if unmatched or null
         */
        fun from(value: String?): TracesExporter = when (value?.lowercase()) {
            LOGGING.id -> LOGGING
            OTLP.id -> OTLP
            INMEMORY_FOR_TESTS.id -> INMEMORY_FOR_TESTS
            else -> NONE
        }

        /**
         * Parses a comma-separated list of exporter ids into a set.
         *
         * @param value comma-separated exporter ids (case-insensitive)
         * @return set of exporters; empty if none
         */
        fun fromList(value: String?): Set<TracesExporter> = value
            ?.split(',')
            ?.mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() } }
            ?.map { from(it) }
            ?.filter { it != NONE }
            ?.toSet()
            ?: emptySet()
    }
}

/** Metrics exporter selection. */
enum class MetricsExporter(val id: String) {
    NONE("none"),
    PROMETHEUS("prometheus"),
    OTLP("otlp"),
    LOGGING("logging"),
    ;

    companion object {
        /**
         * Parses a [value] to a [MetricsExporter].
         *
         * @param value exporter id (case-insensitive)
         * @return the matching exporter or [NONE] if unmatched or null
         */
        fun from(value: String?): MetricsExporter = when (value?.lowercase()) {
            PROMETHEUS.id -> PROMETHEUS
            OTLP.id -> OTLP
            LOGGING.id -> LOGGING
            else -> NONE
        }

        /**
         * Parses a comma-separated list of exporter ids into a set.
         *
         * @param value comma-separated exporter ids (case-insensitive)
         * @return set of exporters; empty if none
         */
        fun fromList(value: String?): Set<MetricsExporter> = value
            ?.split(',')
            ?.mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() } }
            ?.map { from(it) }
            ?.filter { it != NONE }
            ?.toSet()
            ?: emptySet()
    }
}
