# ParseKek

A functional parser combinator library for Kotlin, inspired by Haskell's Parsec and other functional parsing libraries. ParseKek provides a clean, composable API for building parsers with excellent error reporting and backtracking control.

## Features

- 🎯 **Type-safe** - Leverage Kotlin's type system for parser safety
- 🔄 **Composable** - Build complex parsers from simple building blocks
- 📍 **Precise Error Reporting** - Get detailed error messages with line/column information
- ⚡ **Backtracking Control** - Explicit control over when parsers backtrack
- 🎨 **Monadic Interface** - Familiar functional programming patterns
- 📦 **Arrow Integration** - Works seamlessly with Arrow's Either and other functional types
- 🏷️ **Tokenization Support** - Built-in support for lexical analysis with position tracking

## Quick Start

### Basic Usage

```kotlin
import io.github.larsw.parsekek.*

// Parse a simple number
val number = int
val result = runParser(number, "42")
// result: Right(42)

// Parse with whitespace handling
val lexemeNumber = token(int)
val result2 = runParser(lexemeNumber, "  42  ")
// result2: Right(42)
```

### String Parsing

```kotlin
// Match exact strings
val hello = string("hello")
runParser(hello, "hello world") // Right("hello")

// Match characters
val digit = char('5')
runParser(digit, "5") // Right('5')

// Character classes
val letter = satisfy("letter") { it.isLetter() }
val vowel = oneOf("aeiou")
val consonant = noneOf("aeiou")
```

### Parser Combinators

```kotlin
// Sequence parsers (keep both results)
val nameAndAge = identifier then int
runParser(nameAndAge, "john 25") // Right(("john", 25))

// Sequence parsers (keep only one result)
val quoted = char('"') skipL identifier skipR char('"')
runParser(quoted, "\"hello\"") // Right("hello")

// Choice between alternatives
val numberOrString = int.map { it.toString() } or string("null")
runParser(numberOrString, "42") // Right("42")
runParser(numberOrString, "null") // Right("null")

// Optional parsing
val optionalSign = char('-').optional()
runParser(optionalSign, "-") // Right('-')
runParser(optionalSign, "a") // Right(null)

// Repetition
val digits = digit.many()
runParser(digits, "123") // Right(['1', '2', '3'])

val digits1 = digit.many1() // one or more
runParser(digits1, "") // Left(ParseError(...))
```

### Advanced Combinators

```kotlin
// Parse between delimiters
val parenthesized = between(char('('), char(')'), identifier)
runParser(parenthesized, "(hello)") // Right("hello")

// Comma-separated values
val csvNumbers = sepBy(int, char(','))
runParser(csvNumbers, "1,2,3,4") // Right([1, 2, 3, 4])
runParser(csvNumbers, "") // Right([]) - empty list is valid

// At least one element
val csvNumbers1 = sepBy1(int, char(','))
runParser(csvNumbers1, "") // Left(ParseError(...))
```

## Expression Parsing Example

ParseKek includes a complete arithmetic expression parser with operator precedence:

```kotlin
import io.github.larsw.parsekek.*

// Parse and evaluate arithmetic expressions
val expr = "3 + 4 * (2 - 1)"
val ast = runOrThrow(expression, expr)
val result = eval(ast) // 7.0

// The AST structure
sealed interface Expr {
    data class Num(val value: Double) : Expr
    data class Add(val l: Expr, val r: Expr) : Expr
    data class Mul(val l: Expr, val r: Expr) : Expr
    // ... other operations
}

// Handles operator precedence correctly
runOrThrow(expression, "2 + 3 * 4") // Add(Num(2), Mul(Num(3), Num(4)))
```

## Tokenization

ParseKek provides built-in tokenization with position tracking:

```kotlin
// Define your language's tokens
val keywords = setOf("if", "then", "else", "let")
val symbols = setOf("+", "-", "*", "/", "(", ")", "=", "==")

val tokenizer = tokenize(keywords, symbols)
val tokens = runOrThrow(tokenizer, "if x == 42 then x + 1 else 0")

// Result: List of tokens with position information
tokens.forEach { token ->
    when (token) {
        is Tok.Kw -> println("Keyword '${token.kw}' at ${token.span}")
        is Tok.Ident -> println("Identifier '${token.name}' at ${token.span}")
        is Tok.Num -> println("Number ${token.value} at ${token.span}")
        is Tok.Sym -> println("Symbol '${token.sym}' at ${token.span}")
    }
}
```

## Error Handling

ParseKek provides detailed error messages with source location:

```kotlin
val parser = string("hello") then string("world")
val result = runParser(parser, "hello universe")

when (result) {
    is Either.Right -> println("Success: ${result.value}")
    is Either.Left -> {
        println(result.value.pretty("hello universe"))
        // Output:
        // Parse error at line 1, column 7 (index 6)
        // hello universe
        //       ^
        // expected: "world"
    }
}
```

## Building Custom Parsers

### JSON Parser Example

```kotlin
// A simple JSON parser
fun jsonString(): Parser<String> = 
    char('"') skipL 
    noneOf("\"").many().map { it.joinToString("") } skipR 
    char('"')

fun jsonNumber(): Parser<Double> = token(double)

fun jsonBool(): Parser<Boolean> = 
    (keyword("true").map { true } or keyword("false").map { false })

fun jsonNull(): Parser<Nothing?> = keyword("null").map { null }

fun jsonArray(): Parser<List<JsonValue>> = 
    between(
        token(char('[')), 
        token(char(']')), 
        sepBy(lazyParser { jsonValue() }, token(char(',')))
    )

fun jsonObject(): Parser<Map<String, JsonValue>> = 
    between(
        token(char('{')),
        token(char('}')),
        sepBy(
            jsonString() skipR token(char(':')) then lazyParser { jsonValue() },
            token(char(','))
        ).map { it.toMap() }
    )

fun jsonValue(): Parser<JsonValue> = 
    jsonString().map { JsonValue.Str(it) } or
    jsonNumber().map { JsonValue.Num(it) } or
    jsonBool().map { JsonValue.Bool(it) } or
    jsonNull().map { JsonValue.Null } or
    jsonArray().map { JsonValue.Arr(it) } or
    jsonObject().map { JsonValue.Obj(it) }
```

### Configuration Language Parser

```kotlin
// Parse configuration files like:
// server {
//   port = 8080
//   host = "localhost"
// }

data class Config(val sections: List<Section>)
data class Section(val name: String, val properties: Map<String, String>)

val configParser = sepBy(section, spaces).map { Config(it) }

val section = identifier.flatMap { name ->
    between(
        token(char('{')),
        token(char('}')),
        sepBy(property, spaces)
    ).map { props -> Section(name, props.toMap()) }
}

val property = identifier.flatMap { key ->
    token(char('=')) skipL 
    (quotedString or identifier).map { value ->
        key to value
    }
}

val quotedString = between(char('"'), char('"'), 
    noneOf("\"").many().map { it.joinToString("") })
```

## Testing Parsers

ParseKek works great with testing frameworks like Kotest:

```kotlin
class ParserTest : StringSpec({
    "should parse simple expressions" {
        val result = runParser(expression, "2 + 3")
        result shouldBeRight Expr.Add(Expr.Num(2.0), Expr.Num(3.0))
    }
    
    "should handle parse errors" {
        val result = runParser(expression, "2 +")
        result shouldBeLeft { error ->
            error.index shouldBe 3
            error.expected shouldContain "digit"
        }
    }
    
    "should parse with property-based testing" {
        checkAll(Arb.int()) { n ->
            val result = runParser(int, n.toString())
            result shouldBeRight n.toLong()
        }
    }
})
```

## Architecture

ParseKek is organized into several modules:

- **Core Types** (`Input`, `ParseError`, `ParseResult`, `Parser`) - Foundation types
- **Runners** (`runParser`, `runOrThrow`) - Execute parsers and handle results
- **Primitives** (`char`, `string`, `satisfy`) - Basic building blocks
- **Combinators** (`map`, `flatMap`, `or`, `many`) - Composition operators
- **Lexical** (`identifier`, `keyword`, `token`) - Lexical analysis helpers
- **Numbers** (`int`, `double`, `sign`) - Numeric parsing
- **Tokenization** (`Tok`, `tokenize`) - Token-based parsing
- **Expression** (`Expr`, `expression`) - Example expression parser

## API Documentation

Generate comprehensive API documentation with KDoc:

```bash
./gradlew dokkaHtml
```

The documentation will be available in `build/dokka/index.html`.

## Performance Tips

1. **Use `token()` for lexeme parsing** - Automatically handles whitespace
2. **Prefer `many()` over recursion** - More efficient for repetition
3. **Use `lazyParser()` for recursive grammars** - Prevents stack overflow
4. **Consider tokenization first** - For complex languages, tokenize then parse
5. **Profile with realistic inputs** - Parser performance can vary significantly

## Comparison with Other Libraries

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
4. Run `./gradlew test` to ensure all tests pass
5. Run `./gradlew dokkaHtml` to verify documentation
6. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Inspired by Haskell's [Parsec](https://hackage.haskell.org/package/parsec)
- Built with [Arrow](https://arrow-kt.io/) for functional programming support
- Uses [Kotest](https://kotest.io/) for comprehensive testing
