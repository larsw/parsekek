package io.github.larsw.parsekek

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

class CombinatorsTest : FunSpec({

    test("map should transform parser result") {
        val digitParser = char('5').map { it.toString() }
        val result = digitParser.parse(Input("5abc", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<String>>()
        result.value shouldBe "5"
    }

    test("flatMap should chain parsers") {
        val parser = char('a').flatMap { char('b') }

        parser.parse(Input("ab", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        parser.parse(Input("ac", 0)).shouldBeInstanceOf<ParseResult.Err>()
        parser.parse(Input("bb", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("then combinator should combine parsers") {
        val parser = char('a') then char('b')
        val result = parser.parse(Input("ab", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<Pair<Char, Char>>>()
        result.value shouldBe ('a' to 'b')
        result.next.index shouldBe 2
    }

    test("skipL should keep only right result") {
        val parser = char('a') skipL char('b')
        val result = parser.parse(Input("ab", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<Char>>()
        result.value shouldBe 'b'
    }

    test("skipR should keep only left result") {
        val parser = char('a') skipR char('b')
        val result = parser.parse(Input("ab", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<Char>>()
        result.value shouldBe 'a'
    }

    test("or combinator should try alternatives") {
        val parser = char('a') or char('b')

        parser.parse(Input("a", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        parser.parse(Input("b", 0)).shouldBeInstanceOf<ParseResult.Ok<Char>>()
        parser.parse(Input("c", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("or combinator should merge error messages") {
        val parser = char('a') or char('b')
        val result = parser.parse(Input("c", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.error.expected shouldBe setOf("'a'", "'b'")
    }

    test("or combinator should respect consumed input") {
        val parser = string("abc") or string("abd")
        val result = parser.parse(Input("abe", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.consumed shouldBe true // first parser consumed "ab"
    }

    test("optional should succeed with Some or None") {
        val parser = char('a').optional()

        val success = parser.parse(Input("a", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<Char?>>()
        success.value shouldBe 'a'

        val none = parser.parse(Input("b", 0))
        none.shouldBeInstanceOf<ParseResult.Ok<Char?>>()
        none.value shouldBe null
    }

    test("many should parse zero or more") {
        val parser = char('a').many()

        val empty = parser.parse(Input("bbb", 0))
        empty.shouldBeInstanceOf<ParseResult.Ok<List<Char>>>()
        empty.value shouldBe emptyList()

        val some = parser.parse(Input("aaab", 0))
        some.shouldBeInstanceOf<ParseResult.Ok<List<Char>>>()
        some.value shouldBe listOf('a', 'a', 'a')
        some.next.index shouldBe 3
    }

    test("many should detect infinite loops") {
        val infiniteParser = Parser<Unit> { inp -> ParseResult.Ok(Unit, inp) }
        val manyParser = infiniteParser.many()
        val result = manyParser.parse(Input("test", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.error.expected shouldBe setOf("progress")
    }

    test("many1 should parse one or more") {
        val parser = char('a').many1()

        val failure = parser.parse(Input("bbb", 0))
        failure.shouldBeInstanceOf<ParseResult.Err>()

        val success = parser.parse(Input("aaab", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<List<Char>>>()
        success.value shouldBe listOf('a', 'a', 'a')
    }

    test("between should parse surrounded content") {
        val parser = between(char('('), char(')'), char('x'))

        val success = parser.parse(Input("(x)", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<Char>>()
        success.value shouldBe 'x'

        parser.parse(Input("(y)", 0)).shouldBeInstanceOf<ParseResult.Err>()
        parser.parse(Input("x)", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("sepBy should parse separated list") {
        val parser = sepBy(char('a'), char(','))

        val empty = parser.parse(Input("b", 0))
        empty.shouldBeInstanceOf<ParseResult.Ok<List<Char>>>()
        empty.value shouldBe emptyList()

        val single = parser.parse(Input("a", 0))
        single.shouldBeInstanceOf<ParseResult.Ok<List<Char>>>()
        single.value shouldBe listOf('a')

        val multiple = parser.parse(Input("a,a,a", 0))
        multiple.shouldBeInstanceOf<ParseResult.Ok<List<Char>>>()
        multiple.value shouldBe listOf('a', 'a', 'a')
    }

    test("sepBy1 should require at least one element") {
        val parser = sepBy1(char('a'), char(','))

        parser.parse(Input("b", 0)).shouldBeInstanceOf<ParseResult.Err>()

        val single = parser.parse(Input("a", 0))
        single.shouldBeInstanceOf<ParseResult.Ok<List<Char>>>()
        single.value shouldBe listOf('a')
    }

    test("pure should always succeed") {
        val parser = pure("constant")
        val result = parser.parse(Input("anything", 5))

        result.shouldBeInstanceOf<ParseResult.Ok<String>>()
        result.value shouldBe "constant"
        result.next.index shouldBe 5 // unchanged
    }

    test("fail should always fail") {
        val parser = fail<String>("test error")
        val result = parser.parse(Input("anything", 3))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.error.expected shouldBe setOf("test error")
        result.consumed shouldBe false
    }

    test("lazyParser should defer construction") {
        var constructed = false
        val parser = lazyParser {
            constructed = true
            char('a')
        }

        constructed shouldBe false
        parser.parse(Input("a", 0))
        constructed shouldBe true
    }
})
