package io.github.larsw.parsekek

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

class ParseErrorTest : FunSpec({

    test("ParseError should store error information correctly") {
        val error = ParseError(5, setOf("digit", "letter"), "custom message")
        error.index shouldBe 5
        error.expected shouldBe setOf("digit", "letter")
        error.message shouldBe "custom message"
    }

    test("ParseError.expected factory method") {
        val error = ParseError.expected(10, "number", "identifier")
        error.index shouldBe 10
        error.expected shouldBe setOf("number", "identifier")
        error.message shouldBe null
    }

    test("ParseError.pretty should format error message correctly") {
        val source = "hello\nworld\ntest"
        val error = ParseError(8, setOf("digit"), "expected number")
        val pretty = error.pretty(source)

        pretty shouldContain "line 2, column 3"
        pretty shouldContain "world"
        pretty shouldContain "expected: digit"
        pretty shouldContain "message: expected number"
    }

    test("ParseError.pretty with no expectations") {
        val source = "test"
        val error = ParseError(2, emptySet(), null)
        val pretty = error.pretty(source)

        pretty shouldContain "line 1, column 3"
        pretty shouldContain "test"
    }

    test("ParseError.pretty handles edge cases") {
        // Error at start of input
        val error1 = ParseError(0, setOf("start"), null)
        val pretty1 = error1.pretty("hello")
        pretty1 shouldContain "line 1, column 1"

        // Error beyond input length
        val error2 = ParseError(10, setOf("EOF"), null)
        val pretty2 = error2.pretty("short")
        pretty2 shouldContain "line 1, column 6"
    }

    test("ParseError.pretty property test") {
        checkAll(Arb.string(0..100), Arb.int(0..50)) { source, index ->
            val error = ParseError(index, setOf("test"), null)
            val pretty = error.pretty(source)
            pretty shouldContain "Parse error at line"
        }
    }
})
