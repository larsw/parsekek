package io.github.larsw.parsekek

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class LexerTest : FunSpec({

    test("Span should track source positions") {
        val span = Span(5, 10)
        span.start shouldBe 5
        span.end shouldBe 10
    }

    test("identifier should parse valid identifiers") {
        runParser(identifier, "hello_world") shouldBeRight "hello_world"
        runParser(identifier, "_private") shouldBeRight "_private"
        runParser(identifier, "test123") shouldBeRight "test123"
    }

    test("identifier should fail on invalid starts") {
        identifier.parse(Input("123abc", 0)).shouldBeInstanceOf<ParseResult.Err>().error.expected shouldBe setOf("identifier")
        identifier.parse(Input("-test", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("identifier does not skip whitespace by itself") {
        val result = identifier.parse(Input("abc  ", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
        result.next.index shouldBe 3
    }

    test("keyword should match exact keywords with boundaries") {
        val ifParser = keyword("if")

        val success = ifParser.parse(Input("if ", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
        success.value shouldBe "if"
        success.next.index shouldBe 2

        val failure = ifParser.parse(Input("ifx", 0))
        failure.shouldBeInstanceOf<ParseResult.Err>()
        failure.consumed shouldBe false
        failure.error.index shouldBe 0

        ifParser.parse(Input("if", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
    }

    test("keywords sharing a prefix work in either order") {
        runParser(keyword("in") or keyword("int"), "int") shouldBeRight "int"
        runParser(keyword("else") or keyword("elif"), "elif") shouldBeRight "elif"
    }

    test("tokenize should parse complete token streams with exact spans") {
        val parser = tokenize(
            keywords = setOf("if", "else"),
            symbols = setOf("+", "=", "==")
        )

        runParser(parser, "if x == 5 + 1") shouldBeRight listOf(
            Token.Keyword("if", Span(0, 2)),
            Token.Identifier("x", Span(3, 4)),
            Token.Symbol("==", Span(5, 7)),
            Token.Number(BigDecimal("5"), Span(8, 9)),
            Token.Symbol("+", Span(10, 11)),
            Token.Number(BigDecimal("1"), Span(12, 13)),
        )
    }

    test("tokenize spans exclude surrounding whitespace") {
        val tokens = runParser(tokenize(symbols = setOf("+")), "  x  +  1  ").shouldBeRight()
        tokens.map { it.span } shouldBe listOf(Span(2, 3), Span(5, 6), Span(8, 9))
    }

    test("tokenize prefers whole-word keywords over identifiers") {
        val tokens = runParser(tokenize(keywords = setOf("if", "in", "int")), "if iffy in int i").shouldBeRight()
        tokens.map { it::class.simpleName } shouldBe listOf("Keyword", "Identifier", "Keyword", "Keyword", "Identifier")
    }

    test("tokenize keeps signs out of numbers") {
        val tokens = runParser(tokenize(symbols = setOf("-", "+")), "x-1 + -2").shouldBeRight()
        tokens.map {
            when (it) {
                is Token.Identifier -> it.name
                is Token.Number -> it.value.toPlainString()
                is Token.Symbol -> it.text
                is Token.Keyword -> it.text
            }
        } shouldBe listOf("x", "-", "1", "+", "-", "2")
    }

    test("tokenize matches the longest symbol") {
        val tokens = runParser(tokenize(symbols = setOf("+", "++", "+=")), "++ += +").shouldBeRight()
        tokens.map { (it as Token.Symbol).text } shouldBe listOf("++", "+=", "+")
    }

    test("tokenize keeps number literals exact") {
        val tokens = runParser(tokenize(), "99999999999999999999 45.67 1.23e4").shouldBeRight()
        tokens.map { (it as Token.Number).value } shouldBe
            listOf(BigDecimal("99999999999999999999"), BigDecimal("45.67"), BigDecimal("1.23e4"))
    }

    test("tokenize should handle empty input") {
        runParser(tokenize(), "   ") shouldBeRight emptyList()
    }

    test("tokenize should fail on unexpected characters") {
        val error = runParser(tokenize(symbols = setOf("+", "-")), "a @").shouldBeLeft()
        error.index shouldBe 2
        error.expected shouldBe setOf("token", "end of input")
    }
})
