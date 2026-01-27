package cz.cleanship.telemetry.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import com.fasterxml.jackson.core.JsonGenerator
import io.opentelemetry.api.trace.Span
import net.logstash.logback.composite.AbstractJsonProvider

/**
 * Logstash provider that writes trace context and span attributes from the current OpenTelemetry context.
 *
 * Writes `trace_id`, `span_id`, and all accumulated span attributes from active spans.
 * Attributes from nested spans are merged, with the deepest/latest definition taking priority.
 *
 * Use with logstash-logback-encoder when provider-based JSON enrichment is preferred.
 */
class TraceJsonProvider : AbstractJsonProvider<ILoggingEvent>() {
    /**
     * Writes trace context (`trace_id`, `span_id`) and accumulated span attributes.
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

        val attributes = SpanAttributesContext.current()
        for ((key, value) in attributes) {
            writeAttribute(generator, key, value)
        }
    }

    private fun writeAttribute(generator: JsonGenerator, key: String, value: Any?) {
        when (value) {
            null -> Unit
            is String -> generator.writeStringField(key, value)
            is Boolean -> generator.writeBooleanField(key, value)
            is Int -> generator.writeNumberField(key, value)
            is Long -> generator.writeNumberField(key, value)
            is Double -> generator.writeNumberField(key, value)
            is Float -> generator.writeNumberField(key, value)
            else -> generator.writeStringField(key, value.toString())
        }
    }
}
