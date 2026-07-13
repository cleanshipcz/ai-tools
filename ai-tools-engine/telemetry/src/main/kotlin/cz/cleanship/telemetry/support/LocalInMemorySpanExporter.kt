package cz.cleanship.telemetry.support

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.*

/**
 * Lightweight in-memory span exporter intended for unit tests.
 */
class LocalInMemorySpanExporter : SpanExporter {
    private val spans: MutableList<SpanData> = Collections.synchronizedList(mutableListOf())

    /**
     * Snapshot of finished spans exported so far.
     */
    val finishedSpanItems: List<SpanData> get() = ArrayList(spans)

    /**
     * Adds [spans] to the in-memory buffer.
     *
     * @param spans finished spans
     * @return success code
     */
    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        this.spans.addAll(spans)
        return CompletableResultCode.ofSuccess()
    }

    /**
     * No-op flush.
     *
     * @return success code
     */
    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    /**
     * No-op shutdown.
     *
     * @return success code
     */
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

    /**
     * Clears the collected spans.
     */
    fun reset() {
        spans.clear()
    }
}
