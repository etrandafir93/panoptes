package com.etrandafir.panoptes.testtracer.core.model

data class Resource(
    val attributes: Attributes,
    val schemaUrl: String? = null,
) {
    companion object {
        val EMPTY = Resource(Attributes.EMPTY)
    }
}

data class InstrumentationScope(
    val name: String,
    val version: String? = null,
) {
    companion object {
        val UNKNOWN = InstrumentationScope(name = "unknown")
    }
}
