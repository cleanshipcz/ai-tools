package cz.cleanship.telemetry.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import cz.cleanship.telemetry.DefaultTelemetry
import cz.cleanship.telemetry.SpanKind
import cz.cleanship.telemetry.TelemetryConfig
import cz.cleanship.telemetry.TracesExporter
import kotlinx.coroutines.test.runTest
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

class TraceJsonProviderTest {

    private lateinit var telemetry: DefaultTelemetry
    private lateinit var slf4jLogger: Logger
    private lateinit var baos: ByteArrayOutputStream

    @BeforeEach
    fun setUp() {
        telemetry = DefaultTelemetry(
            TelemetryConfig(
                serviceName = "test-service",
                tracesExporters = setOf(TracesExporter.INMEMORY_FOR_TESTS),
                metricsExporters = emptySet(),
                otlpEndpoint = null,
            ),
        )

        // - configure Logback JSON with TraceJsonProvider
        slf4jLogger = LoggerFactory.getLogger("telemetry.provider.test") as Logger
        val ctx = slf4jLogger.loggerContext

        val encoder = LoggingEventCompositeJsonEncoder()
        encoder.context = ctx
        val providers = LoggingEventJsonProviders()
        providers.addProvider(TraceJsonProvider())
        encoder.setProviders(providers)
        encoder.start()

        baos = ByteArrayOutputStream()
        val appender = object : OutputStreamAppender<ILoggingEvent>() {}
        appender.context = ctx
        appender.encoder = encoder
        appender.outputStream = baos
        appender.start()
        slf4jLogger.addAppender(appender)
    }

    @Test
    fun `should include trace_id and span_id in logs inside span`() = runTest {
        // given
        // - a span with some attributes

        // when
        telemetry.inSpan("test-span", SpanKind.INTERNAL) {
            slf4jLogger.info("test message")
        }

        // then
        val json = baos.toString("UTF-8")
        assertThat(json).contains("trace_id")
        assertThat(json).contains("span_id")
    }

    @Test
    fun `should include span attributes in logs inside span`() = runTest {
        // given
        // - a span with custom attributes

        // when
        telemetry.inSpan(
            name = "op-with-attrs",
            attributes = mapOf("user_id" to "user-123", "operation" to "create"),
        ) {
            slf4jLogger.info("processing request")
        }

        // then
        // - JSON should contain the span attributes
        val json = baos.toString("UTF-8")
        assertThat(json).contains("user_id")
        assertThat(json).contains("user-123")
        assertThat(json).contains("operation")
        assertThat(json).contains("create")
    }

    @Test
    fun `should merge attributes from nested spans with deepest priority`() = runTest {
        // given
        // - nested spans with overlapping attribute keys

        // when
        telemetry.inSpan(
            name = "outer-span",
            attributes = mapOf("level" to "outer", "outer_only" to "outer-value"),
        ) {
            telemetry.inSpan(
                name = "inner-span",
                attributes = mapOf("level" to "inner", "inner_only" to "inner-value"),
            ) {
                slf4jLogger.info("inside nested span")
            }
        }

        // then
        // - JSON should contain merged attributes with inner overriding outer for 'level'
        val json = baos.toString("UTF-8")
        assertThat(json).contains("outer_only")
        assertThat(json).contains("outer-value")
        assertThat(json).contains("inner_only")
        assertThat(json).contains("inner-value")
        // - 'level' should be 'inner' (deepest wins)
        assertThat(json).contains("\"level\"")
        assertThat(json).contains("inner")
        // - should NOT contain 'outer' as value for level (it was overridden)
        assertThat(json).doesNotContain("\"level\":\"outer\"")
    }

    @Test
    fun `should include attributes with various types in logs`() = runTest {
        // given
        // - a span with attributes of different types

        // when
        telemetry.inSpan(
            name = "typed-attrs",
            attributes = mapOf(
                "string_attr" to "hello",
                "int_attr" to 42,
                "bool_attr" to true,
                "double_attr" to 3.14,
            ),
        ) {
            slf4jLogger.info("typed attributes test")
        }

        // then
        val json = baos.toString("UTF-8")
        assertThat(json).contains("string_attr")
        assertThat(json).contains("hello")
        assertThat(json).contains("int_attr")
        assertThat(json).contains("42")
        assertThat(json).contains("bool_attr")
        assertThat(json).contains("true")
        assertThat(json).contains("double_attr")
        assertThat(json).contains("3.14")
    }

    @Test
    fun `should restore parent attributes after nested span ends`() = runTest {
        // given
        // - nested spans where inner ends before second log

        // when
        telemetry.inSpan(
            name = "parent-span",
            attributes = mapOf("scope" to "parent"),
        ) {
            telemetry.inSpan(
                name = "child-span",
                attributes = mapOf("scope" to "child"),
            ) {
                // - log inside child span
            }
            // - reset output to capture only the second log
            baos.reset()
            slf4jLogger.info("after child span")
        }

        // then
        // - JSON should contain parent's 'scope' value, not child's
        val json = baos.toString("UTF-8")
        assertThat(json).contains("scope")
        assertThat(json).contains("parent")
        assertThat(json).doesNotContain("\"scope\":\"child\"")
    }
}
