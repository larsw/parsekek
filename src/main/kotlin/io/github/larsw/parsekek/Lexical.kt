package io.github.larsw.parsekek

/**
 * Applies this parser and then skips trailing whitespace.
 *
 * @param ws Parser for whitespace (defaults to spaces)
 * @return Parser that trims whitespace after parsing
 */
fun <A> Parser<A>.lexeme(ws: Parser<*> = spaces): Parser<A> = this.skipR(ws)

/**
 * Skips leading whitespace before applying this parser.
 *
 * @param ws Parser for whitespace (defaults to spaces)
 * @return Parser that trims whitespace before parsing
 */
fun <A> Parser<A>.trimLeft(ws: Parser<*> = spaces): Parser<A> = ws.skipL(this)

/**
 * Alias for lexeme - parses a token and skips trailing whitespace.
 *
 * @param p The parser to tokenize
 * @return Parser that skips whitespace after parsing
 */
fun <A> token(p: Parser<A>): Parser<A> = p.lexeme(spaces)

private val underscore: Parser<Char> = char('_')
private val identStartChar: Parser<Char> = letter or underscore
private val identPartChar: Parser<Char> = letter or digit or underscore

/** Parser that matches a valid identifier (letter/underscore followed by letters/digits/underscores) */
val identifier: Parser<String> = token(
    identStartChar.flatMap { first ->
        identPartChar.many().map { rest -> (listOf(first) + rest).joinToString("") }
    }
)

/**
 * Creates a parser that matches a specific keyword with word boundary checking.
 *
 * @param kw The keyword to match
 * @return Parser that matches the keyword only when followed by a word boundary
 */
fun keyword(kw: String): Parser<String> = token { inp ->
    when (val r = string(kw).parse(inp)) {
        is ParseResult.Ok -> {
            val next = r.next.peek()
            val boundary = next == null || next.isWhitespace() || !(next.isLetterOrDigit() || next == '_')
            if (boundary) ParseResult.Ok(kw, r.next)
            else ParseResult.Err(ParseError.expected(r.next.index, "word boundary after \"$kw\""), consumed = false)
        }
        is ParseResult.Err -> r
    }
}
