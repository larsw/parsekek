package io.github.larsw.parsekek

/**
 * Represents the result of a parsing operation.
 *
 * @param A The type of value produced by successful parsing
 */
sealed class ParseResult<out A> {
    /**
     * Successful parse result.
     *
     * @property value The parsed value
     * @property next The remaining input after parsing
     */
    data class Ok<A>(val value: A, val next: Input) : ParseResult<A>()

    /**
     * Failed parse result.
     *
     * @property error The parse error that occurred
     * @property consumed True if the parser consumed input before failing
     */
    data class Err(val error: ParseError, val consumed: Boolean) : ParseResult<Nothing>()
}

/**
 * Functional interface representing a parser that transforms input into a parse result.
 *
 * @param A The type of value this parser produces
 */
fun interface Parser<A> {
    /**
     * Attempts to parse the given input.
     *
     * @param input The input to parse
     * @return Parse result containing either success or failure
     */
    fun parse(input: Input): ParseResult<A>
}
