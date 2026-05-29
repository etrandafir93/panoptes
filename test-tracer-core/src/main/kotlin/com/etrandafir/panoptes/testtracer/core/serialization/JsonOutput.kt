package com.etrandafir.panoptes.testtracer.core.serialization

/**
 * Tiny manual JSON-writing helpers shared by the OTLP and Zipkin serializers.
 * Kept internal so we don't expose a public mini-JSON API.
 */

internal fun StringBuilder.appendStringField(key: String, value: String) {
    append('"').append(key).append("\":")
    appendJsonString(value)
}

internal fun StringBuilder.appendJsonString(s: String) {
    append('"')
    for (c in s) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c.code < 0x20 -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
    append('"')
}
