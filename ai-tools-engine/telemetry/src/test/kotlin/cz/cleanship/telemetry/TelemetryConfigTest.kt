package cz.cleanship.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class TelemetryConfigTest {

    private companion object {
        private const val SERVICE_NAME_PROP = "telemetry.service.name"
        private const val TRACES_EXPORTER_PROP = "telemetry.traces.exporter"
        private const val METRICS_EXPORTER_PROP = "telemetry.metrics.exporter"
        private const val OTLP_ENDPOINT_PROP = "telemetry.otlp.endpoint"
    }

    private val keys = listOf(
        SERVICE_NAME_PROP,
        TRACES_EXPORTER_PROP,
        METRICS_EXPORTER_PROP,
        OTLP_ENDPOINT_PROP,
    )

    @AfterEach
    fun cleanup() {
        // Clear system properties we might have set to keep tests isolated
        keys.forEach { System.clearProperty(it) }
    }

    @Test
    fun `system properties populate TelemetryConfig fields`() {
        // given
        System.setProperty(SERVICE_NAME_PROP, "svc-A")
        System.setProperty(TRACES_EXPORTER_PROP, "logging,OTLP,inmemory")
        System.setProperty(METRICS_EXPORTER_PROP, "prometheus,logging")
        System.setProperty(OTLP_ENDPOINT_PROP, "http://example:4318")

        // when
        val cfg = TelemetryConfig.fromEnvironment()

        // then
        assertThat(cfg.serviceName).isEqualTo("svc-A")
        assertThat(cfg.tracesExporters)
            .containsExactlyInAnyOrder(TracesExporter.LOGGING, TracesExporter.OTLP, TracesExporter.INMEMORY_FOR_TESTS)
        assertThat(cfg.metricsExporters)
            .containsExactlyInAnyOrder(MetricsExporter.PROMETHEUS, MetricsExporter.LOGGING)
        assertThat(cfg.otlpEndpoint).isEqualTo("http://example:4318")
    }

    @Test
    fun `exporter lists ignore none and unknown tokens`() {
        // given
        System.setProperty(TRACES_EXPORTER_PROP, "none,bad, INMEMORY ")
        System.setProperty(METRICS_EXPORTER_PROP, "NONE, ,otlp,bad")

        // when
        val cfg = TelemetryConfig.fromEnvironment()

        // then
        assertThat(cfg.tracesExporters)
            .containsExactly(TracesExporter.INMEMORY_FOR_TESTS)
        assertThat(cfg.metricsExporters)
            .containsExactly(MetricsExporter.OTLP)
    }

    @Test
    fun `TracesExporter from is case-insensitive and defaults to NONE`() {
        // given/when/then
        assertThat(TracesExporter.from("LoGgInG")).isEqualTo(TracesExporter.LOGGING)
        assertThat(TracesExporter.from("otlp")).isEqualTo(TracesExporter.OTLP)
        assertThat(TracesExporter.from("INMEMORY")).isEqualTo(TracesExporter.INMEMORY_FOR_TESTS)
        assertThat(TracesExporter.from(null)).isEqualTo(TracesExporter.NONE)
        assertThat(TracesExporter.from("unknown")).isEqualTo(TracesExporter.NONE)
    }

    @Test
    fun `TracesExporter fromList parses, trims, deduplicates and filters NONE`() {
        // given
        val input = " logging , otlp , none , , inmemory , LOGGING "

        // when
        val result = TracesExporter.fromList(input)

        // then
        assertThat(result)
            .containsExactlyInAnyOrder(TracesExporter.LOGGING, TracesExporter.OTLP, TracesExporter.INMEMORY_FOR_TESTS)
        assertThat(TracesExporter.fromList(null)).isEmpty()
    }

    @Test
    fun `MetricsExporter from is case-insensitive and defaults to NONE`() {
        // given/when/then
        assertThat(MetricsExporter.from("PrOmEtHeUs")).isEqualTo(MetricsExporter.PROMETHEUS)
        assertThat(MetricsExporter.from("otlp")).isEqualTo(MetricsExporter.OTLP)
        assertThat(MetricsExporter.from("LOGGING")).isEqualTo(MetricsExporter.LOGGING)
        assertThat(MetricsExporter.from(null)).isEqualTo(MetricsExporter.NONE)
        assertThat(MetricsExporter.from("unknown")).isEqualTo(MetricsExporter.NONE)
    }

    @Test
    fun `MetricsExporter fromList parses, trims, deduplicates and filters NONE`() {
        // given
        val input = " prometheus , logging , none , , OTLP , LOGGING "

        // when
        val result = MetricsExporter.fromList(input)

        // then
        assertThat(result)
            .containsExactlyInAnyOrder(MetricsExporter.PROMETHEUS, MetricsExporter.LOGGING, MetricsExporter.OTLP)
        assertThat(MetricsExporter.fromList(null)).isEmpty()
    }
}
