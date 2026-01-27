package cz.cleanship.telemetry.logging

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey

/**
 * OpenTelemetry context key for storing accumulated span attributes.
 *
 * Attributes from nested spans are merged, with the deepest/latest definition taking priority.
 * This enables automatic inclusion of span attributes in log output via [TraceJsonProvider].
 */
object SpanAttributesContext {

    private val ATTRIBUTES_KEY: ContextKey<Map<String, Any?>> =
        ContextKey.named("cz.cleanship.telemetry.span_attributes")

    /**
     * Retrieves the current accumulated attributes from the OpenTelemetry context.
     *
     * @return merged attribute map from all active spans, or empty map if none
     */
    fun current(): Map<String, Any?> = Context.current().get(ATTRIBUTES_KEY) ?: emptyMap()

    /**
     * Creates a new context with merged attributes.
     *
     * New attributes are merged with existing ones, with new values taking priority for duplicate keys.
     *
     * @param parentContext the parent OpenTelemetry context
     * @param newAttributes attributes to add/override
     * @return new context with merged attributes
     */
    fun withAttributes(parentContext: Context, newAttributes: Map<String, Any?>): Context {
        val existing = parentContext.get(ATTRIBUTES_KEY) ?: emptyMap()
        val merged = existing + newAttributes
        return parentContext.with(ATTRIBUTES_KEY, merged)
    }
}
