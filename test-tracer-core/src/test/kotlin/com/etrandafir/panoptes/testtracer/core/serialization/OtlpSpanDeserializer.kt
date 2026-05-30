package com.etrandafir.panoptes.testtracer.core.serialization

import com.etrandafir.panoptes.testtracer.core.model.AttributeValue
import com.etrandafir.panoptes.testtracer.core.model.Attributes
import com.etrandafir.panoptes.testtracer.core.model.InstrumentationScope
import com.etrandafir.panoptes.testtracer.core.model.Resource
import com.etrandafir.panoptes.testtracer.core.model.Span
import com.etrandafir.panoptes.testtracer.core.model.SpanEvent
import com.etrandafir.panoptes.testtracer.core.model.SpanKind
import com.etrandafir.panoptes.testtracer.core.model.SpanStatus
import com.etrandafir.panoptes.testtracer.core.model.StatusCode
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Test-only inverse of [OtlpJsonSerializer]. Lives in test sources so we don't
 * leak a Jackson dependency onto the user classpath.
 */
object OtlpSpanDeserializer {

    private val mapper = ObjectMapper()

    fun deserialize(line: String): Span {
        val n = mapper.readTree(line)
        return Span(
            traceId = n["traceId"].asText(),
            spanId = n["spanId"].asText(),
            parentSpanId = n["parentSpanId"]?.asText(),
            name = n["name"].asText(),
            kind = SpanKind.valueOf(n["kind"].asText().removePrefix("SPAN_KIND_")),
            startEpochNanos = n["startTimeUnixNano"].asText().toLong(),
            endEpochNanos = n["endTimeUnixNano"].asText().toLong(),
            status = readStatus(n["status"]),
            attributes = readAttributes(n["attributes"]),
            events = readEvents(n["events"]),
            resource = readResource(n["resource"]),
            scope = readScope(n["scope"]),
        )
    }

    private fun readStatus(n: JsonNode): SpanStatus = SpanStatus(
        code = StatusCode.valueOf(n["code"].asText().removePrefix("STATUS_CODE_")),
        description = n["message"]?.asText(),
    )

    private fun readAttributes(n: JsonNode): Attributes {
        if (n.isEmpty) return Attributes.EMPTY
        val pairs = (0 until n.size()).map { i ->
            val entry = n[i]
            entry["key"].asText() to readAttributeValue(entry["value"])
        }
        return Attributes(pairs.toMap())
    }

    private fun readAttributeValue(v: JsonNode): AttributeValue = when {
        v.has("stringValue") -> AttributeValue.StringValue(v["stringValue"].asText())
        v.has("boolValue") -> AttributeValue.BooleanValue(v["boolValue"].asBoolean())
        v.has("intValue") -> AttributeValue.LongValue(v["intValue"].asText().toLong())
        v.has("doubleValue") -> AttributeValue.DoubleValue(v["doubleValue"].asDouble())
        v.has("arrayValue") -> readArrayValue(v["arrayValue"]["values"])
        else -> error("unknown attribute value shape: $v")
    }

    private fun readArrayValue(values: JsonNode): AttributeValue {
        if (values.isEmpty) return AttributeValue.StringList(emptyList())
        val first = values[0]
        return when {
            first.has("stringValue") -> AttributeValue.StringList(values.map { it["stringValue"].asText() })
            first.has("boolValue") -> AttributeValue.BooleanList(values.map { it["boolValue"].asBoolean() })
            first.has("intValue") -> AttributeValue.LongList(values.map { it["intValue"].asText().toLong() })
            first.has("doubleValue") -> AttributeValue.DoubleList(values.map { it["doubleValue"].asDouble() })
            else -> error("unknown array element shape: $first")
        }
    }

    private fun readEvents(n: JsonNode): List<SpanEvent> =
        (0 until n.size()).map { i ->
            val e = n[i]
            SpanEvent(
                epochNanos = e["timeUnixNano"].asText().toLong(),
                name = e["name"].asText(),
                attributes = readAttributes(e["attributes"]),
            )
        }

    private fun readResource(n: JsonNode): Resource = Resource(
        attributes = readAttributes(n["attributes"]),
        schemaUrl = n["schemaUrl"]?.asText(),
    )

    private fun readScope(n: JsonNode): InstrumentationScope = InstrumentationScope(
        name = n["name"].asText(),
        version = n["version"]?.asText(),
    )
}
