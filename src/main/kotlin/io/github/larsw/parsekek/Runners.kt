package io.github.larsw.parsekek

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import java.io.Reader

/**
 * Runs a parser on the whole of [source]. Input left over after the parser finishes is an error,
 * so `runParser(int, "42abc")` fails instead of returning 42. To parse only a prefix, call
 * `parser.parse(Input(source))` directly.
 *
 * @param parser The parser to run
 * @param source The source text to parse
 * @return Either containing the parsed value or parse error
 */
fun <A> runParser(parser: Parser<A>, source: String): Either<ParseError, A> =
    when (val r = (parser skipR eof).parse(Input(source))) {
        is ParseResult.Ok  -> r.value.right()
        is ParseResult.Err -> r.error.left()
    }

/**
 * Runs a parser on the whole of [source] and throws on failure. See [runParser].
 *
 * @param parser The parser to run
 * @param source The source text to parse
 * @return The parsed value
 * @throws ParseException if parsing fails
 */
fun <A> runOrThrow(parser: Parser<A>, source: String): A =
    runParser(parser, source).getOrElse { error ->
        val at = Input(source, error.index)
        throw ParseException(error, at.position(), error.pretty(source))
    }

/**
 * Runs a token parser on all of [tokens], typically the output of [tokenize].
 *
 * A failure's [ParseError.index] is a character index into the text the tokens came from (the
 * start of the offending token), and [ParseError.found] describes that token, so
 * `error.pretty(source)` points at the right place.
 *
 * @param parser The parser to run
 * @param tokens The tokens to parse
 * @return Either containing the parsed value or parse error
 */
fun <A> runParser(parser: TokenParser<A>, tokens: List<Token>): Either<ParseError, A> =
    when (val r = (parser skipR endOfInput()).parse(Input(tokens))) {
        is ParseResult.Ok  -> r.value.right()
        is ParseResult.Err -> {
            val token = tokens.getOrNull(r.error.index)
            val index = token?.span?.start ?: tokens.lastOrNull()?.span?.end ?: 0
            r.error.copy(index = index, found = token?.describe() ?: "end of input").left()
        }
    }

/**
 * Parses [reader] as a series of records, yielding each one as soon as it is parsed. Text is
 * read as the parser asks for it, and each record's text is dropped once the record is parsed,
 * so memory use depends on the largest record, not on the size of the input. This works on
 * input that never ends, such as a socket.
 *
 * The record parser has to consume whatever separates records, e.g. `line skipR char('\n')`
 * or `jsonValue.lexeme()`. Parsing ends when the input ends between two records. The sequence
 * can be iterated once; closing [reader] is up to the caller.
 *
 * While parsing, the record parser may read a little past the end of a record to see that it
 * ended, which can block until more input arrives.
 *
 * @param record Parser for one record
 * @param reader Where to read the text from
 * @return The parsed records, read lazily
 * @throws ParseException while iterating, if a record fails to parse. Its error's index counts
 *   from the start of that record; its position is the line and column in the whole input.
 */
fun <A> parseEach(record: Parser<A>, reader: Reader): Sequence<A> = parseEach(record, ReaderSource(reader))

internal fun <A> parseEach(record: Parser<A>, source: ReaderSource): Sequence<A> = sequence {
    while (source.has(0)) {
        when (val r = record.parse(Input(source, 0))) {
            is ParseResult.Ok -> {
                check(r.next.index > 0) { "parseEach: the record parser succeeded without consuming input" }
                source.discard(r.next.index)
                yield(r.value)
            }
            is ParseResult.Err -> {
                val at = Input(source, r.error.index)
                throw ParseException(r.error, at.position(), r.error.format(at, showIndex = false))
            }
        }
    }
}.constrainOnce()

/**
 * Thrown by [runOrThrow] and [parseEach]. The message is formatted like [ParseError.pretty].
 *
 * @property error The structured error
 * @property position The line and column of the error
 */
class ParseException(val error: ParseError, val position: Position, message: String) : RuntimeException(message)
