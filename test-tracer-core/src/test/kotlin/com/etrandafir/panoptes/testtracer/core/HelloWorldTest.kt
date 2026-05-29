package com.etrandafir.panoptes.testtracer.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class HelloWorldTest : StringSpec({
    "module wires up the Kotest runner" {
        (1 + 1) shouldBe 2
    }
})
