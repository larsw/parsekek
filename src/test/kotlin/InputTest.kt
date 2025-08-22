package io.github.larsw.parsekek

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class InputTest : FunSpec({

    test("Input should track position correctly") {
        val input = Input("hello", 0)
        input.index shouldBe 0
        input.text shouldBe "hello"
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
        advanced.text shouldBe "hello"
    }

    test("Input.lineCol should compute correct line and column") {
        val input = Input("line1\nline2\nline3", 8)
        val (line, col) = input.lineCol()
        line shouldBe 2
        col shouldBe 3
    }

    test("Input.lineCol with newlines property test") {
        checkAll(Arb.string()) { text ->
            val input = Input(text, 0)
            val (line, col) = input.lineCol()
            line shouldBe 1
            col shouldBe 1
        }
    }

    test("Input.lineCol handles edge cases") {
        // Empty string
        Input("", 0).lineCol() shouldBe (1 to 1)

        // Single newline
        Input("\n", 1).lineCol() shouldBe (2 to 1)

        // Multiple newlines
        Input("\n\n\n", 3).lineCol() shouldBe (4 to 1)
    }
})
