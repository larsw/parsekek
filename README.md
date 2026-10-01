# ParseKek

A functional parser combinator library for Kotlin, inspired by Haskell's Parsec and other functional parsing libraries. You build parsers by composing small ones, and you get detailed error messages and explicit control over backtracking.

## Features

- Type-safe: parsers are checked by Kotlin's type system
- Complex parsers are built from simple ones, or written as sequential code with the `parser { }` builder
- Error messages include line and column, what was expected and what was found
- You decide when a parser backtracks
- Monadic interface, so the usual functional programming patterns apply
- Works with Arrow's `Either`
- Built-in tokenizer, and token parsers to parse what it produces
- Streams records from a `Reader`, keeping only one record in memory

## Quick start

ParseKek needs Java 21 or newer.

### Basic usage

```kotlin
import io.github.larsw.parsekek.*

// runParser parses the whole input
runParser(int, "42")    // Right(42)
runParser(int, "42abc") // Left: expected end of input at index 2

// Parsers never skip whitespace on their own. Make each token a lexeme,
// which skips the whitespace after it, and skip leading whitespace once.
val number = spaces skipL int.lexeme()
runParser(number, "  42  ") // Right(42)
```

To parse only a prefix of the input, call the parser directly: `parser.parse(Input(source))` returns a `ParseResult` with the value and the remaining input.

### String parsing

```kotlin
// Match exact strings
runParser(string("hello"), "hello") // Right("hello")

// Match characters
runParser(char('5'), "5") // Right('5')

// Character classes
val letter = satisfy("letter") { it.isLetter() }
val vowel = oneOf("aeiou")
val consonant = noneOf("aeiou")
```

### Parser combinators

```kotlin
// Sequence parsers (keep both results)
val nameAndAge = identifier.lexeme() then int
runParser(nameAndAge, "john 25") // Right((john, 25))

// Sequence parsers (keep only one result)
val quoted = char('"') skipL identifier skipR char('"')
runParser(quoted, "\"hello\"") // Right("hello")

// Choice between alternatives
val numberOrNull = int.map { it.toString() } or string("null")
runParser(numberOrNull, "42")   // Right("42")
runParser(numberOrNull, "null") // Right("null")

// Optional parsing
val optionalSign = char('-').optional()
runParser(optionalSign, "-") // Right('-')
runParser(optionalSign, "")  // Right(null)

// Repetition
runParser(digit.many(), "123") // Right([1, 2, 3])
runParser(digit.many1(), "")   // Left: expected digit
```

### Parser builder

Chains of `then` produce nested pairs. For more than two steps, the `parser { }` builder is easier to read: call `bind()` on a parser to run it and get its value.

```kotlin
data class Assignment(val name: String, val value: Int)

val assignment: Parser<Assignment> = parser {
    val name = identifier.lexeme().bind()
    char('=').lexeme().bind()
    Assignment(name, int.lexeme().bind())
}

runParser(assignment, "x = 42") // Right(Assignment(name=x, value=42))
```

### Backtracking

`or` tries its right side only if the left side failed without consuming input. This keeps error messages precise: once a parser has committed to a branch, a failure is reported where it happened instead of as "none of the alternatives matched".

`string`, `keyword` and the number parsers consume nothing when they fail, so `string("let") or string("lambda")` just works. For anything else, wrap the left side in `attempt` to let `or` backtrack:

```kotlin
val ab = char('a') then char('b')
val ac = char('a') then char('c')

runParser(ab or ac, "ac")          // Left: expected 'b'; 'a' was consumed, so ac is not tried
runParser(attempt(ab) or ac, "ac") // Right((a, c))
```

`optional`, `many` and `sepBy` follow the same rule: if an element fails after consuming input, the whole parser fails instead of quietly stopping early.

### More combinators

```kotlin
// Parse between delimiters
runParser(between(char('('), char(')'), identifier), "(hello)") // Right("hello")

// Comma-separated values
val csvNumbers = sepBy(int, char(','))
runParser(csvNumbers, "1,2,3,4") // Right([1, 2, 3, 4])
runParser(csvNumbers, "")        // Right([])
runParser(csvNumbers, "1,2,")    // Left: expected integer at index 4

// At least one element
runParser(sepBy1(int, char(',')), "") // Left(...)

// Left-associative operators
val minus: Parser<(Int, Int) -> Int> = char('-').map { { a: Int, b: Int -> a - b } }
runParser(chainl1(unsignedInt, minus), "10-3-2") // Right(5)
```

The others are `choice` (several alternatives), `lookAhead`, `notFollowedBy` and `label`, which names a parser in error messages: `digit.many1().label("number")` fails with "expected: number" instead of "expected: digit".

## Expression parsing example

[`src/test/kotlin/ArithmeticExample.kt`](src/test/kotlin/ArithmeticExample.kt) is an arithmetic expression parser with operator precedence, built from `chainl1`:

```kotlin
private fun op(symbol: Char, build: (Expr, Expr) -> Expr): Parser<(Expr, Expr) -> Expr> =
    char(symbol).lexeme().map { build }

private val number: Parser<Expr> = unsignedDouble.lexeme().map(Expr::Num)

private val parenthesized: Parser<Expr> =
    between(char('(').lexeme(), char(')').lexeme(), lazyParser { sum })

// Any number of prefix signs, e.g. "--3". An odd number of '-' negates.
private val factor: Parser<Expr> = parser {
    val signs = oneOf("+-").lexeme().many().bind()
    val operand = (number or parenthesized).label("number or '('").bind()
    if (signs.count { it == '-' } % 2 == 1) Expr.Neg(operand) else operand
}

private val product: Parser<Expr> = chainl1(factor, op('*', Expr::Mul) or op('/', Expr::Div))

private val sum: Parser<Expr> = chainl1(product, op('+', Expr::Add) or op('-', Expr::Sub))

val expression: Parser<Expr> = spaces skipL sum

eval(runOrThrow(expression, "3 + 4 * (2 - 1)")) // 7.0
runOrThrow(expression, "2 + 3 * 4")            // Add(Num(2.0), Mul(Num(3.0), Num(4.0)))
```

## Tokenization

The built-in tokenizer tracks the position of each token:

```kotlin
// Define your language's tokens
val keywords = setOf("if", "then", "else", "let")
val symbols = setOf("+", "-", "*", "/", "(", ")", "=", "==")

val tokenizer = tokenize(keywords, symbols)
val tokens = runOrThrow(tokenizer, "if x == 42 then x + 1 else 0")

tokens.forEach { token ->
    when (token) {
        is Token.Keyword -> println("Keyword '${token.text}' at ${token.span}")
        is Token.Identifier -> println("Identifier '${token.name}' at ${token.span}")
        is Token.Number -> println("Number ${token.value} at ${token.span}")
        is Token.Symbol -> println("Symbol '${token.text}' at ${token.span}")
    }
}
// Keyword 'if' at Span(start=0, end=2)
// Identifier 'x' at Span(start=3, end=4)
// Symbol '==' at Span(start=5, end=7)
// Number 42 at Span(start=8, end=10)
// ...
```

Spans cover the token text without surrounding whitespace. Keywords only match whole words, so "iffy" is an identifier. Number tokens are unsigned and exact (`BigDecimal`), so "x-1" is `x`, `-`, `1`.

### Parsing tokens

A `TokenParser` reads tokens instead of text. `Tokens` has a parser for each kind of token, and the combinators work as they do on text:

```kotlin
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

runParser(let, runOrThrow(lexer, "let total = 2 * (3 + 4)")) // Right((total, 14))
```

When `runParser` fails on tokens, the error's index is a position in the source text, so `pretty(source)` works:

```kotlin
val source = "let total = 2 * + 4"
runParser(let, runOrThrow(lexer, source)).onLeft { println(it.pretty(source)) }
// Parse error at line 1, column 17 (index 16)
// let total = 2 * + 4
//                 ^
// expected: number | "("
// found: "+"
```

Parsers aren't limited to text and tokens. `token("even number") { n: Int -> n.takeIf { it % 2 == 0 } }` matches one element of any type, and `parser.parse(Input(list))` runs a parser on a list.

## Error handling

Errors carry the index, what was expected, and an optional message. `pretty` formats them with the source line:

```kotlin
val source = "hello universe"
val parser = string("hello").lexeme() then string("world")

when (val result = runParser(parser, source)) {
    is Either.Right -> println("Success: ${result.value}")
    is Either.Left -> println(result.value.pretty(source))
}
// Parse error at line 1, column 7 (index 6)
// hello universe
//       ^
// expected: "world"
// found: 'u'
```

`runOrThrow` throws a `ParseException` instead, with the same text as its message, the `ParseError` in its `error` property and the line and column in `position`.

## Parsing a stream

`parseEach` reads records from a `Reader` and returns them as a lazy `Sequence`. It reads text as the parser asks for it and drops each record's text once the record is parsed, so memory use depends on the size of a record rather than the size of the input. That makes it suitable for large files and for input that never ends, like a socket.

```kotlin
// One JSON document per line, using the JSON parser below
File("events.ndjson").bufferedReader().use { reader ->
    parseEach(jsonValue, reader).forEach { event -> println(event) }
}
```

The record parser has to consume whatever separates records. Here `jsonValue` is a lexeme, so it skips the newline after each document. If a record fails to parse, iterating throws a `ParseException` whose `position` is the line and column in the whole input. The records before it have already been returned.

## Building custom parsers

### JSON parser example

```kotlin
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

runParser(json, """{"name": "ParseKek", "tags": ["kotlin", "parser"], "stars": 42}""")
```

### Configuration language parser

```kotlin
// Parse configuration files like:
// server {
//   port = 8080
//   host = "localhost"
// }

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
```

Top-level properties are initialized in order, so define a parser before the parsers that use it, or refer to it through `lazyParser`.

## Testing parsers

Parsers are easy to test with frameworks like Kotest. The `shouldBeRight` and `shouldBeLeft` matchers come from `io.kotest:kotest-assertions-arrow`:

```kotlin
class AssignmentTest : StringSpec({
    "parses an assignment" {
        runParser(assignment, "x = 42") shouldBeRight Assignment("x", 42)
    }

    "reports where parsing failed" {
        val error = runParser(assignment, "x = ").shouldBeLeft()
        error.index shouldBe 4
        error.expected shouldContain "integer"
    }

    "parses any Int" {
        checkAll(Arb.int()) { n ->
            runParser(int, n.toString()) shouldBeRight n
        }
    }
})
```

The examples in this README are checked by [`ReadmeExamplesTest`](src/test/kotlin/ReadmeExamplesTest.kt).

## Architecture

ParseKek is split into these modules:

- Core types: `Source`, `Input`, `Position`, `ParseError`, `ParseResult`, and `GenericParser` with its aliases `Parser` (text) and `TokenParser`
- Runners: `runParser` (for text or tokens), `runOrThrow`, `parseEach` and `ParseException`
- Primitives: `char`, `string`, `satisfy`, `spaces`, `eof`, and `token` for any element type
- Combinators: `map`, `flatMap`, `then`, `or`, `attempt`, `many`, `label`, `parser { }` and friends for composing parsers
- Lexical helpers: `lexeme`, `identifier`, `keyword`
- Numbers: `int`, `long`, `bigInteger`, `double`, `bigDecimal`, each with an `unsigned` variant, and `sign`
- Tokenization: `Token`, `tokenize`, and `Tokens` for parsing tokens

## API documentation

Generate the API docs from the KDoc comments:

```bash
./gradlew dokkaGenerate
```

The output goes to `build/dokka/index.html`.

## Performance tips

1. Make each token a lexeme with `.lexeme()` and skip leading whitespace once with `spaces skipL ...`, rather than skipping whitespace before and after everything.
2. Prefer `many()` over recursion for repetition. It loops instead of recursing, so long lists don't grow the stack.
3. `lazyParser()` lets a parser refer to one defined later, which recursive grammars need. It doesn't make recursion stack-safe: very deeply nested input can still overflow the default thread stack. Run the parser on a thread with a bigger stack if you need to accept such input.
4. For large inputs made of independent records, such as log lines or one JSON document per line, use `parseEach`. It parses at about the same speed as `runParser` on a string and keeps only one record in memory.
5. Profile with realistic inputs, since parser performance can vary a lot.

## Comparison with other libraries

| Feature | ParseKek | ANTLR | Kotlin Parser Combinators |
|---------|----------|-------|---------------------------|
| Type Safety | ✅ | ⚠️ | ✅ |
| Composability | ✅ | ❌ | ✅ |
| Error Messages | ✅ | ✅ | ⚠️ |
| Performance | ⚠️ | ✅ | ⚠️ |
| Learning Curve | ⚠️ | ⚠️ | ✅ |
| IDE Support | ✅ | ✅ | ✅ |

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for your changes
4. Run `./gradlew test` and make sure the tests pass
5. Run `./gradlew dokkaGenerate` to check that the docs still build
6. Submit a pull request

## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by Haskell's [Parsec](https://hackage.haskell.org/package/parsec)
- Built on [Arrow](https://arrow-kt.io/)
- Tested with [Kotest](https://kotest.io/)
