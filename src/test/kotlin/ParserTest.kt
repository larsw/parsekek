package io.github.larsw.parsekek

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class ParserTest : FunSpec({

    test("Parser should parse successfully") {
        val parser = Parser<String> { inp ->
            if (inp.startsWith("hello")) {
                ParseResult.Ok("hello", inp.advance(5))
            } else {
                ParseResult.Err(ParseError.expected(inp.index, "hello"), false)
            }
        }

        val result = parser.parse(Input("hello world", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
        result.value shouldBe "hello"
        result.next.index shouldBe 5
    }

    test("Parser should fail with error") {
        val parser = Parser<String> { inp ->
            ParseResult.Err(ParseError.expected(inp.index, "test"), false)
        }

        val result = parser.parse(Input("hello", 0))
        result.shouldBeInstanceOf<ParseResult.Err>()
        result.error.expected shouldBe setOf("test")
        result.consumed shouldBe false
    }

    test("Parser is covariant, so subtype parsers combine without casts") {
        val ints: Parser<Int> = int
        val numbers: Parser<Number> = ints or double
        runParser(numbers, "42") shouldBeRight 42
    }

    test("runParser should return Either") {
        runParser(string("test"), "test") shouldBeRight "test"
        runParser(string("fail"), "test").shouldBeLeft().expected shouldBe setOf("\"fail\"")
    }

    test("runParser requires the whole input to be consumed") {
        val error = runParser(int, "42abc").shouldBeLeft()
        error.index shouldBe 2
        error.expected shouldBe setOf("end of input")
    }

    test("runOrThrow should return value or throw ParseException") {
        runOrThrow(string("test"), "test") shouldBe "test"

        val e = shouldThrow<ParseException> { runOrThrow(string("fail"), "test") }
        e.message shouldContain "Parse error"
        e.error.index shouldBe 0
        e.position shouldBe Position(1, 1)
    }
})
