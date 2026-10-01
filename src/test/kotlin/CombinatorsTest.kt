package io.github.larsw.parsekek

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
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

        result.shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
        result.value shouldBe "5"
    }

    test("flatMap should chain parsers") {
        val parser = char('a').flatMap { char('b') }

        parser.parse(Input("ab", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        parser.parse(Input("ac", 0)).shouldBeInstanceOf<ParseResult.Err>()
        parser.parse(Input("bb", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("then combinator should combine parsers") {
        val parser = char('a') then char('b')
        val result = parser.parse(Input("ab", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<Char, Pair<Char, Char>>>()
        result.value shouldBe ('a' to 'b')
        result.next.index shouldBe 2
    }

    test("skipL should keep only right result") {
        val parser = char('a') skipL char('b')
        val result = parser.parse(Input("ab", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        result.value shouldBe 'b'
    }

    test("skipR should keep only left result") {
        val parser = char('a') skipR char('b')
        val result = parser.parse(Input("ab", 0))

        result.shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        result.value shouldBe 'a'
    }

    test("or combinator should try alternatives") {
        val parser = char('a') or char('b')

        parser.parse(Input("a", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        parser.parse(Input("b", 0)).shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        parser.parse(Input("c", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("or combinator should merge error messages") {
        val parser = char('a') or char('b')
        val result = parser.parse(Input("c", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.error.expected shouldBe setOf("'a'", "'b'")
    }

    test("or combinator should respect consumed input") {
        val parser = (char('a') then char('b')) or (char('a') then char('c'))
        val result = parser.parse(Input("ac", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.consumed shouldBe true // the first branch consumed "a"
        result.error.index shouldBe 1
        result.error.expected shouldBe setOf("'b'")
    }

    test("or does not depend on how a chain is grouped") {
        val ab = char('a') then char('b')
        val ac = char('a') then char('c')
        val x = char('x') then char('x')

        (x or ab or ac).parse(Input("ac", 0)).shouldBeInstanceOf<ParseResult.Err>().consumed shouldBe true
        (x or (ab or ac)).parse(Input("ac", 0)).shouldBeInstanceOf<ParseResult.Err>().consumed shouldBe true
    }

    test("attempt lets or backtrack after consumed input") {
        val parser = attempt(char('a') then char('b')) or (char('a') then char('c'))
        runParser(parser, "ac") shouldBeRight ('a' to 'c')
    }

    test("attempt keeps the position of the deeper error") {
        val parser = attempt(string("ab") then char('c')) or string("abd")
        val error = runParser(parser, "abx").shouldBeLeft()
        error.index shouldBe 2
        error.expected shouldBe setOf("'c'")
    }

    test("choice tries alternatives in order") {
        val parser = choice(string("a"), string("b"), string("c"))
        runParser(parser, "c") shouldBeRight "c"
        parser.parse(Input("d", 0)).shouldBeInstanceOf<ParseResult.Err>().error.expected shouldBe
            setOf("\"a\"", "\"b\"", "\"c\"")
    }

    test("optional should succeed with Some or None") {
        val parser = char('a').optional()

        val success = parser.parse(Input("a", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<Char, Char?>>()
        success.value shouldBe 'a'

        val none = parser.parse(Input("b", 0))
        none.shouldBeInstanceOf<ParseResult.Ok<Char, Char?>>()
        none.value shouldBe null
    }

    test("optional fails if the parser fails after consuming input") {
        val parser = (char('a') then char('b')).optional()
        val result = parser.parse(Input("ac", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.consumed shouldBe true
    }

    test("many should parse zero or more") {
        val parser = char('a').many()

        val empty = parser.parse(Input("bbb", 0))
        empty.shouldBeInstanceOf<ParseResult.Ok<Char, List<Char>>>()
        empty.value shouldBe emptyList()

        val some = parser.parse(Input("aaab", 0))
        some.shouldBeInstanceOf<ParseResult.Ok<Char, List<Char>>>()
        some.value shouldBe listOf('a', 'a', 'a')
        some.next.index shouldBe 3
    }

    test("many rejects a parser that succeeds without consuming input") {
        val infiniteParser = Parser<Unit> { inp -> ParseResult.Ok(Unit, inp) }
        shouldThrow<IllegalStateException> { infiniteParser.many().parse(Input("test", 0)) }
    }

    test("many fails if an element fails after consuming input") {
        val parser = (char('a') then char('b')).many()
        val result = parser.parse(Input("aba", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.error.index shouldBe 3
        result.consumed shouldBe true
    }

    test("many1 should parse one or more") {
        val parser = char('a').many1()

        val failure = parser.parse(Input("bbb", 0))
        failure.shouldBeInstanceOf<ParseResult.Err>()
        failure.error.expected shouldBe setOf("'a'")

        val success = parser.parse(Input("aaab", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<Char, List<Char>>>()
        success.value shouldBe listOf('a', 'a', 'a')
    }

    test("between should parse surrounded content") {
        val parser = between(char('('), char(')'), char('x'))

        val success = parser.parse(Input("(x)", 0))
        success.shouldBeInstanceOf<ParseResult.Ok<Char, Char>>()
        success.value shouldBe 'x'

        parser.parse(Input("(y)", 0)).shouldBeInstanceOf<ParseResult.Err>()
        parser.parse(Input("x)", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("between accepts delimiters of different types") {
        val parser = between(string("<<"), char('>'), int)
        runParser(parser, "<<42>") shouldBeRight 42
    }

    test("sepBy should parse separated list") {
        val parser = sepBy(char('a'), char(','))

        val empty = parser.parse(Input("b", 0))
        empty.shouldBeInstanceOf<ParseResult.Ok<Char, List<Char>>>()
        empty.value shouldBe emptyList()

        val single = parser.parse(Input("a", 0))
        single.shouldBeInstanceOf<ParseResult.Ok<Char, List<Char>>>()
        single.value shouldBe listOf('a')

        val multiple = parser.parse(Input("a,a,a", 0))
        multiple.shouldBeInstanceOf<ParseResult.Ok<Char, List<Char>>>()
        multiple.value shouldBe listOf('a', 'a', 'a')
    }

    test("sepBy rejects a trailing or doubled separator instead of stopping early") {
        val parser = sepBy(int, char(','))

        runParser(parser, "1,2,").shouldBeLeft().let {
            it.index shouldBe 4
            it.expected shouldBe setOf("integer")
        }
        runParser(parser, "1,,2").shouldBeLeft().index shouldBe 2
    }

    test("sepBy1 should require at least one element") {
        val parser = sepBy1(char('a'), char(','))

        parser.parse(Input("b", 0)).shouldBeInstanceOf<ParseResult.Err>()

        val single = parser.parse(Input("a", 0))
        single.shouldBeInstanceOf<ParseResult.Ok<Char, List<Char>>>()
        single.value shouldBe listOf('a')
    }

    test("pure should always succeed") {
        val parser = pure<Char, String>("constant")
        val result = parser.parse(Input("anything", 5))

        result.shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
        result.value shouldBe "constant"
        result.next.index shouldBe 5 // unchanged
    }

    test("fail should always fail") {
        val parser = fail<Char, String>("test error")
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

    test("lazyParser builds its parser only once") {
        var builds = 0
        val parser = lazyParser {
            builds += 1
            char('a')
        }

        runParser(parser.many(), "aaaaa") shouldBeRight List(5) { 'a' }
        builds shouldBe 1
    }

    test("lookAhead succeeds without consuming input") {
        val result = lookAhead(string("ab")).parse(Input("abc", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Char, String>>()
        result.value shouldBe "ab"
        result.next.index shouldBe 0
    }

    test("notFollowedBy succeeds only when the parser fails") {
        val parser = string("if") skipR notFollowedBy(letter)

        runParser(parser, "if") shouldBeRight "if"
        parser.parse(Input("iffy", 0)).shouldBeInstanceOf<ParseResult.Err>().error.message shouldBe "unexpected \"f\""
    }

    test("label replaces what a parser expects when it fails without consuming input") {
        val number = digit.many1().label("number")

        number.parse(Input("x", 0)).shouldBeInstanceOf<ParseResult.Err>().error.expected shouldBe setOf("number")
    }

    test("label keeps the inner error once input was consumed") {
        val pair = (char('(') then char(')')).label("unit")

        pair.parse(Input("(x", 0)).shouldBeInstanceOf<ParseResult.Err>().error.expected shouldBe setOf("')'")
    }

    test("errors list what could have continued after an empty success") {
        val error = runParser(digit.many(), "12x").shouldBeLeft()

        error.index shouldBe 2
        error.expected shouldBe setOf("digit", "end of input")
    }

    test("errors include optional parts that were skipped") {
        val signed = char('-').optional() then digit
        signed.parse(Input("x", 0)).shouldBeInstanceOf<ParseResult.Err>().error.expected shouldBe setOf("'-'", "digit")
    }

    test("chainl1 combines operands left to right") {
        val minus: Parser<(Int, Int) -> Int> = char('-').map { { a: Int, b: Int -> a - b } }
        val parser = chainl1(unsignedInt, minus)

        runParser(parser, "10-3-2") shouldBeRight 5
        runParser(parser, "7") shouldBeRight 7
        runParser(parser, "7-").shouldBeLeft().expected shouldBe setOf("unsigned integer")
    }

    test("parser builder sequences steps and returns the block's result") {
        val assignment = parser {
            val name = identifier.lexeme().bind()
            char('=').lexeme().bind()
            name to int.bind()
        }

        runParser(assignment, "x = 42") shouldBeRight ("x" to 42)
    }

    test("parser builder fails with consumed input after a step consumed") {
        val ab = parser {
            char('a').bind()
            char('b').bind()
        }
        val result = (ab or string("ac")).parse(Input("ac", 0))

        result.shouldBeInstanceOf<ParseResult.Err>()
        result.consumed shouldBe true
        result.error.expected shouldBe setOf("'b'")
    }

    test("parser builder can be backtracked with attempt") {
        val ab = parser {
            char('a').bind()
            char('b').bind()
        }

        runParser(attempt(ab) or string("ac"), "ac") shouldBeRight "ac"
    }

    test("nested parser builders fail independently") {
        val inner = parser { char('x').bind() }
        val outer = parser {
            val first = inner.optional().bind()
            val second = char('y').bind()
            "$first$second"
        }

        runParser(outer, "y") shouldBeRight "nully"
        runParser(outer, "xy") shouldBeRight "xy"
        runParser(outer, "z").shouldBeLeft().expected shouldBe setOf("'x'", "'y'")
    }
})
