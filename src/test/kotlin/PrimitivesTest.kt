package io.github.larsw.parsekek

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

class PrimitivesTest : FunSpec({

    test("satisfy should parse matching character") {
        val digitParser = satisfy("digit") { it in '0'..'9' }
        val result = digitParser.parse(Input("5abc", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<Char>>()
        result.value shouldBe '5'
        result.next.index shouldBe 1
    }

    test("satisfy should fail on non-matching character") {
        val digitParser = satisfy("digit") { it in '0'..'9' }
        val result = digitParser.parse(Input("abc", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.error.expected shouldBe setOf("digit")
        result.consumed shouldBe false
    }

    test("satisfy should fail at EOF") {
        val anyParser = satisfy("any") { true }
        val result = anyParser.parse(Input("", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.error.expected shouldBe setOf("any")
    }

    test("char parser should match specific character") {
        val aParser = char('a')

        aParser.parse(Input("abc", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        aParser.parse(Input("bac", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("char parser property test") {
        checkAll(Arb.char()) { c ->
            val parser = char(c)
            val result = parser.parse(Input(c.toString(), 0))
            result.shouldBeInstanceOf<ParseResult.Ok<Char>>()
            result.value shouldBe c
        }
    }

    test("string parser should match literal string") {
        val helloParser = string("hello")

        val success = helloParser.parse(Input("hello world", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<String>>()
        success.value shouldBe "hello"
        success.next.index shouldBe 5

        val failure = helloParser.parse(Input("help", 0))
        failure.shouldBeInstanceOf<ParseResult.Err>()
        failure.consumed shouldBe true // consumed "hel" before failing
    }

    test("string parser should handle empty string") {
        val emptyParser = string("")
        val result = emptyParser.parse(Input("anything", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<String>>()
        result.value shouldBe ""
        result.next.index shouldBe 0
    }

    test("oneOf should match any character in set") {
        val vowelParser = oneOf("aeiou")

        vowelParser.parse(Input("a", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        vowelParser.parse(Input("e", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        vowelParser.parse(Input("b", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("noneOf should reject characters in set") {
        val consonantParser = noneOf("aeiou")

        consonantParser.parse(Input("b", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        consonantParser.parse(Input("a", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("digit parser should match digits") {
        digit.parse(Input("5", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        digit.parse(Input("a", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("letter parser should match letters") {
        letter.parse(Input("a", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        letter.parse(Input("Z", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        letter.parse(Input("5", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("whitespace parser should match whitespace") {
        whitespace.parse(Input(" ", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        whitespace.parse(Input("\t", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        whitespace.parse(Input("\n", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        whitespace.parse(Input("a", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("spaces parser should consume whitespace") {
        val result = spaces.parse(Input("   hello", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<String>>()
        result.value shouldBe "   "
        result.next.index shouldBe 3
    }

    test("spaces parser should succeed with no whitespace") {
        val result = spaces.parse(Input("hello", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<String>>()
        result.value shouldBe ""
        result.next.index shouldBe 0
    }

    test("eof parser should succeed at end of input") {
        eof.parse(Input("", 0)).shouldBeInstanceOf<ParseResult.Ok<Unit>>()
        eof.parse(Input("a", 1)).shouldBeInstanceOf<ParseResult.Ok<Unit>>()
        eof.parse(Input("a", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }
})
