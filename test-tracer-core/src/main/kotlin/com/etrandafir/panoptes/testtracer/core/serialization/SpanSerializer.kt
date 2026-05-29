package com.etrandafir.panoptes.testtracer.core.serialization

import com.etrandafir.panoptes.testtracer.core.model.Span

/**
 * Converts a [Span] to one line of JSON.
 *
 * Implementations must return a single line (no embedded LF/CR) so the
 * output composes with `NdjsonSpanWriter`.
 */
interface SpanSerializer {
    fun serialize(span: Span): String
}
