package io.github.larsw.parsekek

import java.math.BigDecimal
import java.math.BigInteger

// Number parsers come in pairs: the plain one accepts an optional leading '+' or '-', the
// unsigned one doesn't. All of them consume nothing when no digits follow, and fail (after
// consuming the literal) when the value doesn't fit the result type.

/** Parser that matches an optional sign (+/-), defaulting to positive */
val sign: Parser<Int> = Parser { inp ->
    when (inp.peek()) {
        '+' -> ParseResult.Ok(1, inp.advance(1))
        '-' -> ParseResult.Ok(-1, inp.advance(1))
        else -> ParseResult.Ok(1, inp)
    }
}

/** Parser that matches a signed integer that fits in an Int */
val int: Parser<Int> = number("integer", signed = true, decimal = false) { it.toIntOrNull() }

/** Parser that matches an unsigned integer that fits in an Int */
val unsignedInt: Parser<Int> = number("unsigned integer", signed = false, decimal = false) { it.toIntOrNull() }

/** Parser that matches a signed integer that fits in a Long */
val long: Parser<Long> = number("integer", signed = true, decimal = false) { it.toLongOrNull() }

/** Parser that matches an unsigned integer that fits in a Long */
val unsignedLong: Parser<Long> = number("unsigned integer", signed = false, decimal = false) { it.toLongOrNull() }

/** Parser that matches a signed integer of any size */
val bigInteger: Parser<BigInteger> = number("integer", signed = true, decimal = false) { it.toBigInteger() }

/** Parser that matches an unsigned integer of any size */
val unsignedBigInteger: Parser<BigInteger> =
    number("unsigned integer", signed = false, decimal = false) { it.toBigInteger() }

/**
 * Parser that matches a signed decimal number: digits with an optional fraction (`1.5`, `.5`)
 * and exponent (`1e10`, `2.5E-3`). A '.' or 'e' that isn't followed by digits is not part of
 * the number, so "1." parses as 1 and leaves the '.'.
 */
val bigDecimal: Parser<BigDecimal> = number("number", signed = true, decimal = true) { it.toBigDecimal() }

/** Like [bigDecimal], without a sign. */
val unsignedBigDecimal: Parser<BigDecimal> =
    number("unsigned number", signed = false, decimal = true) { it.toBigDecimal() }

/** Like [bigDecimal], as a Double. Values too large for a Double are an error, not Infinity. */
val double: Parser<Double> = number("number", signed = true, decimal = true) { it.toDouble().takeIf(Double::isFinite) }

/** Like [double], without a sign. */
val unsignedDouble: Parser<Double> =
    number("unsigned number", signed = false, decimal = true) { it.toDouble().takeIf(Double::isFinite) }

private fun <N : Any> number(name: String, signed: Boolean, decimal: Boolean, convert: (String) -> N?): Parser<N> =
    Parser { inp ->
        val chars = inp.chars
        val end = scanNumber(chars, inp.index, signed, decimal)
            ?: return@Parser ParseResult.Err(ParseError.expected(inp.index, name), consumed = false)
        val literal = chars.slice(inp.index, end)
        val value = convert(literal)
            ?: return@Parser ParseResult.Err(
                ParseError(inp.index, setOf(name), "$literal is out of range"),
                consumed = true
            )
        ParseResult.Ok(value, inp.advance(end - inp.index))
    }

/** Returns the end index of the numeric literal starting at [start], or null if there is none. */
private fun scanNumber(chars: CharSource, start: Int, signed: Boolean, decimal: Boolean): Int? {
    fun isAt(i: Int, c: Char) = chars.has(i) && chars.charAt(i) == c
    fun isDigit(i: Int) = chars.has(i) && chars.charAt(i) in '0'..'9'
    fun skipDigits(from: Int): Int {
        var i = from
        while (isDigit(i)) i += 1
        return i
    }

    var i = start
    if (signed && (isAt(i, '+') || isAt(i, '-'))) i += 1
    val intEnd = skipDigits(i)
    val hasIntDigits = intEnd > i
    i = intEnd
    if (!decimal) return if (hasIntDigits) i else null

    var hasFracDigits = false
    if (isAt(i, '.') && isDigit(i + 1)) {
        i = skipDigits(i + 1)
        hasFracDigits = true
    }
    if (!hasIntDigits && !hasFracDigits) return null

    if (isAt(i, 'e') || isAt(i, 'E')) {
        var j = i + 1
        if (isAt(j, '+') || isAt(j, '-')) j += 1
        val expEnd = skipDigits(j)
        if (expEnd > j) i = expEnd
    }
    return i
}
