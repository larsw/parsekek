package io.github.larsw.parsekek

/**
 * Represents a parsing error with position information and expected alternatives.
 *
 * @property index The character index where the error occurred
 * @property expected Set of expected tokens or descriptions
 * @property message Optional custom error message
 * @property found What was at [index], when the runner knows better than the character there.
 *   [runParser] on tokens sets it to the offending token; otherwise it is null.
 */
data class ParseError(
    val index: Int,
    val expected: Set<String>,
    val message: String? = null,
    val found: String? = null
) {
    /**
     * Combines two errors. The one further into the input wins; at the same index the expected
     * sets are joined.
     */
    fun merge(other: ParseError): ParseError = when {
        index > other.index -> this
        other.index > index -> other
        else -> ParseError(index, expected + other.expected, message ?: other.message)
    }

    /**
     * Formats the error as a human-readable string with source context.
     *
     * @param source The original source text
     * @return Formatted error message with line/column info and source snippet
     */
    fun pretty(source: String): String = format(Input(source, index), showIndex = true)

    // `at` is this error's position in the text that failed to parse.
    internal fun format(at: Input<Char>, showIndex: Boolean): String {
        val (line, column) = at.position()
        val snippet = at.currentLine()
        // Copy tabs from the snippet so the caret lines up however the terminal renders them
        val caret = snippet.take(column - 1).map { if (it == '\t') '\t' else ' ' }.joinToString("") + "^"
        return buildString {
            append("Parse error at line $line, column $column")
            if (showIndex) append(" (index $index)")
            appendLine()
            if (snippet.isNotEmpty()) appendLine(snippet)
            appendLine(caret)
            if (expected.isNotEmpty()) appendLine("expected: ${expected.joinToString(" | ")}")
            appendLine("found: ${found ?: describe(at.peek())}")
            message?.let { appendLine("message: $it") }
        }.trimEnd()
    }

    companion object {
        /**
         * Creates a ParseError with expected tokens.
         *
         * @param index The position where the error occurred
         * @param exp Variable number of expected token descriptions
         * @return New ParseError instance
         */
        fun expected(index: Int, vararg exp: String): ParseError =
            ParseError(index, exp.toSet(), null)
    }
}

private fun describe(c: Char?): String = when (c) {
    null -> "end of input"
    '\n', '\r' -> "end of line"
    '\t' -> "tab"
    else -> "'$c'"
}

internal fun merge(a: ParseError?, b: ParseError): ParseError = a?.merge(b) ?: b

internal fun mergeHints(a: ParseError?, b: ParseError?): ParseError? = if (b == null) a else merge(a, b)
