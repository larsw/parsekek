package io.github.larsw.parsekek

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

class ExpressionTest : FunSpec({

    test("Expr evaluation should work correctly") {
        eval(Expr.Num.of(5.0)) shouldBe 5.0
        eval(Expr.Neg(Expr.Num.of(3.0))) shouldBe -3.0
        eval(Expr.Add(Expr.Num.of(2.0), Expr.Num.of(3.0))) shouldBe 5.0
        eval(Expr.Sub(Expr.Num.of(5.0), Expr.Num.of(2.0))) shouldBe 3.0
        eval(Expr.Mul(Expr.Num.of(3.0), Expr.Num.of(4.0))) shouldBe 12.0
        eval(Expr.Div(Expr.Num.of(8.0), Expr.Num.of(2.0))) shouldBe 4.0
    }

    test("expression parser should parse simple numbers") {
        val result = expression.parse(Input("42", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(result.value) shouldBe 42.0

        val floatResult = expression.parse(Input("3.14", 0))
        floatResult.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(floatResult.value) shouldBe 3.14
    }

    test("expression parser should handle whitespace") {
        val result = expression.parse(Input("  42  ", 0))
        result.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(result.value) shouldBe 42.0
    }

    test("expression parser should parse basic arithmetic") {
        val add = expression.parse(Input("2 + 3", 0))
        add.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(add.value) shouldBe 5.0

        val sub = expression.parse(Input("5 - 2", 0))
        sub.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(sub.value) shouldBe 3.0

        val mul = expression.parse(Input("3 * 4", 0))
        mul.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(mul.value) shouldBe 12.0

        val div = expression.parse(Input("8 / 2", 0))
        div.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(div.value) shouldBe 4.0
    }

    test("expression parser should respect operator precedence") {
        val result1 = expression.parse(Input("2 + 3 * 4", 0))
        result1.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(result1.value) shouldBe 14.0 // 2 + (3 * 4)

        val result2 = expression.parse(Input("8 / 2 + 1", 0))
        result2.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(result2.value) shouldBe 5.0 // (8 / 2) + 1

        val result3 = expression.parse(Input("1 + 2 * 3 + 4", 0))
        result3.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(result3.value) shouldBe 11.0 // 1 + (2 * 3) + 4
    }

    test("expression parser should handle left associativity") {
        val sub = expression.parse(Input("10 - 3 - 2", 0))
        sub.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(sub.value) shouldBe 5.0 // (10 - 3) - 2

        val div = expression.parse(Input("20 / 4 / 2", 0))
        div.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(div.value) shouldBe 2.5 // (20 / 4) / 2
    }

    test("expression parser should handle parentheses") {
        val result1 = expression.parse(Input("(2 + 3) * 4", 0))
        result1.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(result1.value) shouldBe 20.toBigDecimal() // (2 + 3) * 4

        val result2 = expression.parse(Input("2 * (3 + 4)", 0))
        result2.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(result2.value) shouldBe 14.toBigDecimal() // 2 * (3 + 4)

        val nested = expression.parse(Input("((2 + 3) * 4) - 1", 0))
        nested.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(nested.value) shouldBe 19.toBigDecimal() // ((2 + 3) * 4) - 1
    }

    test("expression parser should handle unary minus") {
        val simple = expression.parse(Input("-5", 0))
        simple.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(simple.value) shouldBe -5.0

        val complex = expression.parse(Input("-2 + 3", 0))
        complex.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(complex.value) shouldBe 1.0

        val double = expression.parse(Input("--5", 0))
        double.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(double.value) shouldBe 5.0 // double negative

        val triple = expression.parse(Input("---5", 0))
        triple.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(triple.value) shouldBe -5.0 // triple negative
    }

    test("expression parser should handle unary plus") {
        val simple = expression.parse(Input("+5", 0))
        simple.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(simple.value) shouldBe 5.0

        val mixed = expression.parse(Input("+-5", 0))
        mixed.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(mixed.value) shouldBe -5.0
    }

    test("expression parser should require EOF") {
        val result = expression.parse(Input("2 + 3 extra", 0))
        result.shouldBeInstanceOf<ParseResult.Err>()
    }

    test("expression parser should fail on invalid syntax") {
        expression.parse(Input("2 +", 0)).shouldBeInstanceOf<ParseResult.Err>()
        expression.parse(Input("(2 + 3", 0)).shouldBeInstanceOf<ParseResult.Err>()
        expression.parse(Input("2 + 3)", 0)).shouldBeInstanceOf<ParseResult.Err>()
        expression.parse(Input("", 0)).shouldBeInstanceOf<ParseResult.Err>()
    }

    test("expression parser property test") {
        checkAll(Arb.double(-1000.0..1000.0)) { num ->
            if (num.isFinite()) {
                val result = expression.parse(Input(num.toString(), 0))
                result.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
                eval(result.value) shouldBe num
            }
        }
    }

    test("complex expression evaluation") {
        val complex = expression.parse(Input("  -3 + 4 * (2 - 0.5)  ", 0))
        complex.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(complex.value) shouldBe 3.0 // -3 + 4 * 1.5 = -3 + 6 = 3
    }

    test("expression with multiple levels of nesting") {
        val nested = expression.parse(Input("(((-1 + 2) * 3) + 4) / 5", 0))
        nested.shouldBeInstanceOf<ParseResult.Ok<Expr>>()
        eval(nested.value) shouldBe 1.4 // ((1 * 3) + 4) / 5 = 7 / 5 = 1.4
    }
})
