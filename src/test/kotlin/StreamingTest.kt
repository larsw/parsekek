package io.github.larsw.parsekek

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.Reader
import java.io.StringReader

// A line of comma-separated integers, ending in '\n'
private val row: Parser<List<Int>> = sepBy1(int, char(',')) skipR char('\n')

// Counts how many characters have been read, to check that reading is lazy
private class CountingReader(private val inner: Reader) : Reader() {
    var read = 0
        private set

    override fun read(cbuf: CharArray, off: Int, len: Int): Int =
        inner.read(cbuf, off, len).also { if (it > 0) read += it }

    override fun close() = inner.close()
}

// "0\n1\n2\n..." forever
private class EndlessNumbers : Reader() {
    private var next = 0
    private var pending = ""

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (pending.isEmpty()) pending = "${next++}\n"
        val n = minOf(len, pending.length)
        pending.toCharArray(cbuf, off, 0, n)
        pending = pending.substring(n)
        return n
    }

    override fun close() {}
}

class StreamingTest : FunSpec({

    test("ReaderSource reads across chunk boundaries") {
        val source = ReaderSource(StringReader("hello world"), chunkSize = 3)

        source.has(10) shouldBe true
        source.has(11) shouldBe false
        source.slice(4, 9) shouldBe "o wor"
        source.charAt(10) shouldBe 'd'
    }

    test("ReaderSource.discard renumbers and keeps track of lines") {
        val source = ReaderSource(StringReader("ab\ncd\r\nef\rgh"), chunkSize = 2)
        source.has(20)

        source.discard(5) // "ab\ncd"
        source.charAt(0) shouldBe '\r'
        source.origin shouldBe Position(2, 3)

        source.discard(1) // "\r", which is followed by '\n': still line 2
        source.origin shouldBe Position(2, 4)

        source.discard(4) // "\nef\r"
        source.origin shouldBe Position(4, 1)
        source.slice(0, 2) shouldBe "gh"
    }

    test("text parsers work the same on streamed input") {
        val text = "if x1 == 42.5e1 then \"ok\"\n"
        for (chunkSize in listOf(1, 2, 3, 7, 8192)) {
            val source = ReaderSource(StringReader(text), chunkSize)
            val tokens = tokenize(setOf("if", "then"), setOf("==", "\"")).parse(Input(source))
            tokens.shouldBeInstanceOf<ParseResult.Ok<Char, List<Token>>>().value shouldBe
                runOrThrow(tokenize(setOf("if", "then"), setOf("==", "\"")), text)
        }
    }

    test("parseEach yields one value per record") {
        val text = "1,2,3\n4,5\n6\n"
        for (chunkSize in listOf(1, 2, 5, 8192)) {
            parseEach(row, ReaderSource(StringReader(text), chunkSize)).toList() shouldBe
                listOf(listOf(1, 2, 3), listOf(4, 5), listOf(6))
        }
    }

    test("parseEach on empty input yields nothing") {
        parseEach(row, StringReader("")).toList() shouldBe emptyList()
    }

    test("parseEach reads lazily") {
        val reader = CountingReader(StringReader((1..1000).joinToString("") { "$it\n" }))
        val first = parseEach(int skipR char('\n'), ReaderSource(reader, chunkSize = 16)).take(2).toList()

        first shouldBe listOf(1, 2)
        reader.read shouldBeLessThan 50
    }

    test("parseEach keeps memory proportional to a record, not the input") {
        val source = ReaderSource(StringReader((1..20_000).joinToString("") { "$it,$it,$it\n" }), chunkSize = 64)
        var largestBuffer = 0
        var count = 0
        for (r in parseEach(row, source)) {
            largestBuffer = maxOf(largestBuffer, source.buffered)
            count += 1
        }

        count shouldBe 20_000
        largestBuffer shouldBeLessThan 128
    }

    test("parseEach works on input that never ends") {
        val source = ReaderSource(EndlessNumbers(), chunkSize = 32)
        val numbers = parseEach(int skipR char('\n'), source).take(5_000).toList()

        numbers shouldBe (0 until 5_000).toList()
        source.buffered shouldBeLessThan 64
    }

    test("parseEach reports errors with their line and column in the whole input") {
        val text = "1,2\n3,4\n5,x\n"
        val e = shouldThrow<ParseException> { parseEach(row, StringReader(text)).toList() }

        e.position shouldBe Position(3, 3)
        e.error.index shouldBe 2 // counted from the start of the third record
        e.error.expected shouldBe setOf("integer")
        e.message shouldBe """
            Parse error at line 3, column 3
            5,x
              ^
            expected: integer
            found: 'x'
        """.trimIndent()
    }

    test("parseEach yields the records before a failure") {
        val parsed = mutableListOf<List<Int>>()
        shouldThrow<ParseException> { parseEach(row, StringReader("1\n2\n?\n")).forEach { parsed += it } }

        parsed shouldBe listOf(listOf(1), listOf(2))
    }

    test("parseEach rejects a record parser that consumes nothing") {
        shouldThrow<IllegalStateException> { parseEach(spaces, StringReader("x")).toList() }
    }

    test("parseEach can be iterated only once") {
        val records = parseEach(row, StringReader("1\n"))
        records.toList()
        shouldThrow<IllegalStateException> { records.toList() }
    }

    test("lexeme records separate themselves") {
        parseEach(int.lexeme(), StringReader("10 20\n30\t40")).toList() shouldBe listOf(10, 20, 30, 40)
    }

    test("error messages from parseEach have no index, since it would be relative to the record") {
        val e = shouldThrow<ParseException> { parseEach(row, StringReader("x\n")).toList() }
        e.message shouldContain "line 1, column 1"
        e.message shouldNotContain "index"
    }
})
