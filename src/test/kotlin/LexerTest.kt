package io.github.larsw.parsekek

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
        val result1 = identifier.parse(Input("hello_world", 0))
        result1.shouldBeInstanceOf<ParseResult.Ok<String>>()
        result1.value shouldBe "hello_world"

        val result2 = identifier.parse(Input("_private", 0))
        result2.shouldBeInstanceOf<ParseResult.Ok<String>>()
        result2.value shouldBe "_private"

        val result3 = identifier.parse(Input("test123", 0))
        result3.shouldBeInstanceOf<ParseResult.Ok<String>>()
        result3.value shouldBe "test123"
    }

    test("identifier should fail on invalid starts") {
        identifier.parse(Input("123abc", 0)).shouldBeInstanceOf<ParseResult.Err>()
        identifier.parse(Input("-test", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("keyword should match exact keywords with boundaries") {
        val ifParser = keyword("if")

        val success = ifParser.parse(Input("if ", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<String>>()
        success.value shouldBe "if"

        val failure = ifParser.parse(Input("ifx", 0))
        failure.shouldBeInstanceOf<ParseResult.Err>()

        val atEnd = ifParser.parse(Input("if", 0))
        atEnd.shouldBeInstanceOf<ParseResult.Ok<String>>()
    }

    test("identTok should create identifier tokens") {
        val result = identTok.parse(Input("hello", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Tok.Ident>>()
        result.value.name shouldBe "hello"
        result.value.span.start shouldBe 0
        result.value.span.end shouldBe 5
    }

    test("numberTok should create number tokens") {
        val intResult = numberTok.parse(Input("123", 0))
        intResult.shouldBeInstanceOf<ParseResult.Ok<Tok.Num>>()
        intResult.value.value shouldBe BigDecimal.valueOf(123)

        val doubleResult = numberTok.parse(Input("45.67", 0))
        doubleResult.shouldBeInstanceOf<ParseResult.Ok<Tok.Num>>()
        doubleResult.value.value shouldBe BigDecimal.valueOf(45.67)
    }

    test("keywordTok should create keyword tokens") {
        val parser = keywordTok("if", "else", "while")

        val ifResult = parser.parse(Input("if", 0))
        ifResult.shouldBeInstanceOf<ParseResult.Ok<Tok.Kw>>()
        ifResult.value.kw shouldBe "if"

        val elseResult = parser.parse(Input("else", 0))
        elseResult.shouldBeInstanceOf<ParseResult.Ok<Tok.Kw>>()
        elseResult.value.kw shouldBe "else"

        parser.parse(Input("other", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("symbols should parse symbol tokens with longest match") {
        val parser = symbols("++", "+", "+=")

        val increment = parser.parse(Input("++", 0))
        increment.shouldBeInstanceOf<ParseResult.Ok<Tok.Sym>>()
        increment.value.sym shouldBe "++"

        val assign = parser.parse(Input("+=", 0))
        assign.shouldBeInstanceOf<ParseResult.Ok<Tok.Sym>>()
        assign.value.sym shouldBe "+="

        val plus = parser.parse(Input("+", 0))
        plus.shouldBeInstanceOf<ParseResult.Ok<Tok.Sym>>()
        plus.value.sym shouldBe "+"
    }

    test("tokenize should parse complete token streams") {
        val parser = tokenize(
            keywords = setOf("if", "else"),
            symbols = setOf("+", "=", "==")
        )

        val result = parser.parse(Input("if x == 5 + 1", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<List<Tok>>>()

        val tokens = result.value
        tokens.size shouldBe 6

        tokens[0].shouldBeInstanceOf<Tok.Kw>()
        (tokens[0] as Tok.Kw).kw shouldBe "if"

        tokens[1].shouldBeInstanceOf<Tok.Ident>()
        (tokens[1] as Tok.Ident).name shouldBe "x"

        tokens[2].shouldBeInstanceOf<Tok.Sym>()
        (tokens[2] as Tok.Sym).sym shouldBe "=="

        tokens[3].shouldBeInstanceOf<Tok.Num>()
        (tokens[3] as Tok.Num).value shouldBe BigDecimal.valueOf(5)

        tokens[4].shouldBeInstanceOf<Tok.Sym>()
        (tokens[4] as Tok.Sym).sym shouldBe "+"

        tokens[5].shouldBeInstanceOf<Tok.Num>()
        (tokens[5] as Tok.Num).value shouldBe BigDecimal.valueOf(1)
    }

    test("tokenize should handle empty input") {
        val parser = tokenize()
        val result = parser.parse(Input("   ", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<List<Tok>>>()
        result.value shouldBe emptyList()
    }

    test("tokenize should fail on unexpected characters") {
        val parser = tokenize(symbols = setOf("+", "-"))
        val result = parser.parse(Input("@", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
    }

    test("numberTok should handle both integers and decimals") {
        val intResult = numberTok.parse(Input("123", 0))
        intResult.shouldBeInstanceOf<ParseResult.Ok<Tok.Num>>()
        intResult.value.value shouldBe BigDecimal.valueOf(123)
        intResult.value.span.start shouldBe 0
        intResult.value.span.end shouldBe 3

        val negativeResult = numberTok.parse(Input("-456", 0))
        negativeResult.shouldBeInstanceOf<ParseResult.Ok<Tok.Num>>()
        negativeResult.value.value shouldBe BigDecimal.valueOf(-456)

        val decimalResult = numberTok.parse(Input("45.67", 0))
        decimalResult.shouldBeInstanceOf<ParseResult.Ok<Tok.Num>>()
        decimalResult.value.value shouldBe BigDecimal.valueOf(45.67)
        decimalResult.value.span.start shouldBe 0
        decimalResult.value.span.end shouldBe 5

        val scientificResult = numberTok.parse(Input("1.23e4", 0))
        scientificResult.shouldBeInstanceOf<ParseResult.Ok<Tok.Num>>()
        scientificResult.value.value shouldBe BigDecimal.valueOf(1.23e4)
    }
})
