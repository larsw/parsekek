package io.github.larsw.parsekek

import kotlin.math.max

/**
 * Represents the input state for parsing, containing the source text and current position.
 *
 * @property text The source text being parsed
 * @property index The current position in the text (0-based)
 */
data class Input(
    val text: String,
    val index: Int = 0
) {
    /** True if the parser has reached the end of the input */
    val isEof: Boolean get() = index >= text.length

    /** Returns the character at the current position, or null if at EOF */
    fun peek(): Char? = if (isEof) null else text[index]

    /** Returns a new Input advanced by n characters */
    fun advance(n: Int = 1): Input = copy(index = index + n)

    /** Compute 1-based line/col for error reporting. */
    fun lineCol(): Pair<Int, Int> {
        var line = 1
        var col = 1
        var i = 0
        while (i < max(0, index) && i < text.length) {
            val c = text[i]
            if (c == '\n') { line += 1; col = 1 } else { col += 1 }
            i += 1
        }
        return line to col
    }
}
