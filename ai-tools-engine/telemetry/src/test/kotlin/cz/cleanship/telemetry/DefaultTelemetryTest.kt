package cz.cleanship.telemetry

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import cz.cleanship.telemetry.logging.TraceJsonProvider
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micrometer.core.instrument.search.Search
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class DefaultTelemetryTest {

    private fun telemetry(
        serviceName: String = "test-service",
        metricsExporters: Set<MetricsExporter> = emptySet(),
    ): DefaultTelemetry = DefaultTelemetry(
        TelemetryConfig(
            serviceName = serviceName,
            tracesExporters = setOf(TracesExporter.INMEMORY_FOR_TESTS),
            metricsExporters = metricsExporters,
            otlpEndpoint = "http://localhost:4318",
        ),
    )

    // ---- Validation ----

    @Test
    fun `metric name must match regex`() {
        // given
        val t = telemetry()

        // when/then
        assertThatThrownBy { t.counter("bad-name") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { t.timer("db.query.latency") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatCode { t.counter("good_name:part2") }.doesNotThrowAnyException()
    }

    @Test
    fun `label key and value must follow rules`() {
        // given
        val t = telemetry()

        // when/then
        assertThatThrownBy { t.counter("ok", mapOf("bad key" to "v")) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { t.counter("ok", mapOf("key" to "bad value")) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { t.gauge("ok", mapOf("http.route" to "/hello")) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatCode { t.timer("ok", mapOf("status_code" to "200")) }.doesNotThrowAnyException()
    }

    // ---- Tracing attributes & errors ----

    @Test
    fun `attributes of various types are set with correct typing`() = runTest {
        // given
        val t = telemetry()

        // when
        t.inSpan(
            name = "attr-op",
            attributes = mapOf(
                "s" to "str",
                "b" to true,
                "i" to 7,
                "l" to 9L,
                "d" to 1.5,
                "f" to 2.5f,
                "x" to listOf(1, 2, 3),
            ),
        ) { }

        // then
        val span = t.inMemorySpans().find { it.name == "attr-op" }!!
        val attrs = span.attributes
        assertThat(attrs.get(AttributeKey.stringKey("s"))).isEqualTo("str")
        assertThat(attrs.get(AttributeKey.booleanKey("b"))).isTrue()
        assertThat(attrs.get(AttributeKey.longKey("i"))).isEqualTo(7L)
        assertThat(attrs.get(AttributeKey.longKey("l"))).isEqualTo(9L)
        assertThat(attrs.get(AttributeKey.doubleKey("d"))).isEqualTo(1.5)
        assertThat(attrs.get(AttributeKey.doubleKey("f"))).isEqualTo(2.5)
        assertThat(attrs.get(AttributeKey.stringKey("x"))).isEqualTo(listOf(1, 2, 3).toString())
    }

    @Test
    fun `exceptions inside inSpan are recorded and status set to ERROR`() = runTest {
        // given
        val t = telemetry()

        // when
        runCatching {
            t.inSpan("failing-op") {
                throw IllegalStateException("boom")
            }
        }

        // then
        val spans = t.inMemorySpans()
        val span = spans.find { it.name == "failing-op" }
        assertThat(span).isNotNull
        span!!
        assertThat(span.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(span.events.any { it.name == "exception" }).isTrue()
    }

    // ---- Misc ----

    @Test
    fun `shutdown completes without throwing`() {
        // given
        val t = telemetry()

        // when/then
        assertThatCode { t.shutdown() }.doesNotThrowAnyException()
    }

    @Test
    fun `prometheusScrape returns null when Prometheus exporter is disabled`() {
        // given
        val t = telemetry()

        // when
        val scrape = t.prometheusScrape()

        // then
        assertThat(scrape).isNull()
    }

    @Test
    fun `span resource contains service_name`() = runTest {
        // given
        val t = telemetry(serviceName = "my-service")

        // when
        t.inSpan("res-op") { }

        // then
        val span = t.inMemorySpans().first { it.name == "res-op" }
        assertThat(span.resource.getAttribute(AttributeKey.stringKey("service.name"))).isEqualTo("my-service")
    }

    @Test
    fun `can start spans for all kinds`() = runTest {
        // given
        val t = telemetry()

        // when
        for (k in SpanKind.entries) {
            t.inSpan("kind-$k", k) { }
        }

        // then
        val names = t.inMemorySpans().map { it.name }.toSet()
        for (k in SpanKind.entries) {
            assert(names.contains("kind-$k")) { "missing span for kind $k" }
        }
    }

    @Test
    fun `startSpan creates and ends span on close`() {
        // given
        val t = telemetry()

        // when
        val scope = t.startSpan("manual-span", SpanKind.INTERNAL)
        scope.close()

        // then
        val spans = t.inMemorySpans()
        val span = spans.find { it.name == "manual-span" }!!
        assertThat(span.name).isEqualTo("manual-span")
    }

    @Test
    fun `logs inside spans include trace and span ids via TraceJsonProvider`() = runTest {
        // given
        // - configure Logback JSON with TraceJsonProvider and capture output
        val telemetry = telemetry()
        val loggerName = "telemetry.test"

        val slf4jLogger = LoggerFactory.getLogger(loggerName) as Logger
        val ctx = slf4jLogger.loggerContext

        // - configure JSON encoder with our provider
        val encoder = LoggingEventCompositeJsonEncoder()
        encoder.context = ctx
        val providers = LoggingEventJsonProviders()
        providers.addProvider(TraceJsonProvider())
        encoder.setProviders(providers)
        encoder.start()

        // - capture output into a byte array
        val baos = ByteArrayOutputStream()
        val appender = object : OutputStreamAppender<ILoggingEvent>() {}
        appender.context = ctx
        appender.encoder = encoder
        appender.outputStream = baos
        appender.start()
        slf4jLogger.addAppender(appender)

        // when
        // - log inside an active span
        telemetry.inSpan("logging-span") { _ ->
            slf4jLogger.atInfo().addKeyValue("foo", "bar").log("hello")
        }

        // then
        // - JSON contains trace/span identifiers
        val json = baos.toString("UTF-8")
        assertThat(json).contains("trace_id")
        assertThat(json).contains("span_id")
    }

    @Test
    fun `counter increments with tags`() {
        // given
        // - counter with a static label
        val telemetry = telemetry()
        val counter = telemetry.counter("test_counter", mapOf("label" to "value"))

        // when
        counter.increment(2.0)
        counter.increment(3.0)

        // then
        // - micrometer registry observes the expected total
        val found = Search
            .`in`(telemetry.registry())
            .name("test_counter")
            .tag("label", "value")
            .counter()
        assertThat(found).isNotNull
        assertThat(found!!.count()).isCloseTo(5.0, within(1e-6))
    }

    @Test
    fun `timer records durations`() = runTest {
        // given
        // - a timer with a label for filtering
        val telemetry = telemetry()
        val timer = telemetry.timer("test_timer", mapOf("status" to "ok"))

        // when
        timer.record(25)
        timer.recordSuspend { /* simulate work */ }

        // then
        // - count >= 2 and total time is positive
        val found = Search
            .`in`(telemetry.registry())
            .name("test_timer")
            .tag("status", "ok")
            .timer()
        assertThat(found).isNotNull
        assertThat(found!!.count()).isGreaterThanOrEqualTo(2L)
        assertThat(found.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0.0)
    }

    @Test
    fun `gauge reflects last value`() {
        // given
        // - gauge updated over time
        val telemetry = telemetry()
        val gauge = telemetry.gauge("test_gauge", mapOf("phase" to "alpha"))

        // when
        gauge.set(7.0)

        // then
        // - registry shows the latest value
        val found = Search
            .`in`(telemetry.registry())
            .name("test_gauge")
            .tag("phase", "alpha")
            .gauge()
        assertThat(found).isNotNull
        assertThat(found!!.value()).isCloseTo(7.0, within(1e-6))

        // when
        // - update to a new value
        gauge.set(2.5)

        // then
        // - registry reflects the last set value
        assertThat(found.value()).isCloseTo(2.5, within(1e-6))
    }

    @Test
    fun `prometheus scrape includes metrics`() {
        // given
        // - prometheus registry enabled
        val telemetry = telemetry(metricsExporters = setOf(MetricsExporter.PROMETHEUS))
        // - and a counter with method/status labels
        val counter = telemetry.counter("http_server_requests_total", mapOf("method" to "GET", "status" to "200"))

        // when
        // - increment once so metric appears in scrape
        counter.increment()

        // then
        // - scrape contains metric name and labels
        val scrape = telemetry.prometheusScrape()
        assertThat(scrape).isNotNull
        val text = scrape!!
        assertThat(text).contains("http_server_requests_total")
        assertThat(text).contains("method=\"GET\"")
        assertThat(text).contains("status=\"200\"")
    }

    @Test
    fun `parent-child spans are linked`() = runTest {
        // given
        // - in-memory exporter for assertions
        val telemetry = telemetry()

        // when
        // - create a parent span and a nested child span
        telemetry.inSpan("parent", SpanKind.INTERNAL) { _ ->
            telemetry.inSpan("child", SpanKind.INTERNAL) { _ ->
                // - no-op; relationship is what we validate
            }
        }

        // then
        // - child shares traceId and links to parent via parentSpanId
        val spans = telemetry.inMemorySpans()
        val parent = spans.find { it.name == "parent" }!!
        val child = spans.find { it.name == "child" }!!
        assertThat(child.traceId).isEqualTo(parent.traceId)
        assertThat(child.parentSpanId).isEqualTo(parent.spanId)
    }

    @Test
    fun `trace context propagates across coroutines`() = runTest {
        // given
        // - context propagates via asContextElement()
        val telemetry = telemetry()

        // when
        // - start a root span and jump to another dispatcher; create a child span there
        telemetry.inSpan("root", SpanKind.INTERNAL) { _ ->
            withContext(Dispatchers.Default) {
                telemetry.inSpan("async-child") { _ ->
                    // - body intentionally empty
                }
            }
        }

        // then
        // - child keeps trace and links to root across dispatcher boundary
        val spans = telemetry.inMemorySpans()
        val root = spans.find { it.name == "root" }!!
        val child = spans.find { it.name == "async-child" }!!
        assertThat(child.traceId).isEqualTo(root.traceId)
        assertThat(child.parentSpanId).isEqualTo(root.spanId)
    }

    @Test
    fun `attributes are set on spans`() = runTest {
        // given
        // - attributes to set on span creation
        val telemetry = telemetry()

        // when
        // - start a span with attribute map
        telemetry.inSpan("operation", attributes = mapOf("user.id" to 123, "success" to true)) { _ ->
            // - body intentionally empty
        }

        // then
        // - typed attributes are present on the exported span
        val span = telemetry.inMemorySpans().find { it.name == "operation" }!!
        assertThat(span.attributes.get(AttributeKey.longKey("user.id"))).isEqualTo(123L)
        assertThat(span.attributes.get(AttributeKey.booleanKey("success"))).isTrue()
    }

    @Test
    fun `logging exporter adds LoggingMeterRegistry`() {
        // given
        val t = telemetry(metricsExporters = setOf(MetricsExporter.LOGGING))
        val composite = t.registry() as CompositeMeterRegistry
        // Register counter explicitly so it propagates to all child registries
        val counter = Counter.builder("logging_counter").tags("sink", "logging").register(composite)

        // when
        counter.increment(4.0)

        // then
        // - the LoggingMeterRegistry has the same counter with expected tags
        val logReg = composite.registries.first { it is LoggingMeterRegistry }
        val logged = Search
            .`in`(logReg)
            .name("logging_counter")
            .tag("sink", "logging")
            .counter()
        assertThat(logged).isNotNull
        assertThat(logged!!.id.name).isEqualTo("logging_counter")
        assertThat(logged.id.tags.any { it.key == "sink" && it.value == "logging" }).isTrue()
    }

    @Test
    fun `otlp exporter adds OtlpMeterRegistry`() {
        // given
        val t = telemetry(metricsExporters = setOf(MetricsExporter.OTLP))
        val composite = t.registry() as CompositeMeterRegistry
        // - register counter explicitly so it propagates to all child registries
        val counter = Counter.builder("otlp_counter").tags("sink", "otlp").register(composite)

        // when
        counter.increment(3.0)

        // then
        // - the OtlpMeterRegistry contains the same counter with expected tags
        val otlpReg = composite.registries.first { it is OtlpMeterRegistry }
        val otlp = Search
            .`in`(otlpReg)
            .name("otlp_counter")
            .tag("sink", "otlp")
            .counter()
        assertThat(otlp).isNotNull
        assertThat(otlp!!.id.name).isEqualTo("otlp_counter")
        assertThat(otlp.id.tags.any { it.key == "sink" && it.value == "otlp" }).isTrue()
    }
}
