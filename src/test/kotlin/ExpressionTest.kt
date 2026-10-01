package io.github.larsw.parsekek

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

private fun evaluate(source: String): Double = eval(runOrThrow(expression, source))

class ExpressionTest : FunSpec({

    test("Expr evaluation should work correctly") {
        eval(Expr.Num(5.0)) shouldBe 5.0
        eval(Expr.Neg(Expr.Num(3.0))) shouldBe -3.0
        eval(Expr.Add(Expr.Num(2.0), Expr.Num(3.0))) shouldBe 5.0
        eval(Expr.Sub(Expr.Num(5.0), Expr.Num(2.0))) shouldBe 3.0
        eval(Expr.Mul(Expr.Num(3.0), Expr.Num(4.0))) shouldBe 12.0
        eval(Expr.Div(Expr.Num(8.0), Expr.Num(2.0))) shouldBe 4.0
    }

    test("expression parser should parse simple numbers") {
        evaluate("42") shouldBe 42.0
        evaluate("3.14") shouldBe 3.14
    }

    test("expression parser should handle whitespace") {
        evaluate("  42  ") shouldBe 42.0
    }

    test("expression parser should parse basic arithmetic") {
        evaluate("2 + 3") shouldBe 5.0
        evaluate("5 - 2") shouldBe 3.0
        evaluate("3 * 4") shouldBe 12.0
        evaluate("8 / 2") shouldBe 4.0
    }

    test("README example evaluates") {
        evaluate("3 + 4 * (2 - 1)") shouldBe 7.0
    }

    test("expression parser builds the AST with correct precedence") {
        runParser(expression, "2 + 3 * 4") shouldBeRight
            Expr.Add(Expr.Num(2.0), Expr.Mul(Expr.Num(3.0), Expr.Num(4.0)))
    }

    test("expression parser should respect operator precedence") {
        evaluate("2 + 3 * 4") shouldBe 14.0 // 2 + (3 * 4)
        evaluate("8 / 2 + 1") shouldBe 5.0 // (8 / 2) + 1
        evaluate("1 + 2 * 3 + 4") shouldBe 11.0 // 1 + (2 * 3) + 4
    }

    test("expression parser should handle left associativity") {
        evaluate("10 - 3 - 2") shouldBe 5.0 // (10 - 3) - 2
        evaluate("20 / 4 / 2") shouldBe 2.5 // (20 / 4) / 2
    }

    test("division is not truncated") {
        evaluate("7 / 2") shouldBe 3.5
        evaluate("1 / 3") shouldBe (1.0 / 3.0 plusOrMinus 1e-15)
    }

    test("expression parser should handle parentheses") {
        evaluate("(2 + 3) * 4") shouldBe 20.0
        evaluate("2 * (3 + 4)") shouldBe 14.0
        evaluate("((2 + 3) * 4) - 1") shouldBe 19.0
    }

    test("expression parser should handle unary minus") {
        evaluate("-5") shouldBe -5.0
        evaluate("-2 + 3") shouldBe 1.0
        evaluate("--5") shouldBe 5.0
        evaluate("---5") shouldBe -5.0
        evaluate("2 * -3") shouldBe -6.0
    }

    test("expression parser should handle unary plus") {
        evaluate("+5") shouldBe 5.0
        evaluate("+-5") shouldBe -5.0
    }

    test("expression parser should require EOF") {
        val error = runParser(expression, "2 + 3 extra").shouldBeLeft()
        error.index shouldBe 6
        error.expected shouldBe setOf("'*'", "'/'", "'+'", "'-'", "end of input")
    }

    test("expression parser should fail on invalid syntax") {
        runParser(expression, "2 +").shouldBeLeft().let {
            it.index shouldBe 3
            it.expected shouldBe setOf("one of [+-]", "number or '('")
        }
        runParser(expression, "(2 + 3").shouldBeLeft().expected shouldBe setOf("'*'", "'/'", "'+'", "'-'", "')'")
        runParser(expression, "2 + 3)").shouldBeLeft().index shouldBe 5
        runParser(expression, "").shouldBeLeft().index shouldBe 0
    }

    test("expression parser property test") {
        checkAll(Arb.double(-1000.0..1000.0)) { num ->
            if (num.isFinite()) {
                evaluate(num.toString()) shouldBe num
            }
        }
    }

    test("complex expression evaluation") {
        evaluate("  -3 + 4 * (2 - 0.5)  ") shouldBe 3.0 // -3 + 4 * 1.5 = -3 + 6 = 3
    }

    test("expression with multiple levels of nesting") {
        evaluate("(((-1 + 2) * 3) + 4) / 5") shouldBe 1.4 // ((1 * 3) + 4) / 5 = 7 / 5 = 1.4
    }
})
