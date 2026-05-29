package com.etrandafir.panoptes.testtracer.core.export

import com.etrandafir.panoptes.testtracer.core.io.NdjsonSpanWriter
import com.etrandafir.panoptes.testtracer.core.serialization.OtlpJsonSerializer
import com.etrandafir.panoptes.testtracer.core.serialization.SpanSerializer
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.nio.file.Path

/**
 * OpenTelemetry [SpanExporter] that writes each span as a line of JSON into an
 * NDJSON file. Designed to be paired with `SimpleSpanProcessor` so spans flush
 * to disk on `end()` — no batch loss if the test JVM dies.
 *
 * The on-wire format is delegated to a [SpanSerializer]. Default is
 * [OtlpJsonSerializer]; pass `ZipkinJsonSerializer()` to emit a Zipkin-importable
 * file alongside the primary OTLP one.
 */
class TestTracerSpanExporter(
    private val writer: NdjsonSpanWriter,
    private val serializer: SpanSerializer = OtlpJsonSerializer(),
) : SpanExporter {

    constructor(path: Path) : this(NdjsonSpanWriter(path))
    constructor(path: Path, serializer: SpanSerializer) : this(NdjsonSpanWriter(path), serializer)

    override fun export(spans: Collection<SpanData>): CompletableResultCode = try {
        for (data in spans) {
            writer.write(serializer.serialize(SpanDataMapper.toSpan(data)))
        }
        CompletableResultCode.ofSuccess()
    } catch (t: Throwable) {
        CompletableResultCode.ofFailure()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = try {
        writer.close()
        CompletableResultCode.ofSuccess()
    } catch (t: Throwable) {
        CompletableResultCode.ofFailure()
    }
}
