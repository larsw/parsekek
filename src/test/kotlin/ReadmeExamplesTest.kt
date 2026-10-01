package io.github.larsw.parsekek.readme

// The examples from README.md, kept here so they are compiled and checked. If you change one,
// change the other.

import arrow.core.right
import io.github.larsw.parsekek.*
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.StringReader
import java.math.BigDecimal

// Parser builder

data class Assignment(val name: String, val value: Int)

val assignment: Parser<Assignment> = parser {
    val name = identifier.lexeme().bind()
    char('=').lexeme().bind()
    Assignment(name, int.lexeme().bind())
}

// Parsing tokens

val lexer = tokenize(keywords = setOf("let"), symbols = setOf("+", "*", "(", ")", "="))

fun op(symbol: String, f: (BigDecimal, BigDecimal) -> BigDecimal): TokenParser<(BigDecimal, BigDecimal) -> BigDecimal> =
    Tokens.symbol(symbol).map { f }

val atom: TokenParser<BigDecimal> =
    Tokens.number or between(Tokens.symbol("("), Tokens.symbol(")"), lazyParser { sum })
val product: TokenParser<BigDecimal> = chainl1(atom, op("*", BigDecimal::times))
val sum: TokenParser<BigDecimal> = chainl1(product, op("+", BigDecimal::plus))

val let: TokenParser<Pair<String, BigDecimal>> = parser {
    Tokens.keyword("let").bind()
    val name = Tokens.identifier.bind()
    Tokens.symbol("=").bind()
    name to sum.bind()
}

// JSON

sealed interface JsonValue {
    data class Str(val value: String) : JsonValue
    data class Num(val value: BigDecimal) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
}

// Strings without escape sequences, to keep the example short
val jsonString: Parser<String> =
    (char('"') skipL noneOf("\"").many() skipR char('"')).map { it.joinToString("") }.lexeme()

// Defined further down; lazyParser lets arrays and objects refer to it
val jsonValue: Parser<JsonValue> = lazyParser { anyJsonValue }

val jsonArray: Parser<JsonValue> =
    between(char('[').lexeme(), char(']').lexeme(), sepBy(jsonValue, char(',').lexeme()))
        .map { JsonValue.Arr(it) }

val jsonMember: Parser<Pair<String, JsonValue>> = jsonString skipR char(':').lexeme() then jsonValue

val jsonObject: Parser<JsonValue> =
    between(char('{').lexeme(), char('}').lexeme(), sepBy(jsonMember, char(',').lexeme()))
        .map { JsonValue.Obj(it.toMap()) }

val anyJsonValue: Parser<JsonValue> = choice(
    jsonString.map { JsonValue.Str(it) },
    bigDecimal.lexeme().map { JsonValue.Num(it) },
    keyword("true").lexeme().map { JsonValue.Bool(true) },
    keyword("false").lexeme().map { JsonValue.Bool(false) },
    keyword("null").lexeme().map { JsonValue.Null },
    jsonArray,
    jsonObject,
).label("JSON value")

val json: Parser<JsonValue> = spaces skipL jsonValue

// Configuration language

data class Section(val name: String, val properties: Map<String, String>)
data class Config(val sections: List<Section>)

val quotedString: Parser<String> =
    (char('"') skipL noneOf("\"").many() skipR char('"')).map { it.joinToString("") }.lexeme()

val bareValue: Parser<String> =
    satisfy("value") { it.isLetterOrDigit() || it in "._-" }.many1().map { it.joinToString("") }.lexeme()

val property: Parser<Pair<String, String>> = parser {
    val key = identifier.lexeme().bind()
    char('=').lexeme().bind()
    key to (quotedString or bareValue).bind()
}

val section: Parser<Section> = parser {
    val name = identifier.lexeme().bind()
    val properties = between(char('{').lexeme(), char('}').lexeme(), property.many()).bind()
    Section(name, properties.toMap())
}

val config: Parser<Config> = (spaces skipL section.many()).map { Config(it) }

class ReadmeExamplesTest : FunSpec({

    test("basic usage") {
        runParser(int, "42") shouldBe 42.right()
        runParser(int, "42abc").shouldBeLeft().let {
            it.index shouldBe 2
            it.expected shouldBe setOf("end of input")
        }
        runParser(spaces skipL int.lexeme(), "  42  ") shouldBeRight 42
    }

    test("string parsing") {
        runParser(string("hello"), "hello") shouldBeRight "hello"
        runParser(char('5'), "5") shouldBeRight '5'
        runParser(satisfy("letter") { it.isLetter() }, "x") shouldBeRight 'x'
        runParser(oneOf("aeiou"), "e") shouldBeRight 'e'
        runParser(noneOf("aeiou"), "x") shouldBeRight 'x'
    }

    test("parser combinators") {
        runParser(identifier.lexeme() then int, "john 25") shouldBeRight ("john" to 25)
        runParser(char('"') skipL identifier skipR char('"'), "\"hello\"") shouldBeRight "hello"

        val numberOrNull = int.map { it.toString() } or string("null")
        runParser(numberOrNull, "42") shouldBeRight "42"
        runParser(numberOrNull, "null") shouldBeRight "null"

        val optionalSign = char('-').optional()
        runParser(optionalSign, "-") shouldBeRight '-'
        runParser(optionalSign, "") shouldBeRight null

        runParser(digit.many(), "123") shouldBeRight listOf('1', '2', '3')
        runParser(digit.many1(), "").shouldBeLeft().expected shouldBe setOf("digit")
    }

    test("parser builder") {
        runParser(assignment, "x = 42") shouldBeRight Assignment("x", 42)
    }

    test("backtracking") {
        val ab = char('a') then char('b')
        val ac = char('a') then char('c')
        runParser(ab or ac, "ac").shouldBeLeft().expected shouldBe setOf("'b'")
        runParser(attempt(ab) or ac, "ac") shouldBeRight ('a' to 'c')
    }

    test("more combinators") {
        runParser(between(char('('), char(')'), identifier), "(hello)") shouldBeRight "hello"

        val csvNumbers = sepBy(int, char(','))
        runParser(csvNumbers, "1,2,3,4") shouldBeRight listOf(1, 2, 3, 4)
        runParser(csvNumbers, "") shouldBeRight emptyList()
        runParser(csvNumbers, "1,2,").shouldBeLeft().index shouldBe 4
        runParser(sepBy1(int, char(',')), "").isLeft() shouldBe true

        val minus: Parser<(Int, Int) -> Int> = char('-').map { { a: Int, b: Int -> a - b } }
        runParser(chainl1(unsignedInt, minus), "10-3-2") shouldBeRight 5
    }

    test("tokenization") {
        val tokenizer = tokenize(
            keywords = setOf("if", "then", "else", "let"),
            symbols = setOf("+", "-", "*", "/", "(", ")", "=", "==")
        )
        val tokens = runOrThrow(tokenizer, "if x == 42 then x + 1 else 0")
        val printed = tokens.map { token ->
            when (token) {
                is Token.Keyword -> "Keyword '${token.text}' at ${token.span}"
                is Token.Identifier -> "Identifier '${token.name}' at ${token.span}"
                is Token.Number -> "Number ${token.value} at ${token.span}"
                is Token.Symbol -> "Symbol '${token.text}' at ${token.span}"
            }
        }
        printed.take(4) shouldBe listOf(
            "Keyword 'if' at Span(start=0, end=2)",
            "Identifier 'x' at Span(start=3, end=4)",
            "Symbol '==' at Span(start=5, end=7)",
            "Number 42 at Span(start=8, end=10)",
        )
        printed.size shouldBe 10
    }

    test("parsing tokens") {
        runParser(let, runOrThrow(lexer, "let total = 2 * (3 + 4)")) shouldBeRight ("total" to BigDecimal("14"))

        val source = "let total = 2 * + 4"
        val error = runParser(let, runOrThrow(lexer, source)).shouldBeLeft()
        error.pretty(source) shouldBe """
            Parse error at line 1, column 17 (index 16)
            let total = 2 * + 4
                            ^
            expected: number | "("
            found: "+"
        """.trimIndent()

        val even = token("even number") { n: Int -> n.takeIf { it % 2 == 0 } }
        (even.many().parse(Input(listOf(2, 4, 5))) as ParseResult.Ok).value shouldBe listOf(2, 4)
    }

    test("parsing a stream") {
        val events = """
            {"type": "login", "user": "ada"}
            {"type": "logout", "user": "ada"}
        """.trimIndent() + "\n"

        parseEach(jsonValue, StringReader(events)).toList() shouldBe listOf(
            JsonValue.Obj(mapOf("type" to JsonValue.Str("login"), "user" to JsonValue.Str("ada"))),
            JsonValue.Obj(mapOf("type" to JsonValue.Str("logout"), "user" to JsonValue.Str("ada"))),
        )
    }

    test("error handling") {
        val source = "hello universe"
        val error = runParser(string("hello").lexeme() then string("world"), source).shouldBeLeft()
        error.pretty(source) shouldBe """
            Parse error at line 1, column 7 (index 6)
            hello universe
                  ^
            expected: "world"
            found: 'u'
        """.trimIndent()
    }

    test("JSON parser") {
        runParser(json, """{"name": "ParseKek", "tags": ["kotlin", "parser"], "stars": 42, "fork": null}""") shouldBeRight
            JsonValue.Obj(
                mapOf(
                    "name" to JsonValue.Str("ParseKek"),
                    "tags" to JsonValue.Arr(listOf(JsonValue.Str("kotlin"), JsonValue.Str("parser"))),
                    "stars" to JsonValue.Num(BigDecimal("42")),
                    "fork" to JsonValue.Null,
                )
            )
        runParser(json, "[1, 2,]").shouldBeLeft().expected shouldBe setOf("JSON value")
    }

    test("configuration language parser") {
        val source = """
            server {
              port = 8080
              host = "localhost"
            }
        """.trimIndent()

        runParser(config, source) shouldBeRight
            Config(listOf(Section("server", mapOf("port" to "8080", "host" to "localhost"))))
    }

    test("testing parsers") {
        runParser(assignment, "x = 42") shouldBeRight Assignment("x", 42)

        val error = runParser(assignment, "x = ").shouldBeLeft()
        error.index shouldBe 4
        error.expected shouldContain "integer"
    }
})
