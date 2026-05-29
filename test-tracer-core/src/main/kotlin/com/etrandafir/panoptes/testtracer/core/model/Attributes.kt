package com.etrandafir.panoptes.testtracer.core.model

@JvmInline
value class Attributes(val values: Map<String, AttributeValue>) {
    operator fun get(key: String): AttributeValue? = values[key]
    fun isEmpty(): Boolean = values.isEmpty()
    fun size(): Int = values.size

    companion object {
        val EMPTY = Attributes(emptyMap())

        fun of(vararg pairs: Pair<String, AttributeValue>): Attributes =
            Attributes(pairs.toMap())
    }
}

sealed interface AttributeValue {
    data class StringValue(val value: String) : AttributeValue
    data class BooleanValue(val value: Boolean) : AttributeValue
    data class LongValue(val value: Long) : AttributeValue
    data class DoubleValue(val value: Double) : AttributeValue
    data class StringList(val values: List<String>) : AttributeValue
    data class BooleanList(val values: List<Boolean>) : AttributeValue
    data class LongList(val values: List<Long>) : AttributeValue
    data class DoubleList(val values: List<Double>) : AttributeValue
}
