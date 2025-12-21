package cz.cleanship.telemetry.support

import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LocalInMemorySpanExporterTest {

    @Test
    fun `export stores spans and reset clears them`() {
        // given
        val exporter = LocalInMemorySpanExporter()
        val empty: MutableCollection<SpanData> = mutableListOf()
        assertThat(exporter.export(empty).isSuccess).isTrue()
        assertThat(exporter.finishedSpanItems).isEmpty()

        // when
        // - build a minimal valid finished span
        val span = TestSpanData
            .builder()
            .setName("test")
            .setKind(SpanKind.INTERNAL)
            .setSpanContext(
                SpanContext.create(
                    "0123456789abcdef0123456789abcdef",
                    "0123456789abcdef",
                    TraceFlags.getSampled(),
                    TraceState.getDefault(),
                ),
            ).setParentSpanContext(SpanContext.getInvalid())
            .setStartEpochNanos(1L)
            .setEndEpochNanos(2L)
            .setStatus(StatusData.unset())
            .setHasEnded(true)
            .build()

        // then
        val batch: MutableCollection<SpanData> = mutableListOf(span)
        assertThat(exporter.export(batch).isSuccess).isTrue()
        assertThat(exporter.finishedSpanItems).hasSize(1)
        assertThat(exporter.finishedSpanItems.first().name).isEqualTo("test")

        // - reset clears storage
        exporter.reset()
        assertThat(exporter.finishedSpanItems).isEmpty()
    }
}
