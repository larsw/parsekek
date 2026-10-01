package io.github.larsw.parsekek

// An arithmetic expression parser built from the library's combinators. It lives in the test
// sources because it is an example, not part of the library: the README walks through it.

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

private fun op(symbol: Char, build: (Expr, Expr) -> Expr): Parser<(Expr, Expr) -> Expr> =
    char(symbol).lexeme().map { build }

private val number: Parser<Expr> = unsignedDouble.lexeme().map(Expr::Num)

private val parenthesized: Parser<Expr> =
    between(char('(').lexeme(), char(')').lexeme(), lazyParser { sum })

// Any number of prefix signs, e.g. "--3". An odd number of '-' negates.
private val factor: Parser<Expr> = parser {
    val signs = oneOf("+-").lexeme().many().bind()
    val operand = (number or parenthesized).label("number or '('").bind()
    if (signs.count { it == '-' } % 2 == 1) Expr.Neg(operand) else operand
}

private val product: Parser<Expr> = chainl1(factor, op('*', Expr::Mul) or op('/', Expr::Div))

private val sum: Parser<Expr> = chainl1(product, op('+', Expr::Add) or op('-', Expr::Sub))

/** Parses an arithmetic expression, allowing whitespace around it. Use with [runParser]. */
val expression: Parser<Expr> = spaces skipL sum
