# Telemetry Module

A framework-agnostic telemetry facade for Kotlin apps. It hides vendor SDKs (OpenTelemetry, Micrometer) behind a simple API and supports configuration-driven exporters for Prometheus (metrics) and OTLP (Tempo traces, OTEL metrics). For logging, use standard SLF4J 2.x with Logback and the provided `TraceJsonProvider` to correlate logs with traces.

## Features

- Tracing via OpenTelemetry with W3C Trace Context propagation.
- Metrics via Micrometer: counters, timers, gauges. Prometheus-compatible naming/labels.
- Log correlation via SLF4J/Logback JSON with automatic `trace_id` and `span_id` via `TraceJsonProvider`. See [Structured JSON logging (Logback)](#structured-json-logging-logback).
- Configurable exporters via env vars / system properties.

## Table of contents

- [Gradle](#gradle)
- [Quick start](#quick-start)
- [Usage patterns and examples](#usage-patterns-and-examples)
- [Trace context: trace_id vs span_id and propagation](#trace-context-trace_id-vs-span_id-and-propagation)
- [Configuration](#configuration-env-or--d-system-properties)
- [Exporter selection and typical setups](#exporter-selection-and-typical-setups)
- [What prints to STDOUT vs what is sent](#what-prints-to-stdout-vs-what-is-sent)
- [Structured JSON logging (Logback)](#structured-json-logging-logback)
- [Using the sample Logback config](#using-the-sample-logback-config)
- [Extending and customizing logging](#extending-and-customizing-logging)
- [Gotchas](#gotchas)
- [Testing](#testing)

## Gradle

This module is already included in settings. Add dependency from your application module:

```kotlin
dependencies {
    implementation(project(":telemetry"))
}
```

## Quick start

```kotlin
import cz.cleanship.telemetry.*
import org.slf4j.LoggerFactory

fun main() {
    // create a telemetry instance with a default configuration
    val telemetry = Telemetry.create(TelemetryConfig.fromEnvironment())
    // create a logger
    val log = LoggerFactory.getLogger("example.app")

    // create and register counter metric (registered under a combination of name and tags - see docs)
    val requests = telemetry.counter("http_requests_total", mapOf("method" to "GET"))

    // wrap the next execution in a span (everything within that execution is part of the span (even nested spans)) and logged as such -> good for tracing e.g. unique incoming request processing
    // - span_id and trace_id are then automatically added to any logs within this span
    telemetry.inSpan("handle-request", SpanKind.SERVER, mapOf("route" to "/hello")) { span ->
        // increment counter -> counts incoming requests
        requests.increment()
        // create and register a timer metric (registered under a combination of name and tags - see docs)
        val timer = telemetry.timer("work_duration_ms")
        // record a time interval -> counts work duration
        timer.recordSuspend {
            // do work
        }
        // create and register a gauge metric (registered under a combination of name and tags - see docs) and set the value (e.g. queue depth)
        telemetry.gauge("queue_depth").set(3.0)
        // SLF4J 2.x event API for structured logging
        log.atInfo().addKeyValue("user", 42).log("Handled request")
    }
}
```

## Usage patterns and examples

### 1) Request handling: trace + metrics + structured logs

```kotlin
import cz.cleanship.telemetry.*
import org.slf4j.LoggerFactory

val telemetry = Telemetry.create()
val log = LoggerFactory.getLogger("app.http")
val httpRequests = telemetry.counter("http_requests_total")
val requestLatency = telemetry.timer("http_request_duration_ms")

suspend fun handleRequest(method: String, route: String) {
    telemetry.inSpan(
        "http.server.request", SpanKind.SERVER, mapOf(
            "http.method" to method,
            "http.route" to route,
        )
    ) {
        httpRequests.increment()
        requestLatency.recordSuspend {
            // your business logic here
        }
        log.atInfo().addKeyValue("method", method).addKeyValue("route", route).log("Handled request")
    }
}
```

Why this pattern:

- Tracing gives per-request diagnostics and causality.
- The counter and timer provide aggregate visibility for dashboards and alerts.
- Structured logs add searchable fields without high-cardinality metric labels.

### 2) External call: CLIENT span + latency metric

```kotlin
val externalLatency = telemetry.timer("external_api_duration_ms", mapOf("service" to "payments"))

suspend fun charge(amount: Long) = telemetry.inSpan(
    name = "payments.charge",
    kind = SpanKind.CLIENT,
    attributes = mapOf("amount" to amount),
) {
    externalLatency.recordSuspend {
        // invoke HTTP client here
    }
}
```

Why this pattern:

- A CLIENT span clearly marks the outbound dependency in traces.
- The timer captures latency distribution over time for SLOs and alerting.

### 3) Error logging inside spans

```kotlin
val log = LoggerFactory.getLogger("app.errors")

suspend fun doWork() = telemetry.inSpan("work") {
    try {
        // risky operation
    } catch (e: Exception) {
        // inSpan will recordException and set span status=ERROR when rethrowing
        log.atError().setCause(e).addKeyValue("op", "work").log("Operation failed")
        throw e
    }
}
```

Why this pattern:

- The span captures the exception (stack trace, status) for traces.
- The log provides an immediate alerting/signal channel and searchable context.

### 4) Async/coroutines propagation

`DefaultTelemetry.inSpan` propagates the OpenTelemetry context across coroutines via `Context.asContextElement()`, so any log or nested span inside the `inSpan` block is correlated automatically.

```kotlin
suspend fun doConcurrent() = telemetry.inSpan("concurrent") {
    coroutineScope {
        val a = async { /* logs here include trace/span */ }
        val b = async { /* spans created here are children of 'concurrent' */ }
        a.await(); b.await()
    }
}
```

### 5) Optional: map-style logging helpers (SLF4J 2.x)

If you prefer passing fields as a map, the module provides tiny extensions built on the SLF4J 2.x event API:

```kotlin
import cz.cleanship.telemetry.logging.info
import cz.cleanship.telemetry.logging.error

val log = LoggerFactory.getLogger("app")
log.info("Processed order", mapOf("orderId" to 123, "user" to 42))
log.error("Failed", fields = mapOf("orderId" to 123), t = RuntimeException("boom"))
```

Reasoning:

- They delegate to `logger.atXxx().addKeyValue(...)` under the hood; use them if you like map ergonomics.
- Otherwise, prefer the standard event API directly.

### 6) Framework endpoints: expose Prometheus

Ktor example:

```kotlin
val telemetry = Telemetry.create()

routing {
    get("/metrics") {
        call.respondText(telemetry.prometheusScrape() ?: "prometheus exporter disabled", ContentType.Text.Plain)
    }
}
```

Spring Boot example:

```kotlin
@RestController
class MetricsController(private val telemetry: TelemetryFacade = Telemetry.create()) {
    @GetMapping("/metrics")
    fun metrics(): ResponseEntity<String> = ResponseEntity.ok(telemetry.prometheusScrape() ?: "prometheus exporter disabled")
}
```

### 7) Recommended label hygiene for metrics

- Prefer low-cardinality labels: `http.method`, `http.route` (templated), `status_class`.
- Avoid labels like user ID, raw URL, request ID (these belong in spans/logs).
- Use spans for high-cardinality, per-request context; metrics for aggregation and alerting.

### Metric naming and label requirements (strict)

Metric names and labels must follow these rules to ensure compatibility with common monitoring backends and predictable dashboards/queries.

- Metric name format: must match `[A-Za-z0-9_:]+`
  - Examples: `http_requests_total`, `db_query_latency`, `my:custom:metric`
  - Not allowed: `http-requests` (dash), `db.query.latency` (dot), `user id` (space)

- Label key format: must match `[A-Za-z0-9_:]+`
  - Examples: `method`, `status_code`, `service:role`
  - Not allowed: `status-code` (dash), `http.route` (dot), `user id` (space)

- Label value format: must not contain spaces or the double-quote character (`"`)
  - Examples: `GET`, `payments`, `/hello`
  - Not allowed: `bad value` (space), `a"b` (double quote)

Behavior on invalid inputs

- `TelemetryFacade.counter(...)`, `timer(...)`, and `gauge(...)` throw `IllegalArgumentException` if the name or any tag key/value violates these rules. See method KDoc for details.

Rationale

- Ensures consistent, portable naming across tools (Prometheus/OTLP).
- Prevents accidental high-cardinality or ambiguous series due to ad-hoc characters.

## Trace context: trace_id vs span_id and propagation

Short version

- trace_id: Identifies the entire request/transaction across services. All spans for the same operation share it.
- span_id: Identifies one span (unit of work) within that trace. Child spans point to their parent via parentSpanId.

How to use it across services

1) Get the IDs in service A

- Inside `TelemetryFacade.inSpan { }`, you receive `TelemetrySpan` with `traceId` and `spanId`.
- If you log within a span, the JSON provider (or our logger helpers) adds `trace_id` and `span_id` automatically.

2) Inject trace context into outgoing HTTP headers (service A)

```kotlin
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context

suspend fun callDownstream() = telemetry.inSpan("payments.charge", SpanKind.CLIENT) {
    val headers = mutableMapOf<String, String>()
    val propagator = W3CTraceContextPropagator.getInstance()
    // Inject the CURRENT context (must be inside an active span)
    propagator.inject(Context.current(), headers) { carrier, key, value -> carrier[key] = value }
    // Use 'headers' for your HTTP client request
}
```

3) Extract context on the receiving side (service B) and create a child span

```kotlin
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context

fun handleIncoming(headers: Map<String, String>, telemetry: TelemetryFacade) {
    val propagator = W3CTraceContextPropagator.getInstance()
    val parentCtx = propagator.extract(Context.current(), headers) { carrier, key -> carrier[key] }
    val scope = parentCtx.makeCurrent()
    try {
        telemetry.inSpan("handle-request", SpanKind.SERVER) { span ->
            // This span shares the same trace_id, has a new span_id, and links to the parent
        }
    } finally {
        scope.close()
    }
}
```

Notes

- You do not set `trace_id` manually. You propagate context (headers) between services; the new span inherits the `trace_id`.
- Many frameworks/instrumentations can auto-inject/extract W3C headers. The examples above show the minimal manual approach when using this facade directly.

## Configuration (env or -D system properties)

- TELEMETRY_SERVICE_NAME / -Dtelemetry.service.name (default: `bootstrap-kotlin`)
- TELEMETRY_EXPORTER_TRACES / -Dtelemetry.traces.exporter: comma-separated list of `none` | `logging` | `otlp` |
  `inmemory`
- TELEMETRY_EXPORTER_METRICS / -Dtelemetry.metrics.exporter: comma-separated list of `none` | `prometheus` | `otlp` |
  `logging`
- TELEMETRY_OTLP_ENDPOINT / -Dtelemetry.otlp.endpoint (default: none; example: `http://localhost:4318`)
  Notes:
- Multiple exporters can be enabled simultaneously by comma-separating values.
- See “Exporter selection and typical setups” below for concrete configurations.

## Exporter selection and typical setups

- Traces (`TELEMETRY_EXPORTER_TRACES`)
    - `none`: disable trace export
    - `logging`: export spans to stdout (OpenTelemetry LoggingSpanExporter)
    - `otlp`: export spans to an OTLP collector (uses `TELEMETRY_OTLP_ENDPOINT`)
    - `inmemory`: in-memory exporter for tests

- Metrics (`TELEMETRY_EXPORTER_METRICS`)
    - `none`: only a SimpleMeterRegistry is active
    - `prometheus`: adds PrometheusMeterRegistry; `prometheusScrape()` returns exposition text
    - `otlp`: adds OtlpMeterRegistry; pushes metrics to the collector
    - `logging`: adds LoggingMeterRegistry; periodically logs metric snapshots

Notes

- Exporters are additive: you can enable multiple for traces and metrics at the same time (the SimpleMeterRegistry is
  always present).
- Traces and metrics are configured independently.
- Logging uses standard SLF4J; correlation is added by the Logback JSON provider described below.

Typical configs

- Local dev (console only):
    - `TELEMETRY_EXPORTER_TRACES=logging`
    - `TELEMETRY_EXPORTER_METRICS=logging`
- Prod (pull metrics, send traces):
    - `TELEMETRY_EXPORTER_TRACES=otlp`
    - `TELEMETRY_EXPORTER_METRICS=prometheus`
    - `TELEMETRY_OTLP_ENDPOINT=http://collector:4318`
- Combined (send and see locally):
    - `TELEMETRY_EXPORTER_TRACES=logging,otlp`
    - `TELEMETRY_EXPORTER_METRICS=prometheus,otlp,logging`
- Tests:
    - `TELEMETRY_EXPORTER_TRACES=inmemory`
    - `TELEMETRY_EXPORTER_METRICS=none`

## What prints to STDOUT vs what is sent

- Application logs
    - Emitted by Logback appenders. With `TraceJsonProvider` in the JSON encoder, logs inside `telemetry.inSpan { }` include `trace_id`/`span_id`.

- Traces (`TELEMETRY_EXPORTER_TRACES`)
    - `logging`: spans printed to STDOUT.
    - `otlp`: spans sent to the collector at `TELEMETRY_OTLP_ENDPOINT`.
    - `inmemory`: spans kept in-memory for tests.
    - `none`: disabled.

- Metrics (`TELEMETRY_EXPORTER_METRICS`)
    - `logging`: periodic snapshots printed to STDOUT.
    - `prometheus`: expose `telemetry.prometheusScrape()` via HTTP; Prometheus pulls it.
    - `otlp`: metrics pushed to the collector.
    - `none`: disabled.

If you enable multiple exporters (e.g., `logging,otlp`), all behaviors apply.

Prometheus endpoint examples: see "Usage patterns and examples" → "Framework endpoints: expose Prometheus".

## Structured JSON logging (Logback)

Choose one of the following approaches:

### Option A: Provider-based (recommended)

- Add `cz.cleanship.telemetry.logging.TraceJsonProvider` to write `trace_id`/`span_id` directly based on the current OpenTelemetry context.
- Use SLF4J 2.x event API to add structured fields.

```xml

<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <loggerName/>
                <threadName/>
                <logLevel/>
                <message/>
                <provider class="cz.cleanship.telemetry.logging.TraceJsonProvider"/>
            </providers>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

See also: `telemetry/logback.sample.xml` for a minimal reference configuration.

Kotlin logging example with structured fields using SLF4J 2.x:

```kotlin
val log = LoggerFactory.getLogger("app")
log.atInfo().addKeyValue("user", 42).addKeyValue("order", 123).log("Processed order")
```

### Option B: MDC-based (alternative)

- If you prefer MDC, include `<mdc/>` in your JSON providers and manage MDC yourself (or with filters).

```xml

<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <loggerName/>
                <threadName/>
                <logLevel/>
                <message/>
                <mdc/>
            </providers>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

### Using the sample Logback config

1. Copy `telemetry/logback.sample.xml` into your application as `src/main/resources/logback.xml`.

2. Start your app. Logs will be JSON on STDOUT and include `trace_id`/`span_id` when emitted inside `telemetry.inSpan { ... }`.

## Extending and customizing logging

You can add more context to logs in two simple ways:

### A) MDC (Mapped Diagnostic Context)

Use MDC for fields that should appear on every log within a scope (including logs from third‑party libraries) when your encoder prints `<mdc/>`.

```kotlin
import org.slf4j.MDC

fun handleRequest(reqId: String) {
    MDC.putCloseable("request_id", reqId).use {
        log.atInfo().log("Start")
        // ... other code (including library logs) will include request_id
        log.atInfo().log("Done")
    } // request_id removed automatically
}
```

Notes:

- MDC is thread‑local; in coroutines, execution may hop threads. If you need MDC across coroutines, consider `kotlinx-coroutines-slf4j` and wrap blocks with `MDCContext(MDC.getCopyOfContextMap())`.
- Keep MDC keys low‑cardinality and non‑sensitive; they are printed on every log when `<mdc/>` is enabled.

MDC with coroutines (kotlinx‑coroutines‑slf4j)

Add the dependency to your application module (align the version with your coroutines):

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.10.2")
}
```

Propagate MDC into coroutine blocks:

```kotlin
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC

suspend fun handleRequest(reqId: String) {
    MDC.putCloseable("request_id", reqId).use {
        // Capture current MDC and propagate within this coroutine scope
        withContext(MDCContext()) {
            log.atInfo().log("Start")
            // ... other suspending work; logs include request_id if <mdc/> is enabled
            log.atInfo().log("Done")
        }
    }
}
```

### B) Custom JSON providers

For fields derived at encode time (no MDC), implement a provider:

```kotlin
class EnvJsonProvider : AbstractJsonProvider<ILoggingEvent>() {
    override fun writeTo(generator: JsonGenerator, event: ILoggingEvent) {
        generator.writeStringField("env", System.getenv("ENV") ?: "dev")
    }
}
```

Register it in Logback alongside `TraceJsonProvider`:

```xml

<providers>
    <timestamp/>
    <loggerName/>
    <threadName/>
    <logLevel/>
    <message/>
    <provider class="cz.cleanship.telemetry.logging.TraceJsonProvider"/>
    <provider class="com.example.logging.EnvJsonProvider"/>
    <!-- or <mdc/> if you rely on MDC -->
</providers>
```

When to use what:

- Use `TraceJsonProvider` for `trace_id`/`span_id` (no MDC needed).
- Use SLF4J 2.x event API for per‑log structured fields (`addKeyValue`).
- Use MDC for ambient fields you want on every log in a scope, including third‑party logs.

## Gotchas

- Log correlation requires the provider
    - Add `cz.cleanship.telemetry.logging.TraceJsonProvider` to your Logback JSON encoder providers. Without it (or MDC), `trace_id`/`span_id` will not be written.
    - The provider works with JSON encoders (e.g., logstash-logback-encoder). It does not affect plain pattern layouts.
- Context propagation
    - Inside `telemetry.inSpan { ... }` the span is set as current and is propagated across coroutines via `Context.asContextElement()`.
    - If you spawn threads or leave the `inSpan` scope, propagate OTel Context manually if you still need correlation.
- SLF4J API level
    - Examples use SLF4J 2.x event API (`logger.atInfo().addKeyValue(...)`). With Logback 1.5.x this is available by default.
    - If stuck on older SLF4J, consider `StructuredArguments` from logstash-logback-encoder as an alternative for structured fields.
- MDC is optional
    - The provider reads the current OTel context directly; you do not need MDC for trace/span correlation.
    - If you choose to rely on MDC for other reasons, include `<mdc/>` in providers and manage MDC entries carefully.
- Metric label cardinality
    - Keep metric labels low-cardinality (e.g., `http.method`, `http.route`, `status_class`). Avoid user IDs, raw URLs, etc.
    - Use spans for high-cardinality diagnostic data; use metrics for aggregation and alerting.

## Testing

Run tests (module only):

```bash
./gradlew :telemetry:test
```

The suite uses:

- LocalInMemorySpanExporter for spans
- Micrometer Search API for metric assertions
- Logback JSON encoder with `TraceJsonProvider` for log correlation assertions
