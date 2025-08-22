package io.github.larsw.parsekek

import arrow.core.Either
import arrow.core.left
import arrow.core.right

/**
 * Runs a parser on source text and returns an Either result.
 *
 * @param parser The parser to run
 * @param source The source text to parse
 * @return Either containing the parsed value or parse error
 */
fun <A> runParser(parser: Parser<A>, source: String): Either<ParseError, A> =
    when (val r = parser.parse(Input(source))) {
        is ParseResult.Ok  -> r.value.right()
        is ParseResult.Err -> r.error.left()
    }

/**
 * Runs a parser on source text and throws an exception on failure.
 *
 * @param parser The parser to run
 * @param source The source text to parse
 * @return The parsed value
 * @throws IllegalArgumentException if parsing fails
 */
fun <A> runOrThrow(parser: Parser<A>, source: String): A =
    when (val r = parser.parse(Input(source))) {
        is ParseResult.Ok  -> r.value
        is ParseResult.Err -> throw IllegalArgumentException(r.error.pretty(source))
    }
