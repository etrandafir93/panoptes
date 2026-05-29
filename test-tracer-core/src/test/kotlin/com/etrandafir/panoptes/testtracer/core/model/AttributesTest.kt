package com.etrandafir.panoptes.testtracer.core.model

import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.BooleanValue
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.LongValue
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.StringValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AttributesTest : StringSpec({

    "EMPTY has zero entries" {
        Attributes.EMPTY.isEmpty() shouldBe true
        Attributes.EMPTY.size() shouldBe 0
    }

    "of(pairs) builds an Attributes" {
        val attrs = Attributes.of(
            "http.method" to StringValue("GET"),
            "http.status_code" to LongValue(200),
            "http.cached" to BooleanValue(true),
        )
        attrs.size() shouldBe 3
        attrs["http.method"] shouldBe StringValue("GET")
        attrs["http.status_code"] shouldBe LongValue(200)
        attrs["http.cached"] shouldBe BooleanValue(true)
    }

    "get returns null for missing key" {
        Attributes.EMPTY["nope"].shouldBeNull()
    }
})
