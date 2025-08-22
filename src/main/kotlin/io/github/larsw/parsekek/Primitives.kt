package io.github.larsw.parsekek

/**
 * Creates a parser that succeeds when the current character satisfies the predicate.
 *
 * @param name Description for error messages
 * @param pred Predicate to test characters against
 * @return Parser that matches satisfying characters
 */
fun satisfy(name: String, pred: (Char) -> Boolean): Parser<Char> = Parser { inp ->
    val c = inp.peek()
    when {
        c == null -> ParseResult.Err(ParseError.expected(inp.index, name), consumed = false)
        pred(c)   -> ParseResult.Ok(c, inp.advance(1))
        else      -> ParseResult.Err(ParseError.expected(inp.index, name), consumed = false)
    }
}

/**
 * Creates a parser that matches a specific character.
 *
 * @param ch The character to match
 * @return Parser that matches the specified character
 */
fun char(ch: Char): Parser<Char> = satisfy("'$ch'") { it == ch }

/**
 * Creates a parser that matches any character from the given string.
 *
 * @param chars String containing allowed characters
 * @return Parser that matches any of the specified characters
 */
fun oneOf(chars: String): Parser<Char> =
    satisfy("one of [$chars]") { it in chars }

/**
 * Creates a parser that matches any character not in the given string.
 *
 * @param chars String containing forbidden characters
 * @return Parser that matches characters not in the specified set
 */
fun noneOf(chars: String): Parser<Char> =
    satisfy("none of [$chars]") { it !in chars }

/**
 * Creates a parser that matches a specific string literal.
 *
 * @param lit The string literal to match
 * @return Parser that matches the exact string
 */
fun string(lit: String): Parser<String> = Parser { inp ->
    var i = 0
    var cur = inp
    while (i < lit.length) {
        val c = cur.peek()
        if (c == null || c != lit[i]) {
            return@Parser ParseResult.Err(ParseError.expected(cur.index, "\"$lit\""), consumed = i > 0)
        }
        cur = cur.advance(1)
        i += 1
    }
    ParseResult.Ok(lit, cur)
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
val spaces: Parser<String> = whitespace.many().map { it.joinToString("") }

/** Parser that matches one or more whitespace characters */
val spaces1: Parser<String> = whitespace.many1().map { it.joinToString("") }

/** Parser that succeeds only at end of file */
val eof: Parser<Unit> = Parser { inp ->
    if (inp.isEof) ParseResult.Ok(Unit, inp)
    else ParseResult.Err(ParseError.expected(inp.index, "EOF"), consumed = false)
}
