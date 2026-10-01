package io.github.larsw.parsekek

/**
 * Applies this parser and then skips trailing whitespace.
 *
 * Parsers in this library never skip whitespace on their own. The usual pattern is to make every
 * token a lexeme and skip leading whitespace once, at the start: `spaces skipL grammar`.
 *
 * @param ws Parser for whitespace (defaults to spaces)
 * @return Parser that trims whitespace after parsing
 */
fun <A> Parser<A>.lexeme(ws: Parser<*> = spaces): Parser<A> = this skipR ws

/**
 * Skips leading whitespace before applying this parser.
 *
 * @param ws Parser for whitespace (defaults to spaces)
 * @return Parser that trims whitespace before parsing
 */
fun <A> Parser<A>.trimLeft(ws: Parser<*> = spaces): Parser<A> = ws skipL this

private fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '_'

private fun isIdentifierPart(c: Char): Boolean = c.isLetter() || c in '0'..'9' || c == '_'

/** Parser that matches a valid identifier (letter/underscore followed by letters/digits/underscores) */
val identifier: Parser<String> = Parser { inp ->
    val chars = inp.chars
    val start = inp.index
    if (!chars.has(start) || !isIdentifierStart(chars.charAt(start))) {
        return@Parser ParseResult.Err(ParseError.expected(start, "identifier"), consumed = false)
    }
    var end = start + 1
    while (chars.has(end) && isIdentifierPart(chars.charAt(end))) end += 1
    ParseResult.Ok(chars.slice(start, end), inp.advance(end - start))
}

/**
 * Creates a parser that matches a specific keyword with word boundary checking. Like [string],
 * it consumes nothing when it fails, so `keyword("in") or keyword("int")` works.
 *
 * @param kw The keyword to match
 * @return Parser that matches the keyword only when it is not followed by a letter, digit or '_'
 */
fun keyword(kw: String): Parser<String> = Parser { inp ->
    val chars = inp.chars
    val end = inp.index + kw.length
    val matches = inp.startsWith(kw) && !(chars.has(end) && isIdentifierPart(chars.charAt(end)))
    if (matches) ParseResult.Ok(kw, inp.advance(kw.length))
    else ParseResult.Err(ParseError.expected(inp.index, "\"$kw\""), consumed = false)
}
