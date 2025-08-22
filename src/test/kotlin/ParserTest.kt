package io.github.larsw.parsekek

import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class ParserTest : FunSpec({

    test("Parser should parse successfully") {
        val parser = Parser<String> { inp ->
            if (inp.text.startsWith("hello", inp.index)) {
                ParseResult.Ok("hello", inp.advance(5))
            } else {
                ParseResult.Err(ParseError.expected(inp.index, "hello"), false)
            }
        }

        val result = parser.parse(Input("hello world", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<String>>()
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

    test("runParser should return Either") {
        val successParser = Parser<String> { inp ->
            ParseResult.Ok("success", inp)
        }
        val failParser = Parser<String> { inp ->
            ParseResult.Err(ParseError.expected(inp.index, "fail"), false)
        }

        runParser(successParser, "test") shouldBe "success".right()
        runParser(failParser, "test").isLeft() shouldBe true
    }

    test("runOrThrow should return value or throw") {
        val successParser = Parser<String> { inp ->
            ParseResult.Ok("success", inp)
        }
        val failParser = Parser<String> { inp ->
            ParseResult.Err(ParseError.expected(inp.index, "fail"), false)
        }

        runOrThrow(successParser, "test") shouldBe "success"

        try {
            runOrThrow(failParser, "test")
            throw AssertionError("Should have thrown")
        } catch (e: IllegalArgumentException) {
            e.message shouldContain "Parse error"
        }
    }
})
