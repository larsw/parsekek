package io.github.larsw.parsekek

/**
 * Sealed interface representing arithmetic expressions.
 */
sealed interface Expr {
    /** Numeric literal expression */
    data class Num(val value: Double) : Expr

    /** Unary negation expression */
    data class Neg(val expr: Expr) : Expr

    /** Addition expression */
    data class Add(val l: Expr, val r: Expr) : Expr

    /** Subtraction expression */
    data class Sub(val l: Expr, val r: Expr) : Expr

    /** Multiplication expression */
    data class Mul(val l: Expr, val r: Expr) : Expr

    /** Division expression */
    data class Div(val l: Expr, val r: Expr) : Expr
}

/**
 * Evaluates an arithmetic expression to a Double value.
 *
 * @param e The expression to evaluate
 * @return The numeric result of the expression
 */
fun eval(e: Expr): Double = when (e) {
    is Expr.Num -> e.value
    is Expr.Neg -> -eval(e.expr)
    is Expr.Add -> eval(e.l) + eval(e.r)
    is Expr.Sub -> eval(e.l) - eval(e.r)
    is Expr.Mul -> eval(e.l) * eval(e.r)
    is Expr.Div -> eval(e.l) / eval(e.r)
}

private val lparen = token(char('('))
private val rparen = token(char(')'))
private val plus   = token(char('+'))
private val minus  = token(char('-'))
private val star   = token(char('*'))
private val slash  = token(char('/'))

private val numberExpr: Parser<Expr> =
    token(double or int.map { it.toDouble() }).map { Expr.Num(it) }

private val atom: Parser<Expr> =
    numberExpr or between(lparen, rparen, lazyParser { expr })

private val prefix: Parser<Expr> = Parser { inp ->
    // handle unary +/- nesting, e.g., ---3
    var cur = inp
    var negations = 0
    while (true) {
        val r = (token(char('-')) or token(char('+'))).parse(cur)
        when (r) {
            is ParseResult.Ok -> {
                if (r.value == '-') negations += 1
                cur = r.next
            }
            is ParseResult.Err -> {
                if (r.consumed) return@Parser r
                break
            }
        }
    }
    return@Parser when (val after = atom.parse(cur)) {
        is ParseResult.Ok  -> {
            val base = after.value
            val res = if (negations % 2 == 0) base else Expr.Neg(base)
            ParseResult.Ok(res, after.next)
        }
        is ParseResult.Err -> after
    }
}

private fun infixLeft(term: Parser<Expr>, ops: List<Parser<(Expr, Expr) -> Expr>>): Parser<Expr> {
    val op = ops.reduce { a, b -> a or b }
    return term.flatMap { first ->
        (op.then(term)).many().map { tail ->
            tail.fold(first) { acc, (f, rhs) -> f(acc, rhs) }
        }
    }
}

private val factor: Parser<Expr> = prefix
private val term: Parser<Expr> = lazyParser {
    val mul: Parser<(Expr, Expr) -> Expr> = star.map { _: Char -> { l: Expr, r: Expr -> Expr.Mul(l, r) } }
    val div: Parser<(Expr, Expr) -> Expr> = slash.map { _: Char -> { l: Expr, r: Expr -> Expr.Div(l, r) } }
    infixLeft(factor, listOf(mul, div))
}
private val expr: Parser<Expr> = lazyParser {
    val add: Parser<(Expr, Expr) -> Expr> = plus.map { _: Char -> { l: Expr, r: Expr -> Expr.Add(l, r) } }
    val sub: Parser<(Expr, Expr) -> Expr> = minus.map { _: Char -> { l: Expr, r: Expr -> Expr.Sub(l, r) } }
    infixLeft(term, listOf(add, sub))
}

/** Public expression parser that trims both ends and requires EOF. */
val expression: Parser<Expr> = spaces.skipL(expr).skipR(spaces).skipR(eof)
