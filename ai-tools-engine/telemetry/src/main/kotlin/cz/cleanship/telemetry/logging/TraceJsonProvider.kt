package cz.cleanship.telemetry.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import com.fasterxml.jackson.core.JsonGenerator
import io.opentelemetry.api.trace.Span
import net.logstash.logback.composite.AbstractJsonProvider

/**
 * Logstash provider that writes `trace_id` and `span_id` from the current OpenTelemetry span.
 *
 * Use with logstash-logback-encoder when provider-based JSON enrichment is preferred.
 */
class TraceJsonProvider : AbstractJsonProvider<ILoggingEvent>() {
    /**
     * Writes `trace_id` and `span_id` if a valid span exists.
     *
     * @param generator the JSON generator
     * @param event the logging event
     */
    override fun writeTo(generator: JsonGenerator, event: ILoggingEvent) {
        val ctx = Span.current().spanContext
        if (ctx.isValid) {
            generator.writeStringField("trace_id", ctx.traceId)
            generator.writeStringField("span_id", ctx.spanId)
        }
    }
}
