package com.etrandafir.panoptes.testtracer.core.export

import com.etrandafir.panoptes.testtracer.core.model.AttributeValue
import com.etrandafir.panoptes.testtracer.core.model.Attributes
import com.etrandafir.panoptes.testtracer.core.model.InstrumentationScope
import com.etrandafir.panoptes.testtracer.core.model.Resource
import com.etrandafir.panoptes.testtracer.core.model.Span
import com.etrandafir.panoptes.testtracer.core.model.SpanEvent
import com.etrandafir.panoptes.testtracer.core.model.SpanKind
import com.etrandafir.panoptes.testtracer.core.model.SpanStatus
import com.etrandafir.panoptes.testtracer.core.model.StatusCode
import io.opentelemetry.api.common.AttributeType
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.api.common.Attributes as OtelAttributes
import io.opentelemetry.api.trace.SpanKind as OtelSpanKind
import io.opentelemetry.api.trace.StatusCode as OtelStatusCode

internal object SpanDataMapper {

    fun toSpan(data: SpanData): Span = Span(
        traceId = data.traceId,
        spanId = data.spanId,
        parentSpanId = data.parentSpanContext.takeIf { it.isValid }?.spanId,
        name = data.name,
        kind = mapKind(data.kind),
        startEpochNanos = data.startEpochNanos,
        endEpochNanos = data.endEpochNanos,
        status = mapStatus(data.status),
        attributes = mapAttributes(data.attributes),
        events = data.events.map(::mapEvent),
        resource = Resource(mapAttributes(data.resource.attributes), data.resource.schemaUrl),
        scope = mapScope(data.instrumentationScopeInfo),
    )

    private fun mapKind(kind: OtelSpanKind): SpanKind = when (kind) {
        OtelSpanKind.INTERNAL -> SpanKind.INTERNAL
        OtelSpanKind.SERVER -> SpanKind.SERVER
        OtelSpanKind.CLIENT -> SpanKind.CLIENT
        OtelSpanKind.PRODUCER -> SpanKind.PRODUCER
        OtelSpanKind.CONSUMER -> SpanKind.CONSUMER
    }

    private fun mapStatus(status: StatusData): SpanStatus {
        val code = when (status.statusCode) {
            OtelStatusCode.UNSET -> StatusCode.UNSET
            OtelStatusCode.OK -> StatusCode.OK
            OtelStatusCode.ERROR -> StatusCode.ERROR
        }
        val message = status.description.takeIf { it.isNotBlank() }
        return SpanStatus(code, message)
    }

    private fun mapEvent(event: EventData): SpanEvent = SpanEvent(
        epochNanos = event.epochNanos,
        name = event.name,
        attributes = mapAttributes(event.attributes),
    )

    private fun mapScope(scope: InstrumentationScopeInfo): InstrumentationScope =
        InstrumentationScope(name = scope.name, version = scope.version)

    private fun mapAttributes(attrs: OtelAttributes): Attributes {
        if (attrs.isEmpty) return Attributes.EMPTY
        val map = LinkedHashMap<String, AttributeValue>(attrs.size())
        attrs.forEach { key, value -> map[key.key] = mapAttributeValue(key.type, value) }
        return Attributes(map)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapAttributeValue(type: AttributeType, value: Any): AttributeValue = when (type) {
        AttributeType.STRING -> AttributeValue.StringValue(value as String)
        AttributeType.BOOLEAN -> AttributeValue.BooleanValue(value as Boolean)
        AttributeType.LONG -> AttributeValue.LongValue(value as Long)
        AttributeType.DOUBLE -> AttributeValue.DoubleValue(value as Double)
        AttributeType.STRING_ARRAY -> AttributeValue.StringList(value as List<String>)
        AttributeType.BOOLEAN_ARRAY -> AttributeValue.BooleanList(value as List<Boolean>)
        AttributeType.LONG_ARRAY -> AttributeValue.LongList(value as List<Long>)
        AttributeType.DOUBLE_ARRAY -> AttributeValue.DoubleList(value as List<Double>)
    }
}
