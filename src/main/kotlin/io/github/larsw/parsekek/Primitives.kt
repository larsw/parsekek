package io.github.larsw.parsekek

/**
 * Creates a parser that succeeds when the current character satisfies the predicate.
 *
 * @param name Description for error messages
 * @param pred Predicate to test characters against
 * @return Parser that matches satisfying characters
 */
fun satisfy(name: String, pred: (Char) -> Boolean): Parser<Char> = Parser { inp ->
    val chars = inp.chars
    if (chars.has(inp.index)) {
        val c = chars.charAt(inp.index)
        if (pred(c)) return@Parser ParseResult.Ok(c, inp.advance(1))
    }
    ParseResult.Err(ParseError.expected(inp.index, name), consumed = false)
}

/**
 * Creates a parser that matches one element of any input type, the counterpart of [satisfy]
 * for parsers that don't read text. [match] returns the value to produce, or null if the
 * element doesn't match: `token("number") { (it as? Token.Number)?.value }`.
 *
 * @param expected Description for error messages
 * @param match Function from an element to the parsed value, or null
 * @return Parser that matches one element
 */
fun <T : Any, A : Any> token(expected: String, match: (T) -> A?): GenericParser<T, A> = GenericParser { inp ->
    val value = inp.peek()?.let(match)
    if (value != null) ParseResult.Ok(value, inp.advance(1))
    else ParseResult.Err(ParseError.expected(inp.index, expected), consumed = false)
}

/**
 * Creates a parser that matches a specific character.
 *
 * @param ch The character to match
 * @return Parser that matches the specified character
 */
fun char(ch: Char): Parser<Char> = satisfy("'${escape(ch.toString())}'") { it == ch }

/**
 * Creates a parser that matches any character from the given string.
 *
 * @param chars String containing allowed characters
 * @return Parser that matches any of the specified characters
 */
fun oneOf(chars: String): Parser<Char> =
    satisfy("one of [${escape(chars)}]") { it in chars }

/**
 * Creates a parser that matches any character not in the given string.
 *
 * @param chars String containing forbidden characters
 * @return Parser that matches characters not in the specified set
 */
fun noneOf(chars: String): Parser<Char> =
    satisfy("none of [${escape(chars)}]") { it !in chars }

/**
 * Creates a parser that matches a specific string literal. It consumes nothing unless the whole
 * literal matches, so `string("let") or string("lambda")` works without [attempt].
 *
 * @param lit The string literal to match
 * @return Parser that matches the exact string
 */
fun string(lit: String): Parser<String> = Parser { inp ->
    if (inp.startsWith(lit)) ParseResult.Ok(lit, inp.advance(lit.length))
    else ParseResult.Err(ParseError.expected(inp.index, "\"${escape(lit)}\""), consumed = false)
}

/** Parser that matches any single character */
val anyChar: Parser<Char> = satisfy("any char") { true }

/** Parser that matches any digit character (0-9) */
val digit: Parser<Char> = satisfy("digit") { it in '0'..'9' }

/** Parser that matches any letter character */
val letter: Parser<Char> = satisfy("letter") { it.isLetter() }

/** Parser that matches any whitespace character */
val whitespace: Parser<Char> = satisfy("whitespace") { it.isWhitespace() }

/** Parser that matches zero or more whitespace characters */
val spaces: Parser<String> = Parser { inp ->
    val chars = inp.chars
    var end = inp.index
    while (chars.has(end) && chars.charAt(end).isWhitespace()) end += 1
    ParseResult.Ok(chars.slice(inp.index, end), inp.advance(end - inp.index))
}

/** Parser that matches one or more whitespace characters */
val spaces1: Parser<String> = (whitespace then spaces).map { (first, rest) -> "$first$rest" }

/** Parser that succeeds only at end of file */
val eof: Parser<Unit> = endOfInput()

// Shows line breaks and tabs in error messages as escapes rather than as themselves.
internal fun escape(text: String): String = buildString {
    for (c in text) {
        when (c) {
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
}
