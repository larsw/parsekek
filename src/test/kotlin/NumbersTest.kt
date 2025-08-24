package io.github.larsw.parsekek

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

class NumbersTest : FunSpec({

    test("unsignedInt should parse positive integers") {
        val result = unsignedInt.parse(Input("123abc", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<Long>>()
        result.value shouldBe 123L
        result.next.index shouldBe 3
    }

    test("unsignedInt should fail on non-digits") {
        val result = unsignedInt.parse(Input("abc", 0))
        result.shouldBeInstanceOf<ParseResult.Err>()
    }

    test("int should parse signed integers") {
        val positive = int.parse(Input("123", 0))
        positive.shouldBeInstanceOf<ParseResult.Ok<Long>>()
        positive.value shouldBe 123L

        val negative = int.parse(Input("-456", 0))
        negative.shouldBeInstanceOf<ParseResult.Ok<Long>>()
        negative.value shouldBe -456L

        val explicitPositive = int.parse(Input("+789", 0))
        explicitPositive.shouldBeInstanceOf<ParseResult.Ok<Long>>()
        explicitPositive.value shouldBe 789L
    }

    test("int property test with safe range") {
        checkAll(Arb.long(-1000000L..1000000L)) { num ->
            val result = int.parse(Input(num.toString(), 0))
            result.shouldBeInstanceOf<ParseResult.Ok<Long>>()
            result.value shouldBe num
        }
    }

    test("double should parse floating point numbers") {
        val simple = bigDecimal.parse(Input("123.456", 0))
        simple.shouldBeInstanceOf<ParseResult.Ok<Double>>()
        simple.value shouldBe 123.456

        val noIntPart = bigDecimal.parse(Input(".456", 0))
        noIntPart.shouldBeInstanceOf<ParseResult.Ok<Double>>()
        noIntPart.value shouldBe 0.456

        val noFracPart = bigDecimal.parse(Input("123.", 0))
        noFracPart.shouldBeInstanceOf<ParseResult.Ok<Double>>()
        noFracPart.value shouldBe 123.0
    }

    test("double should parse scientific notation") {
        val withE = bigDecimal.parse(Input("1.5e10", 0))
        withE.shouldBeInstanceOf<ParseResult.Ok<Double>>()
        withE.value shouldBe 1.5e10

        val withCapitalE = bigDecimal.parse(Input("2.5E-3", 0))
        withCapitalE.shouldBeInstanceOf<ParseResult.Ok<Double>>()
        withCapitalE.value shouldBe 2.5E-3

        val withPositiveExp = bigDecimal.parse(Input("1e+5", 0))
        withPositiveExp.shouldBeInstanceOf<ParseResult.Ok<Double>>()
        withPositiveExp.value shouldBe 1e+5
    }

    test("double should handle signed numbers") {
        val negative = bigDecimal.parse(Input("-123.456", 0))
        negative.shouldBeInstanceOf<ParseResult.Ok<Double>>()
        negative.value shouldBe -123.456

        val positive = bigDecimal.parse(Input("+123.456", 0))
        positive.shouldBeInstanceOf<ParseResult.Ok<Double>>()
        positive.value shouldBe 123.456
    }

    test("double should fail on invalid input") {
        bigDecimal.parse(Input("abc", 0)).shouldBeInstanceOf<ParseResult.Err>()
        bigDecimal.parse(Input(".", 0)).shouldBeInstanceOf<ParseResult.Err>()
        bigDecimal.parse(Input("1e", 0)).shouldBeInstanceOf<ParseResult.Err>()
        bigDecimal.parse(Input("1e+", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("double property test with valid doubles") {
        checkAll(Arb.double(-1000.0..1000.0)) { num ->
            if (num.isFinite()) {
                val result = bigDecimal.parse(Input(num.toString(), 0))
                result.shouldBeInstanceOf<ParseResult.Ok<Double>>()
                result.value shouldBe num
            }
        }
    }
})
