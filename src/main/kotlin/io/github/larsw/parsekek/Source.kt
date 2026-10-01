package io.github.larsw.parsekek

import java.io.Reader

/**
 * The elements a parser reads, addressed by index: characters for text, tokens for a token
 * list. A source may produce its elements lazily, for example by reading from a stream, but
 * must return the same element for an index every time.
 *
 * @param T The element type
 */
interface Source<out T : Any> {
    /** True if there is an element at [index]. A streaming source may block here to read more. */
    fun has(index: Int): Boolean

    /** The element at [index]. Only valid after [has] returned true for it. */
    operator fun get(index: Int): T
}

// Text sources expose characters without boxing them, which the character-level parsers rely on.
internal interface CharSource : Source<Char> {
    fun charAt(index: Int): Char

    /** The text in `[start, end)`. Every index in the range must be available. */
    fun slice(start: Int, end: Int): String

    /** The line and column of index 0. */
    val origin: Position get() = Position(1, 1)

    override fun get(index: Int): Char = charAt(index)
}

internal data class TextSource(private val text: CharSequence) : CharSource {
    override fun has(index: Int): Boolean = index < text.length
    override fun charAt(index: Int): Char = text[index]
    override fun slice(start: Int, end: Int): String = text.substring(start, end)
}

internal data class ListSource<T : Any>(private val elements: List<T>) : Source<T> {
    override fun has(index: Int): Boolean = index < elements.size
    override fun get(index: Int): T = elements[index]
}

// Lets the character parsers run on a Source<Char> that isn't a CharSource, e.g. a List<Char>.
private class BoxedCharSource(private val source: Source<Char>) : CharSource {
    override fun has(index: Int): Boolean = source.has(index)
    override fun charAt(index: Int): Char = source[index]
    override fun slice(start: Int, end: Int): String = buildString { for (i in start until end) append(source[i]) }
}

internal val Input<Char>.chars: CharSource
    get() = source as? CharSource ?: BoxedCharSource(source)

/**
 * Reads characters from [reader] a chunk at a time, as parsers ask for them, and keeps them
 * until [discard] drops them. [parseEach] discards each record once it is parsed, so memory
 * stays proportional to the largest record rather than the whole input.
 */
internal class ReaderSource(private val reader: Reader, chunkSize: Int = 8192) : CharSource {
    private val buffer = StringBuilder()
    private val chunk = CharArray(chunkSize)
    private var exhausted = false

    override var origin: Position = Position(1, 1)
        private set

    /** Number of characters currently held in memory. */
    val buffered: Int get() = buffer.length

    override fun has(index: Int): Boolean {
        while (index >= buffer.length && !exhausted) {
            val n = reader.read(chunk)
            if (n < 0) exhausted = true else buffer.appendRange(chunk, 0, n)
        }
        return index < buffer.length
    }

    override fun charAt(index: Int): Char = buffer[index]

    override fun slice(start: Int, end: Int): String = buffer.substring(start, end)

    /** Drops the first [count] characters. Afterwards, what was index [count] is index 0. */
    fun discard(count: Int) {
        origin = advance(origin, count)
        buffer.delete(0, count)
    }

    private fun advance(from: Position, count: Int): Position {
        var (line, column) = from
        for (i in 0 until count) {
            if (isLineBreakAt(i)) {
                line += 1
                column = 1
            } else {
                column += 1
            }
        }
        return Position(line, column)
    }
}

/**
 * A '\n', or a '\r' not directly followed by '\n', ends a line. In "\r\n" only the '\n' counts,
 * so the pair is one line break.
 */
internal fun CharSource.isLineBreakAt(index: Int): Boolean {
    val c = charAt(index)
    return c == '\n' || (c == '\r' && !(has(index + 1) && charAt(index + 1) == '\n'))
}
