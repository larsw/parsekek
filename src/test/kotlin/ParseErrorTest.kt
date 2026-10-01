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

    test("ParseError.merge keeps the furthest error and joins expectations at the same index") {
        val a = ParseError.expected(3, "digit")
        val b = ParseError.expected(3, "letter")
        val further = ParseError.expected(5, "end of input")

        a.merge(b) shouldBe ParseError.expected(3, "digit", "letter")
        a.merge(further) shouldBe further
        further.merge(a) shouldBe further
    }

    test("ParseError.pretty should format error message correctly") {
        val source = "hello\nworld\ntest"
        val error = ParseError(8, setOf("digit"), "expected number")

        error.pretty(source) shouldBe """
            Parse error at line 2, column 3 (index 8)
            world
              ^
            expected: digit
            found: 'r'
            message: expected number
        """.trimIndent()
    }

    test("ParseError.pretty with no expectations") {
        val pretty = ParseError(2, emptySet(), null).pretty("test")

        pretty shouldContain "line 1, column 3"
        pretty shouldContain "test"
        pretty.contains("expected:") shouldBe false
    }

    test("ParseError.pretty handles edge cases") {
        ParseError(0, setOf("start"), null).pretty("hello") shouldContain "line 1, column 1"

        val beyondEnd = ParseError(10, setOf("end of input"), null).pretty("short")
        beyondEnd shouldContain "line 1, column 6"
        beyondEnd shouldContain "found: end of input"
    }

    test("ParseError.pretty keeps tabs so the caret lines up") {
        val pretty = ParseError(2, setOf("digit"), null).pretty("\t\tx")
        pretty.lines()[2] shouldBe "\t\t^"
    }

    test("ParseError.pretty uses the same line breaks as Input.position") {
        val pretty = ParseError(3, setOf("digit"), null).pretty("a\rbcd")
        pretty.lines()[0] shouldBe "Parse error at line 2, column 2 (index 3)"
        pretty.lines()[1] shouldBe "bcd"
        pretty.lines()[2] shouldBe " ^"
    }

    test("ParseError.pretty property test") {
        checkAll(Arb.string(0..100), Arb.int(0..50)) { source, index ->
            val error = ParseError(index, setOf("test"), null)
            val pretty = error.pretty(source)
            pretty shouldContain "Parse error at line"
        }
    }
})
