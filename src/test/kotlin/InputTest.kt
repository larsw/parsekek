package io.github.larsw.parsekek

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class InputTest : FunSpec({

    test("Input should track position correctly") {
        val input = Input("hello", 0)
        input.index shouldBe 0
        input.isEof shouldBe false
    }

    test("Input.peek should return correct character") {
        val input = Input("abc", 1)
        input.peek() shouldBe 'b'
    }

    test("Input.peek should return null at EOF") {
        val input = Input("abc", 3)
        input.peek() shouldBe null
        input.isEof shouldBe true
    }

    test("Input.advance should move position") {
        val input = Input("hello", 2)
        val advanced = input.advance(2)
        advanced.index shouldBe 4
        advanced.peek() shouldBe 'o'
    }

    test("Input.startsWith and textUntil read text for custom parsers") {
        val input = Input("hello world", 6)
        input.startsWith("world") shouldBe true
        input.startsWith("worlds") shouldBe false
        input.textUntil(input.advance(3)) shouldBe "wor"
    }

    test("Inputs over equal text or lists are equal") {
        Input("abc", 1) shouldBe Input("abc", 1)
        Input(listOf(1, 2)) shouldBe Input(listOf(1, 2))
    }

    test("Input over a list reads its elements") {
        val input = Input(listOf("a", "b"))
        input.peek() shouldBe "a"
        input.advance(2).isEof shouldBe true
    }

    test("Input.position should compute correct line and column") {
        Input("line1\nline2\nline3", 8).position() shouldBe Position(2, 3)
    }

    test("Input.position on text without line breaks is line 1, column index + 1") {
        checkAll(Arb.string(0..50).map { it.filterNot { c -> c == '\n' || c == '\r' } }, Arb.int(0..60)) { text, i ->
            val index = minOf(i, text.length)
            Input(text, index).position() shouldBe Position(1, index + 1)
        }
    }

    test("Input.position handles edge cases") {
        Input("", 0).position() shouldBe Position(1, 1)
        Input("\n", 1).position() shouldBe Position(2, 1)
        Input("\n\n\n", 3).position() shouldBe Position(4, 1)
    }

    test("Input.position treats \\r\\n and lone \\r as one line break") {
        Input("ab\r\ncd", 4).position() shouldBe Position(2, 1)
        Input("ab\r\ncd", 5).position() shouldBe Position(2, 2)
        Input("a\rbc", 2).position() shouldBe Position(2, 1)
    }

    test("Input.currentLine returns the line at index without its terminator") {
        Input("one\ntwo\r\nthree", 5).currentLine() shouldBe "two"
        Input("one\ntwo\r\nthree", 10).currentLine() shouldBe "three"
        Input("a\rbc", 3).currentLine() shouldBe "bc"
        Input("abc\n", 4).currentLine() shouldBe ""
    }
})
