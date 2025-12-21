package cz.cleanship.telemetry

import cz.cleanship.telemetry.support.LocalInMemorySpanExporter
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micrometer.core.instrument.logging.LoggingRegistryConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micrometer.registry.otlp.OtlpConfig
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Default implementation of [TelemetryFacade] using OpenTelemetry and Micrometer.
 *
 * @param config telemetry configuration
 * @param testSpanExporter optional span exporter used for tests
 */
@Suppress("TooManyFunctions")
class DefaultTelemetry(
    private val config: TelemetryConfig = TelemetryConfig.fromEnvironment(),
    private val testSpanExporter: SpanExporter? = null,
    private val clock: Clock = Clock.SYSTEM,
    instrumentationScopeName: String = "cz.cleanship.telemetry",
    instrumentationScopeVersion: String = "1.0.0",
) : TelemetryFacade {

    private val meterRegistry: CompositeMeterRegistry = CompositeMeterRegistry()
    private var prometheusRegistry: PrometheusMeterRegistry? = null

    private val openTelemetry: OpenTelemetry
    private val tracer: Tracer
    private val sdkTracerProvider: SdkTracerProvider
    private var localInMemory: LocalInMemorySpanExporter? = null
    private val bootLogger = LoggerFactory.getLogger(DefaultTelemetry::class.java)

    private fun initMetricRegistries() {
        val simpleMeterRegistry = SimpleMeterRegistry()
        meterRegistry.add(simpleMeterRegistry)
        for (exporter in config.metricsExporters) {
            when (exporter) {
                MetricsExporter.PROMETHEUS -> {
                    val prom = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
                    meterRegistry.add(prom)
                    prometheusRegistry = prom
                }

                MetricsExporter.LOGGING -> {
                    val logging = LoggingMeterRegistry(
                        object : LoggingRegistryConfig {
                            override fun get(key: String) = null

                            override fun step(): Duration = Duration.ofSeconds(10)
                        },
                        clock,
                    )
                    meterRegistry.add(logging)
                }

                MetricsExporter.OTLP -> {
                    check(config.otlpEndpoint != null) { "telemetry.otlp.endpoint is not configured" }
                    val endpoint = config.otlpEndpoint
                    val otlp = OtlpMeterRegistry(
                        object : OtlpConfig {
                            override fun get(key: String): String? = null

                            override fun url(): String = endpoint
                        },
                        clock,
                    )
                    meterRegistry.add(otlp)
                }

                MetricsExporter.NONE -> { // skip
                }
            }
        }
    }

    private fun initTracing(): SdkTracerProvider {
        val spanExporters = mutableListOf<Pair<SpanExporter, Boolean>>() // exporter to isInMemory
        for (exporter in config.tracesExporters) {
            when (exporter) {
                TracesExporter.LOGGING -> spanExporters += LoggingSpanExporter.create() to false
                TracesExporter.OTLP -> {
                    val builder = OtlpGrpcSpanExporter.builder()
                    config.otlpEndpoint?.let {
                        builder.setEndpoint(it)
                    }
                    spanExporters += builder.build() to false
                }

                TracesExporter.INMEMORY_FOR_TESTS -> {
                    val inMem = (testSpanExporter as? LocalInMemorySpanExporter)
                        ?: LocalInMemorySpanExporter()
                    localInMemory = inMem
                    spanExporters += inMem to true
                }

                TracesExporter.NONE -> { // skip
                }
            }
        }

        val resource = Resource.getDefault().merge(
            Resource.create(
                io.opentelemetry.api.common.Attributes.of(
                    AttributeKey.stringKey("service.name"),
                    config.serviceName,
                ),
            ),
        )

        val tracerProviderBuilder = SdkTracerProvider.builder().setResource(resource)
        for ((exporter, isInMemory) in spanExporters) {
            val processor = if (isInMemory) {
                SimpleSpanProcessor.create(exporter)
            } else {
                BatchSpanProcessor.builder(exporter).build()
            }
            tracerProviderBuilder.addSpanProcessor(processor)
        }
        return tracerProviderBuilder.build()
    }

    init {
        initMetricRegistries()
        sdkTracerProvider = initTracing()
        openTelemetry = OpenTelemetrySdk
            .builder()
            .setTracerProvider(sdkTracerProvider)
            .setPropagators(
                ContextPropagators.create(
                    io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
                        .getInstance(),
                ),
            ).build()

        tracer = openTelemetry.getTracer(instrumentationScopeName, instrumentationScopeVersion)
    }

    override fun counter(name: String, tags: Map<String, String>): CounterHandle {
        validateMetricName(name)
        val micrometerTags = tagsToMicrometer(tags)
        val counter = meterRegistry.counter(name, micrometerTags)
        return object : CounterHandle {
            override fun increment(amount: Double) {
                counter.increment(amount)
            }
        }
    }

    override fun timer(name: String, tags: Map<String, String>): TimerHandle {
        validateMetricName(name)
        val micrometerTags = tagsToMicrometer(tags)
        val timer = Timer.builder(name).tags(micrometerTags).register(meterRegistry)
        return object : TimerHandle {
            override fun record(durationMillis: Long) {
                timer.record(durationMillis, TimeUnit.MILLISECONDS)
            }

            override suspend fun <T> recordSuspend(block: suspend () -> T): T {
                val start = System.nanoTime()
                try {
                    return block()
                } finally {
                    val end = System.nanoTime()
                    val duration = end - start
                    timer.record(duration, TimeUnit.NANOSECONDS)
                }
            }
        }
    }

    override fun gauge(name: String, tags: Map<String, String>): GaugeHandle {
        validateMetricName(name)
        val micrometerTags = tagsToMicrometer(tags)
        val ref = AtomicReference(0.0)
        Gauge.builder(name, ref) { r -> r.get() }.tags(micrometerTags).register(meterRegistry)
        return object : GaugeHandle {
            override fun set(value: Double) {
                ref.set(value)
            }
        }
    }

    override fun startSpan(name: String, kind: SpanKind, attributes: Map<String, Any?>): SpanScope {
        val builder = tracer.spanBuilder(name).setSpanKind(kind.toOtel()).setParent(Context.current())
        setSpanAttributes(builder, attributes)
        val otelSpan = builder.startSpan()
        val scope = otelSpan.makeCurrent()
        return object : SpanScope {
            override val span: TelemetrySpan = TelemetrySpanImpl(otelSpan)

            override fun close() {
                scope.close()
                otelSpan.end()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> inSpan(
        name: String,
        kind: SpanKind,
        attributes: Map<String, Any?>,
        block: suspend (TelemetrySpan) -> T,
    ): T {
        val parentContext = Context.current()
        val builder = tracer.spanBuilder(name).setSpanKind(kind.toOtel()).setParent(parentContext)
        setSpanAttributes(builder, attributes)
        val otelSpan = builder.startSpan()
        try {
            val scope = otelSpan.makeCurrent()
            try {
                // Propagate the CURRENT OTel context (which includes this span) across coroutines
                return withContext(Context.current().asContextElement()) {
                    try {
                        block(TelemetrySpanImpl(otelSpan))
                    } catch (e: Exception) {
                        otelSpan.recordException(e)
                        otelSpan.setStatus(StatusCode.ERROR)
                        throw e
                    }
                }
            } finally {
                scope.close()
            }
        } finally {
            otelSpan.end()
        }
    }

    override fun prometheusScrape(): String? = prometheusRegistry?.scrape()

    @Suppress("TooGenericExceptionCaught")
    override fun shutdown() {
        try {
            sdkTracerProvider.shutdown().join(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            bootLogger.debug("Failed to shutdown tracer provider", e)
        }

        // Close all meter registries to release background scheduler threads
        try {
            meterRegistry.close()
        } catch (e: Exception) {
            bootLogger.debug("Failed to close meter registries", e)
        }
    }

    // ---------- Helpers ----------

    private fun validateMetricName(name: String) {
        require(name.matches(Regex("^[A-Za-z0-9_:]+$"))) {
            "Invalid metric name '$name': must match [A-Za-z0-9_:]+"
        }
    }

    private fun validateLabelName(name: String) {
        require(name.matches(Regex("^[A-Za-z0-9_:]+$"))) {
            "Invalid label name '$name': must match [A-Za-z0-9_:]+"
        }
    }

    private fun validateLabelValue(value: String) {
        require('"' !in value && !value.any { it.isWhitespace() }) {
            "Invalid label value '$value': must not contain spaces or double quotes (\")"
        }
    }

    private fun tagsToMicrometer(tags: Map<String, String>): Iterable<Tag> =
        tags.map { (k, v) ->
            validateLabelName(k)
            validateLabelValue(v)
            Tag.of(k, v)
        }

    private fun setSpanAttributes(builder: io.opentelemetry.api.trace.SpanBuilder, attributes: Map<String, Any?>) {
        for ((k, v) in attributes) {
            when (v) {
                null -> Unit
                is String -> builder.setAttribute(AttributeKey.stringKey(k), v)
                is Boolean -> builder.setAttribute(AttributeKey.booleanKey(k), v)
                is Int -> builder.setAttribute(AttributeKey.longKey(k), v.toLong())
                is Long -> builder.setAttribute(AttributeKey.longKey(k), v)
                is Double -> builder.setAttribute(AttributeKey.doubleKey(k), v)
                is Float -> builder.setAttribute(AttributeKey.doubleKey(k), v.toDouble())
                else -> builder.setAttribute(AttributeKey.stringKey(k), v.toString())
            }
        }
    }

    // Internal accessors for tests
    internal fun inMemorySpans(): List<SpanData> {
        check(config.tracesExporters.contains(TracesExporter.INMEMORY_FOR_TESTS)) { "InMemory exporter not active" }
        localInMemory?.let { return it.finishedSpanItems }
        val e = testSpanExporter
        if (e is LocalInMemorySpanExporter) return e.finishedSpanItems
        error("InMemory exporter not available")
    }

    internal fun registry(): MeterRegistry = meterRegistry
}

private fun SpanKind.toOtel(): io.opentelemetry.api.trace.SpanKind = when (this) {
    SpanKind.INTERNAL -> io.opentelemetry.api.trace.SpanKind.INTERNAL
    SpanKind.SERVER -> io.opentelemetry.api.trace.SpanKind.SERVER
    SpanKind.CLIENT -> io.opentelemetry.api.trace.SpanKind.CLIENT
    SpanKind.PRODUCER -> io.opentelemetry.api.trace.SpanKind.PRODUCER
    SpanKind.CONSUMER -> io.opentelemetry.api.trace.SpanKind.CONSUMER
}

private class TelemetrySpanImpl(private val delegate: io.opentelemetry.api.trace.Span) : TelemetrySpan {
    private val ctx = delegate.spanContext
    override val traceId: String get() = ctx.traceId
    override val spanId: String get() = ctx.spanId
}
