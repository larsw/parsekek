package io.github.larsw.parsekek

/** Parser that matches an optional sign (+/-), defaulting to positive */
val sign: Parser<Int> =
    (char('+').map { 1 } or char('-').map { -1 } or pure(1))

/** Parser that matches an unsigned integer */
val unsignedInt: Parser<Long> =
    digit.many1().map { it.joinToString("").toLong() }

/** Parser that matches a signed integer */
val int: Parser<Long> =
    sign.flatMap { s -> unsignedInt.map { it * s } }

/** Parser that matches floating-point numbers with optional exponent notation */
val double: Parser<Double> = Parser { inp ->
    val start = inp
    var cur = inp
    fun consumeDigits(): Boolean {
        var consumedAny = false
        while (true) {
            val c = cur.peek() ?: break
            if (c in '0'..'9') { cur = cur.advance(1); consumedAny = true } else break
        }
        return consumedAny
    }

    when (cur.peek()) {
        '+', '-' -> cur = cur.advance(1)
    }

    val intPart = consumeDigits()
    val dot = if (cur.peek() == '.') { cur = cur.advance(1); true } else false
    val fracPart = if (dot) consumeDigits() else false

    if (!intPart && !(dot && fracPart)) {
        return@Parser ParseResult.Err(ParseError.expected(cur.index, "double"), consumed = cur.index > start.index)
    }

    val e = cur.peek()
    if (e == 'e' || e == 'E') {
        val afterE = cur.advance(1)
        var tmp = afterE
        val sgn = tmp.peek()
        if (sgn == '+' || sgn == '-') tmp = tmp.advance(1)
        var expDigits = false
        while (true) {
            val c = tmp.peek() ?: break
            if (c in '0'..'9') { tmp = tmp.advance(1); expDigits = true } else break
        }
        if (!expDigits) {
            return@Parser ParseResult.Err(ParseError.expected(tmp.index, "exponent digits"), consumed = true)
        }
        cur = tmp
    }

    val lexeme = inp.text.substring(inp.index, cur.index)
    val value = lexeme.toDoubleOrNull()
        ?: return@Parser ParseResult.Err(ParseError.expected(cur.index, "double"), consumed = true)
    ParseResult.Ok(value, cur)
}
