package io.github.larsw.parsekek

import java.math.BigDecimal

/** A half-open range `[start, end)` of character indices in the source. */
data class Span(val start: Int, val end: Int)

/**
 * A token produced by [tokenize]. Its [span] covers the token's own text, without the
 * whitespace around it. Parse a list of tokens with a [TokenParser], built from [Tokens] and
 * the usual combinators.
 */
sealed interface Token {
    /** The source span this token covers */
    val span: Span

    /** Identifier token */
    data class Identifier(val name: String, override val span: Span) : Token

    /** Numeric literal token. Literals are unsigned; a '-' before one is a [Symbol]. */
    data class Number(val value: BigDecimal, override val span: Span) : Token

    /** Keyword token */
    data class Keyword(val text: String, override val span: Span) : Token

    /** Symbol/operator token */
    data class Symbol(val text: String, override val span: Span) : Token
}

private fun <A, T> Parser<A>.withSpan(build: (A, Span) -> T): Parser<T> = Parser { inp ->
    when (val r = this.parse(inp)) {
        is ParseResult.Ok  -> ParseResult.Ok(build(r.value, Span(inp.index, r.next.index)), r.next, r.hint)
        is ParseResult.Err -> r
    }
}

// Matches the longest of the given symbols at the current position.
private fun longestSymbol(symbols: Set<String>): Parser<String> {
    val longestFirst = symbols.sortedByDescending { it.length }
    return Parser { inp ->
        val match = longestFirst.firstOrNull { inp.startsWith(it) }
        if (match != null) ParseResult.Ok(match, inp.advance(match.length))
        else ParseResult.Err(ParseError.expected(inp.index, "symbol"), consumed = false)
    }
}

/**
 * Creates a tokenizer for the entire input with given keywords and symbols.
 *
 * Keywords take priority over identifiers but only match whole words ("iffy" is an identifier
 * even when "if" is a keyword). Symbols are matched longest first, so with "=" and "==" the
 * input "==" is one symbol. Whitespace between tokens is skipped.
 *
 * @param keywords Set of keyword strings to recognize
 * @param symbols Set of symbol strings to recognize
 * @return Parser that produces a list of tokens
 */
fun tokenize(
    keywords: Set<String> = emptySet(),
    symbols: Set<String> = emptySet()
): Parser<List<Token>> {
    val alternatives = buildList<Parser<Token>> {
        if (keywords.isNotEmpty()) {
            add(choice(*keywords.map(::keyword).toTypedArray()).withSpan(Token::Keyword))
        }
        add(identifier.withSpan(Token::Identifier))
        add(unsignedBigDecimal.withSpan(Token::Number))
        if (symbols.isNotEmpty()) add(longestSymbol(symbols).withSpan(Token::Symbol))
    }
    val token = choice(*alternatives.toTypedArray()).label("token")
    return spaces skipL token.lexeme().many() skipR eof
}

/**
 * Parsers that match one token each, for building a [TokenParser]. Run it with
 * `runParser(parser, tokens)`.
 *
 * ```
 * val sum: TokenParser<BigDecimal> =
 *     chainl1(Tokens.number, Tokens.symbol("+").map { { a: BigDecimal, b: BigDecimal -> a + b } })
 * ```
 */
object Tokens {
    /** Matches an identifier token and produces its name. */
    val identifier: TokenParser<String> = token("identifier") { (it as? Token.Identifier)?.name }

    /** Matches a number token and produces its value. */
    val number: TokenParser<BigDecimal> = token("number") { (it as? Token.Number)?.value }

    /** Matches the keyword [text]. */
    fun keyword(text: String): TokenParser<String> =
        token("\"$text\"") { (it as? Token.Keyword)?.text?.takeIf { kw -> kw == text } }

    /** Matches the symbol [text]. */
    fun symbol(text: String): TokenParser<String> =
        token("\"$text\"") { (it as? Token.Symbol)?.text?.takeIf { sym -> sym == text } }
}

// How a token is shown in error messages; matches the names Tokens uses for expectations.
internal fun Token.describe(): String = when (this) {
    is Token.Identifier -> "identifier \"$name\""
    is Token.Number -> "number ${value.toPlainString()}"
    is Token.Keyword -> "\"$text\""
    is Token.Symbol -> "\"$text\""
}
