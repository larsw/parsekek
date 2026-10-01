package io.github.larsw.parsekek

/**
 * Represents the result of a parsing operation.
 *
 * @param T The element type of the input
 * @param A The type of value produced by successful parsing
 */
sealed class ParseResult<out T : Any, out A> {
    /**
     * Successful parse result.
     *
     * @property value The parsed value
     * @property next The remaining input after parsing
     * @property hint What else would have been accepted at [next], if anything. Combinators merge it
     *   into a later error at the same position, so that e.g. `digit.many() skipR eof` on "12x"
     *   reports `expected: digit | end of input` rather than only `end of input`.
     */
    data class Ok<out T : Any, out A>(val value: A, val next: Input<T>, val hint: ParseError? = null) :
        ParseResult<T, A>()

    /**
     * Failed parse result.
     *
     * @property error The parse error that occurred
     * @property consumed True if the parser consumed input before failing. [or] only tries its
     *   alternative, and [many] and [optional] only stop quietly, when this is false.
     */
    data class Err(val error: ParseError, val consumed: Boolean) : ParseResult<Nothing, Nothing>()
}

/**
 * A parser that reads elements of type [T] and produces a value of type [A]. Text parsers are
 * [Parser]s; parsers over the output of [tokenize] are [TokenParser]s. All combinators work on
 * either.
 *
 * @param T The element type this parser reads
 * @param A The type of value this parser produces
 */
fun interface GenericParser<T : Any, out A> {
    /**
     * Attempts to parse the given input.
     *
     * @param input The input to parse
     * @return Parse result containing either success or failure
     */
    fun parse(input: Input<T>): ParseResult<T, A>
}

/** A parser over text. */
typealias Parser<A> = GenericParser<Char, A>

/** A parser over the tokens produced by [tokenize]. */
typealias TokenParser<A> = GenericParser<Token, A>
