package io.github.larsw.parsekek

import java.math.BigDecimal
import java.math.BigInteger

/** Parser that matches an optional sign (+/-), defaulting to positive */
val sign: Parser<Int> =
    (char('+').map { 1 } or char('-').map { -1 } or pure(1))

/** Parser that matches an unsigned integer */
val unsignedInt: Parser<UInt> =
    digit.many1().map { it.joinToString("").toUInt() }

/** Parser that matches a signed integer */
val int: Parser<Int> =
    sign.flatMap { s -> unsignedInt.map { it.toInt() * s } }

val unsignedBigInteger: Parser<BigInteger> =
    digit.many1().map { it.joinToString("").toBigInteger() }

val bigInteger: Parser<BigInteger> =
    sign.flatMap { s -> unsignedBigInteger.map { it * s.toBigInteger() } }

val unsignedLong: Parser<ULong> =
    digit.many1().map { it.joinToString("").toULong() }

val long: Parser<Long> =
    sign.flatMap { s -> unsignedLong.map { it.toLong() * s } }

/** Parser that matches floating-point numbers with optional exponent notation */
val bigDecimal: Parser<BigDecimal> = Parser { inp ->
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
    val value = lexeme.toBigDecimalOrNull()
        ?: return@Parser ParseResult.Err(ParseError.expected(cur.index, "double"), consumed = true)
    ParseResult.Ok(value, cur)
}

val double: Parser<Double> =
    bigDecimal.map { it.toDouble() }

val float = bigDecimal.map { it.toFloat() }
