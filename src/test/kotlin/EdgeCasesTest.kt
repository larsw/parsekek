package io.github.larsw.parsekek

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal

class EdgeCasesTest : FunSpec({

    test("lexeme should skip trailing whitespace") {
        val parser = char('a').lexeme()

        val result = parser.parse(Input("a   b", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        result.value shouldBe 'a'
        result.next.index shouldBe 4 // consumed 'a' + 3 spaces
    }

    test("lexeme should work with complex parsers") {
        val parser = string("hello").lexeme()

        val result = parser.parse(Input("hello   world", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
        result.value shouldBe "hello"
        result.next.index shouldBe 8 // consumed "hello" + 3 spaces
    }

    test("trimLeft should consume leading whitespace") {
        val parser = char('a').trimLeft()

        val result = parser.parse(Input("   a", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        result.value shouldBe 'a'
        result.next.index shouldBe 4
    }

    test("identifier with underscores and numbers") {
        val result = identifier.parse(Input("_test_123_var", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
        result.value shouldBe "_test_123_var"
    }

    test("keyword boundary detection edge cases") {
        val ifParser = keyword("if")

        // Should succeed at end of input
        ifParser.parse(Input("if", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, String>>()

        // Should succeed before whitespace
        ifParser.parse(Input("if\t", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, String>>()

        // Should succeed before newline
        ifParser.parse(Input("if\n", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, String>>()

        // Should fail when followed by identifier char
        ifParser.parse(Input("ifx", 0)).shouldBeInstanceOf<ParseResult.Err>()
        ifParser.parse(Input("if_", 0)).shouldBeInstanceOf<ParseResult.Err>()
        ifParser.parse(Input("if1", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("double parser edge cases") {
        // Just a dot is not a number
        bigDecimal.parse(Input(".", 0)).shouldBeInstanceOf<ParseResult.Err>()

        // An incomplete exponent is left for whatever comes next
        runParser(bigDecimal skipR string("e-"), "1e-") shouldBeRight BigDecimal.ONE

        // Valid edge cases
        runParser(bigDecimal, ".5") shouldBeRight BigDecimal("0.5")
        runParser(bigDecimal skipR char('.'), "42.") shouldBeRight BigDecimal("42")
        runParser(bigDecimal, "1e5").shouldBeRight().compareTo(BigDecimal(100000)) shouldBe 0
    }

    test("sign parser coverage") {
        val positiveSign = sign.parse(Input("+", 0))
        positiveSign.shouldBeInstanceOf<ParseResult.Ok<Char, Int>>()
        positiveSign.value shouldBe 1

        val negativeSign = sign.parse(Input("-", 0))
        negativeSign.shouldBeInstanceOf<ParseResult.Ok<Char, Int>>()
        negativeSign.value shouldBe -1

        val noSign = sign.parse(Input("5", 0))
        noSign.shouldBeInstanceOf<ParseResult.Ok<Char, Int>>()
        noSign.value shouldBe 1
    }

    test("spaces1 should require at least one whitespace") {
        val success = spaces1.parse(Input("   hello", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
        success.value shouldBe "   "

        val failure = spaces1.parse(Input("hello", 0))
        failure.shouldBeInstanceOf<ParseResult.Err>()
    }

    test("anyChar parser should accept any character") {
        anyChar.parse(Input("a", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        anyChar.parse(Input("1", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        anyChar.parse(Input("@", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        anyChar.parse(Input("", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("complex nested expressions") {
        eval(runOrThrow(expression, "1 + 2 * 3 - 4 / 2")) shouldBe 5.0 // 1 + 6 - 2 = 5
        eval(runOrThrow(expression, "(1 + 2) * (3 - 4) / 2")) shouldBe -1.5 // 3 * (-1) / 2 = -1.5
    }

    test("error message merging with different indices") {
        val parser1 = string("abc")
        val parser2 = string("def")
        val combined = parser1 or parser2

        val result = combined.parse(Input("xyz", 0))
        result.shouldBeInstanceOf<ParseResult.Err>()
        result.error.expected shouldBe setOf("\"abc\"", "\"def\"")
    }

    test("consumed flag propagation") {
        val parser1 = char('a') then char('b') // consumes "a" before failing on "ac"
        val parser2 = string("ac")
        val combined = parser1 or parser2

        val result = combined.parse(Input("ac", 0))
        result.shouldBeInstanceOf<ParseResult.Err>()
        result.consumed shouldBe true // first parser consumed input
    }

    test("tokenize with empty keywords and symbols should still parse identifiers and numbers") {
        val parser = tokenize()

        // Should succeed with empty input
        runParser(parser, "   ") shouldBeRight emptyList()

        // Identifiers and numbers are always recognized
        runParser(parser, "hello 42") shouldBeRight listOf(
            Token.Identifier("hello", Span(0, 5)),
            Token.Number(BigDecimal("42"), Span(6, 8)),
        )
    }

    test("safe integer parsing") {
        runParser(int, "123456789") shouldBeRight 123456789
        runParser(int, "-123456789") shouldBeRight -123456789
    }

    test("expression parser with deeply nested parentheses") {
        eval(runOrThrow(expression, "((((1))))")) shouldBe 1.0
    }

    test("error messages with context") {
        val error = ParseError(5, setOf("number"), "custom error")
        val source = "hello world test"
        val pretty = error.pretty(source)

        pretty shouldContain "line 1, column 6"
        pretty shouldContain "hello world test"
        pretty.lines()[2] shouldBe "     ^"
        pretty shouldContain "expected: number"
        pretty shouldContain "message: custom error"
    }
})
