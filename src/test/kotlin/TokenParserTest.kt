package io.github.larsw.parsekek

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

private val lexer = tokenize(keywords = setOf("let", "in"), symbols = setOf("+", "*", "(", ")", "="))

private fun lex(source: String): List<Token> = runOrThrow(lexer, source)

private fun op(symbol: String, f: (BigDecimal, BigDecimal) -> BigDecimal): TokenParser<(BigDecimal, BigDecimal) -> BigDecimal> =
    Tokens.symbol(symbol).map { f }

private val atom: TokenParser<BigDecimal> =
    Tokens.number or between(Tokens.symbol("("), Tokens.symbol(")"), lazyParser { arithmetic })

private val product: TokenParser<BigDecimal> = chainl1(atom, op("*", BigDecimal::times))

// Sums and products of numbers, with parentheses
private val arithmetic: TokenParser<BigDecimal> = chainl1(product, op("+", BigDecimal::plus))

class TokenParserTest : FunSpec({

    test("Tokens match one token each") {
        val tokens = lex("let x = 42")

        runParser(Tokens.keyword("let") skipL Tokens.identifier skipR Tokens.symbol("=") then Tokens.number, tokens) shouldBeRight
            ("x" to BigDecimal("42"))
    }

    test("Token parsers combine like text parsers") {
        runParser(arithmetic, lex("1 + 2 * (3 + 4)")) shouldBeRight BigDecimal("15")
    }

    test("the parser builder works on tokens") {
        val let: TokenParser<Pair<String, BigDecimal>> = parser {
            Tokens.keyword("let").bind()
            val name = Tokens.identifier.bind()
            Tokens.symbol("=").bind()
            name to arithmetic.bind()
        }

        runParser(let, lex("let total = 2 * 21")) shouldBeRight ("total" to BigDecimal("42"))
    }

    test("errors point into the source text and describe the token found") {
        val source = "1 + * 2"
        val error = runParser(arithmetic, lex(source)).shouldBeLeft()

        error.index shouldBe 4
        error.found shouldBe "\"*\""
        error.pretty(source) shouldBe """
            Parse error at line 1, column 5 (index 4)
            1 + * 2
                ^
            expected: number | "("
            found: "*"
        """.trimIndent()
    }

    test("an error after the last token points at its end") {
        val source = "1 +  "
        val error = runParser(arithmetic, lex(source)).shouldBeLeft()

        error.index shouldBe 3
        error.found shouldBe "end of input"
    }

    test("leftover tokens are an error") {
        val error = runParser(arithmetic, lex("1 2")).shouldBeLeft()

        error.index shouldBe 2
        error.expected shouldBe setOf("\"*\"", "\"+\"", "end of input")
        error.found shouldBe "number 2"
    }

    test("keyword and symbol parsers only match their own text") {
        val tokens = lex("in")
        runParser(Tokens.keyword("let"), tokens).shouldBeLeft().expected shouldBe setOf("\"let\"")
        runParser(Tokens.symbol("in"), tokens).shouldBeLeft().found shouldBe "\"in\""
        runParser(Tokens.identifier, tokens).shouldBeLeft().found shouldBe "\"in\""
    }

    test("notFollowedBy describes tokens readably") {
        val result = notFollowedBy(Tokens.identifier).parse(Input(lex("x")))

        result.shouldBeInstanceOf<ParseResult.Err>().error.message shouldBe "unexpected identifier \"x\""
    }

    test("token parses any element type") {
        val even: GenericParser<Int, Int> = token("even number") { n: Int -> n.takeIf { it % 2 == 0 } }
        val result = even.many().parse(Input(listOf(2, 4, 6, 7, 8)))

        result.shouldBeInstanceOf<ParseResult.Ok<Int, List<Int>>>()
        result.value shouldBe listOf(2, 4, 6)
        result.next.index shouldBe 3
        result.hint?.expected shouldBe setOf("even number")
    }

    test("text parsers also run on a list of characters") {
        val result = identifier.parse(Input("abc def".toList()))

        result.shouldBeInstanceOf<ParseResult.Ok<Char, String>>().value shouldBe "abc"
    }
})
