package io.github.larsw.parsekek

import arrow.optics.optics
import kotlin.math.max

/**
 * Represents a parsing error with position information and expected alternatives.
 *
 * @property index The character index where the error occurred
 * @property expected Set of expected tokens or descriptions
 * @property message Optional custom error message
 */
@optics
data class ParseError(
    val index: Int,
    val expected: Set<String>,
    val message: String? = null
) {
    /**
     * Formats the error as a human-readable string with source context.
     *
     * @param source The original source text
     * @return Formatted error message with line/column info and source snippet
     */
    fun pretty(source: String): String {
        val (line, col) = Input(source, index).lineCol()
        val caretLine = " ".repeat(max(0, col - 1)) + "^"
        val exp = if (expected.isEmpty()) "" else "expected: ${expected.joinToString(" | ")}\n"
        val msg = message?.let { "message: $it\n" } ?: ""
        val snippet = source.lineSequence().drop(line - 1).firstOrNull().orEmpty()
        return buildString {
            appendLine("Parse error at line $line, column $col (index $index)")
            if (snippet.isNotEmpty()) appendLine(snippet)
            appendLine(caretLine)
            append(exp)
            append(msg)
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
