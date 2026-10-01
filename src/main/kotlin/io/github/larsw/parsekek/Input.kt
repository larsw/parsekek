package io.github.larsw.parsekek

/**
 * A position in a [Source]: what a parser reads from. Inputs are immutable, so a parser can
 * return to an earlier one to backtrack.
 *
 * @property source The elements being parsed
 * @property index The current position in the source (0-based)
 */
data class Input<out T : Any>(
    val source: Source<T>,
    val index: Int = 0
) {
    /** True if the parser has reached the end of the input */
    val isEof: Boolean get() = !source.has(index)

    /** Returns the element at the current position, or null if at EOF */
    fun peek(): T? = if (source.has(index)) source[index] else null

    /** Returns a new Input advanced by n elements */
    fun advance(n: Int = 1): Input<T> = Input(source, index + n)
}

/** Creates an input over [text], starting at [index]. */
fun Input(text: CharSequence, index: Int = 0): Input<Char> = Input(TextSource(text), index)

/** Creates an input over a list of elements, for example tokens, starting at [index]. */
fun <T : Any> Input(elements: List<T>, index: Int = 0): Input<T> = Input(ListSource(elements), index)

/** True if the text at this position starts with [prefix]. */
fun Input<Char>.startsWith(prefix: String): Boolean {
    val chars = chars
    for (i in prefix.indices) {
        if (!chars.has(index + i) || chars.charAt(index + i) != prefix[i]) return false
    }
    return true
}

/** The text from this position up to [end], which must be at or after it in the same source. */
fun Input<Char>.textUntil(end: Input<Char>): String = chars.slice(index, end.index)

/**
 * Computes the 1-based line and column of this input's [Input.index]. `\n`, `\r\n` and a lone
 * `\r` each end a line.
 */
fun Input<Char>.position(): Position {
    val chars = chars
    var (line, column) = chars.origin
    var i = 0
    while (i < index && chars.has(i)) {
        if (chars.isLineBreakAt(i)) {
            line += 1
            column = 1
        } else {
            column += 1
        }
        i += 1
    }
    return Position(line, column)
}

/** Returns the text of the line containing this input's [Input.index], without its line terminator. */
fun Input<Char>.currentLine(): String {
    val chars = chars
    var end = 0
    while (end < index && chars.has(end)) end += 1
    var start = end
    while (start > 0 && !chars.isLineBreakAt(start - 1)) start -= 1
    var stop = end
    while (chars.has(stop) && chars.charAt(stop) != '\n' && chars.charAt(stop) != '\r') stop += 1
    return chars.slice(start, stop)
}

/** A 1-based line and column in the source text. */
data class Position(val line: Int, val column: Int)
