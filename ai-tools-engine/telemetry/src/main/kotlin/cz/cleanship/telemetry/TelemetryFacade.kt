package cz.cleanship.telemetry

import cz.cleanship.telemetry.SpanKind.*

/**
 * Framework-agnostic facade over tracing and metrics.
 * Application code should depend only on this interface, not on vendor SDKs.
 */
interface TelemetryFacade {
    // ---- Metrics ----
    /**
     * Returns a counter-handle used to count occurrences of discrete events (monotonically increasing).
     *
     * @param name stable metric name identifying the time series. Choose a descriptive, low-cardinality
     * name (e.g., "http_server_requests_total"). The name is combined with [tags] to form distinct
     * time series in backends. Must match the regex `[A-Za-z0-9_:]+`.
     * @param tags dimensional labels for the series (e.g., method=GET, status=200). Using the same
     * [name] with different tag sets creates separate series; using the same [name] and identical
     * tag set contributes to the same series. Tag keys must match `[A-Za-z0-9_:]+`; tag values must not
     * contain spaces or double quotes. Be cautious with tag cardinality.
     * @return a handle to increment the counter; call [CounterHandle.increment] when the event occurs.
     * @throws IllegalArgumentException if [name] or any tag key/value violates the format constraints.
     * @see timer
     * @see gauge
     */
    fun counter(
        name: String,
        tags: Map<String, String> = emptyMap(),
    ): CounterHandle

    /**
     * Returns a timer handle used to record durations/latency of operations.
     *
     * @param name stable metric name for latency measurement (e.g., "db_query_latency"). The name is
     * combined with [tags] to identify the series. Must match the regex `[A-Za-z0-9_:]+`.
     * @param tags dimensional labels for breaking down latency (e.g., query=select_user). As with
     * counters, same [name] + equal [tags] aggregate together; different tags produce separate series.
     * Tag keys must match `[A-Za-z0-9_:]+`; tag values must not contain spaces or double quotes.
     * @return a handle to record durations via [TimerHandle.record] or to time suspending work via
     * [TimerHandle.recordSuspend]. Common use cases include measuring HTTP handler latency, DB calls,
     * or RPC round-trips.
     * @throws IllegalArgumentException if [name] or any tag key/value violates the format constraints.
     * @see counter
     * @see gauge
     */
    fun timer(
        name: String,
        tags: Map<String, String> = emptyMap(),
    ): TimerHandle

    /**
     * Returns a gauge handle representing a value at a point in time (last-set-wins).
     *
     * @param name stable metric name for the gauge (e.g., "queue_depth", "cache_size"). The name is
     * combined with [tags] to identify the series. Must match the regex `[A-Za-z0-9_:]+`.
     * @param tags dimensional labels for the gauge series (e.g., queue=payments). Same rules as above
     * apply: same [name] + equal [tags] update the same series.
     * Tag keys must match `[A-Za-z0-9_:]+`; tag values must not contain spaces or double quotes.
     * @return a handle to set gauge values via [GaugeHandle.set]. Typical use cases include queue sizes,
     * pool utilization, and cache metrics.
     * @throws IllegalArgumentException if [name] or any tag key/value violates the format constraints.
     * @see counter
     * @see timer
     */
    fun gauge(
        name: String,
        tags: Map<String, String> = emptyMap(),
    ): GaugeHandle

    // ---- Tracing ----

    /**
     * Starts a span and makes it current until the returned scope is closed.
     *
     * @param name operation name shown in tracing backends (keep stable and descriptive, e.g.,
     * "order.create").
     * @param kind span kind (INTERNAL by default); choose SERVER/CLIENT/PRODUCER/CONSUMER when relevant.
     * @param attributes initial attributes added to the span (low-cardinality keys such as user_id,
     * component, route). Values are recorded with appropriate types.
     * @return a [SpanScope] that must be closed to end the span (use `use`/`try-finally`).
     * @see inSpan
     */
    fun startSpan(
        name: String,
        kind: SpanKind = INTERNAL,
        attributes: Map<String, Any?> = emptyMap(),
    ): SpanScope

    /**
     * Runs [block] within a new span and returns the result of [block]. Exceptions thrown by [block]
     * are recorded on the span and rethrown.
     *
     * @param name operation name for the new span.
     * @param kind span kind.
     * @param attributes initial attributes for the span.
     * @param block suspending work executed within the span; receives the created span for optional
     * enrichment (e.g., add events/attributes).
     * @return the result of [block]; use this when you need the function result in addition to tracing.
     * Typical use cases include wrapping service calls, database operations, or external requests where
     * you want both latency visibility and the computed result.
     */
    suspend fun <T> inSpan(
        name: String,
        kind: SpanKind = INTERNAL,
        attributes: Map<String, Any?> = emptyMap(),
        block: suspend (TelemetrySpan) -> T,
    ): T

    // ---- Exporters / Utilities ----

    /**
     * Returns Prometheus exposition text.
     *
     * @return scrape text if Prometheus is enabled; otherwise null
     */
    fun prometheusScrape(): String?

    /** Shutdown exporters/SDKs gracefully. */
    fun shutdown()
}

// ------------ Handles ------------

/**
 * Monotonically increasing counter-handle bound to a specific metric series.
 *
 * Use cases: requests, retries, errors, cache hits/misses, processed items.
 * Thread-safe; reuse on hot paths.
 */
interface CounterHandle {
    /**
     * Increments the counter.
     *
     * @param amount non-negative increment; 1.0 counts one occurrence
     */
    fun increment(amount: Double = 1.0)
}

/**
 * Timer handle for recording operation latency for a specific series.
 *
 * Use cases: HTTP handler latency, database queries, RPC calls.
 * Thread-safe; reuse handles.
 */
interface TimerHandle {
    /**
     * Records a measured duration.
     *
     * @param durationMillis duration in milliseconds
     */
    fun record(durationMillis: Long)

    /**
     * Times [block], records the duration, and returns its result.
     *
     * Exceptions are recorded and rethrown.
     * @param block suspending work to time
     * @return result of [block]
     */
    suspend fun <T> recordSuspend(block: suspend () -> T): T
}

/**
 * Gauge handle to set the current value of a series (last-set-wins).
 *
 * Use cases: queue depth, pool utilization/size, cache entries.
 * Thread-safe; reuse handles.
 */
fun interface GaugeHandle {
    /**
     * Sets the current gauge value.
     *
     * @param value value to report
     */
    fun set(value: Double)
}

/**
 * Holds an active span bound to the current context.
 *
 * Purpose: represent the lifetime of the current span. Always close the scope to end the span and
 * restore the previous context. Prefer using Kotlin's `use` or `try/finally` to ensure cleanup.
 */
interface SpanScope : AutoCloseable {
    /** The active span. */
    val span: TelemetrySpan

    /** Ends the span and restores the previous context. */
    override fun close()
}

/**
 * Minimal, vendor-agnostic view of the current span.
 *
 * Use cases:
 * - Read stable identifiers for correlation (e.g., add to logs or HTTP responses).
 * - Assert parent/child relationships in tests.
 *
 * Notes:
 * - Valid only while the span scope is active; do not retain beyond the scope.
 * - Control span lifetime and attributes via [TelemetryFacade.inSpan] or [TelemetryFacade.startSpan].
 *
 * @property traceId W3C Trace Context trace-id (32 lowercase hex). Stable across all spans in the same trace; use this for log/HTTP correlation.
 * @property spanId W3C Trace Context span-id (16 lowercase hex) identifying this span; use to link parent/child spans.
 * @see SpanKind
 * @see TelemetryFacade.inSpan
 */
interface TelemetrySpan {
    val traceId: String
    val spanId: String
}

/**
 * Classifies the role of a span in a trace. Choose a kind to indicate whether the work is purely
 * in-process or crosses RPC/HTTP or messaging boundaries. Backends use this to group, visualize,
 * and derive semantics (e.g., request/response timing, dependency graphs).
 *
 * Usage guidelines:
 * - Use [INTERNAL] for in-process work (business logic, CPU-bound operations).
 * - Use [SERVER] and [CLIENT] for request/response over RPC/HTTP and similar protocols.
 * - Use [PRODUCER] and [CONSUMER] for asynchronous messaging (queues, streams, pub/sub).
 */
enum class SpanKind {
    /**
     * Internal application work that does not cross a process, network, or messaging boundary.
     *
     * Typical uses: business logic, in-memory computations, cache lookups, parsing/serialization
     * performed entirely within the current process.
     */
    INTERNAL,

    /**
     * The current code is receiving a request from a remote peer and acts as the server.
     *
     * Typical uses: HTTP request handlers, RPC servers, inbound gRPC endpoints, message
     * processing frameworks that model inbound handling as server-side work.
     */
    SERVER,

    /**
     * The current code is initiating an outbound request to a remote peer and acts as the client.
     *
     * Typical uses: HTTP clients, database drivers, RPC/gRPC clients, calls to external services.
     */
    CLIENT,

    /**
     * The current code publishes a message to a transport or broker without waiting for processing.
     *
     * Typical uses: enqueue/send to Kafka/RabbitMQ/PubSub, placing an event/command onto a topic.
     */
    PRODUCER,

    /**
     * The current code consumes a message that was previously produced and delivered by a broker.
     *
     * Typical uses: Kafka/RabbitMQ/PubSub consumer handlers, background workers processing queue items.
     */
    CONSUMER,
}
