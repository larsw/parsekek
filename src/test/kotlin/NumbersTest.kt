package io.github.larsw.parsekek

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.math.BigInteger

class NumbersTest : FunSpec({

    test("unsignedInt should parse positive integers") {
        val result = unsignedInt.parse(Input("123abc", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<Char, Int>>()
        result.value shouldBe 123
        result.next.index shouldBe 3
    }

    test("unsignedInt should fail on non-digits and signs") {
        unsignedInt.parse(Input("abc", 0)).shouldBeInstanceOf<ParseResult.Err>()
        unsignedInt.parse(Input("-1", 0)).shouldBeInstanceOf<ParseResult.Err>().consumed shouldBe false
    }

    test("int should parse signed integers") {
        runParser(int, "123") shouldBeRight 123
        runParser(int, "-456") shouldBeRight -456
        runParser(int, "+789") shouldBeRight 789
        runParser(int, "-2147483648") shouldBeRight Int.MIN_VALUE
    }

    test("int property test") {
        checkAll(Arb.int()) { num ->
            runParser(int, num.toString()) shouldBeRight num
        }
    }

    test("int reports overflow as a parse error instead of wrapping or throwing") {
        for (text in listOf("2147483648", "4294967295", "-2147483649", "99999999999")) {
            val error = runParser(int, text).shouldBeLeft()
            error.index shouldBe 0
            error.message shouldBe "$text is out of range"
        }
    }

    test("a lone sign is not a number and consumes nothing") {
        val result = int.parse(Input("-x", 0))
        result.shouldBeInstanceOf<ParseResult.Err>()
        result.consumed shouldBe false

        runParser(bigDecimal or string("-").map { BigDecimal.ZERO }, "-") shouldBeRight BigDecimal.ZERO
    }

    test("long and unsignedLong cover the Long range") {
        runParser(long, Long.MIN_VALUE.toString()) shouldBeRight Long.MIN_VALUE
        runParser(unsignedLong, Long.MAX_VALUE.toString()) shouldBeRight Long.MAX_VALUE
        runParser(unsignedLong, "18446744073709551615").shouldBeLeft().message shouldBe
            "18446744073709551615 is out of range"
    }

    test("bigInteger has no range limit") {
        runParser(bigInteger, "-123456789012345678901234567890") shouldBeRight
            BigInteger("-123456789012345678901234567890")
        runParser(unsignedBigInteger, "123456789012345678901234567890") shouldBeRight
            BigInteger("123456789012345678901234567890")
    }

    test("bigDecimal should parse decimal numbers exactly") {
        runParser(bigDecimal, "123.456") shouldBeRight BigDecimal("123.456")
        runParser(bigDecimal, ".456") shouldBeRight BigDecimal("0.456")
        runParser(bigDecimal, "-123.456") shouldBeRight BigDecimal("-123.456")
        runParser(bigDecimal, "+123.456") shouldBeRight BigDecimal("123.456")
    }

    test("bigDecimal should parse scientific notation") {
        runParser(bigDecimal, "1.5e10") shouldBeRight BigDecimal("1.5e10")
        runParser(bigDecimal, "2.5E-3") shouldBeRight BigDecimal("2.5E-3")
        runParser(bigDecimal, "1e+5") shouldBeRight BigDecimal("1e+5")
    }

    test("a '.' or exponent without digits is not part of the number") {
        for ((text, end) in listOf("123." to 3, "1e" to 1, "1e+" to 1, "2em" to 1)) {
            val result = bigDecimal.parse(Input(text, 0))
            result.shouldBeInstanceOf<ParseResult.Ok<Char, BigDecimal>>()
            result.next.index shouldBe end
        }
    }

    test("bigDecimal should fail on input without digits") {
        bigDecimal.parse(Input("abc", 0)).shouldBeInstanceOf<ParseResult.Err>()
        bigDecimal.parse(Input(".", 0)).shouldBeInstanceOf<ParseResult.Err>().consumed shouldBe false
        bigDecimal.parse(Input("-", 0)).shouldBeInstanceOf<ParseResult.Err>().consumed shouldBe false
    }

    test("double should parse floating point numbers") {
        runParser(double, "123.456") shouldBeRight 123.456
        runParser(double, "-1.5e10") shouldBeRight -1.5e10
        runParser(unsignedDouble, "2.5E-3") shouldBeRight 2.5E-3
    }

    test("double reports values too large for a Double instead of returning Infinity") {
        runParser(double, "1e400").shouldBeLeft().message shouldBe "1e400 is out of range"
    }

    test("double property test with valid doubles") {
        checkAll(Arb.double(-1000.0..1000.0)) { num ->
            if (num.isFinite()) {
                runParser(double, num.toString()) shouldBeRight num
            }
        }
    }
})
