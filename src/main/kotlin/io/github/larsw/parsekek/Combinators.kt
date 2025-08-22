package io.github.larsw.parsekek

/**
 * Maps the result of this parser using the given function.
 *
 * @param f Function to transform the parsed value
 * @return Parser that produces the transformed value
 */
fun <A, B> Parser<A>.map(f: (A) -> B): Parser<B> = Parser { inp ->
    when (val r = this.parse(inp)) {
        is ParseResult.Ok  -> ParseResult.Ok(f(r.value), r.next)
        is ParseResult.Err -> r
    }
}

/**
 * Monadic bind operation for parsers.
 *
 * @param f Function that takes the parsed value and returns a new parser
 * @return Parser that sequences this parser with the result of f
 */
fun <A, B> Parser<A>.flatMap(f: (A) -> Parser<B>): Parser<B> = Parser { inp ->
    when (val r = this.parse(inp)) {
        is ParseResult.Ok  -> f(r.value).parse(r.next)
        is ParseResult.Err -> r
    }
}

/**
 * Sequences two parsers, keeping both results as a pair.
 *
 * @param other The parser to run after this one
 * @return Parser that produces a pair of both results
 */
infix fun <A, B> Parser<A>.then(other: Parser<B>): Parser<Pair<A, B>> =
    this.flatMap { a -> other.map { b -> a to b } }

/**
 * Sequences two parsers, keeping only the result of the second.
 *
 * @param other The parser whose result to keep
 * @return Parser that produces only the second result
 */
infix fun <A, B> Parser<A>.skipL(other: Parser<B>): Parser<B> =
    (this then other).map { it.second }

/**
 * Sequences two parsers, keeping only the result of the first.
 *
 * @param other The parser whose result to discard
 * @return Parser that produces only the first result
 */
infix fun <A, B> Parser<A>.skipR(other: Parser<B>): Parser<A> =
    (this then other).map { it.first }

/**
 * Creates a choice between two parsers with backtracking control.
 * Tries the second parser only if the first fails without consuming input.
 *
 * @param other The alternative parser to try
 * @return Parser that tries this parser first, then the alternative
 */
infix fun <A> Parser<A>.or(other: Parser<A>): Parser<A> = Parser { inp ->
    when (val r = this.parse(inp)) {
        is ParseResult.Ok -> r
        is ParseResult.Err ->
            if (r.consumed) r
            else when (val r2 = other.parse(inp)) {
                is ParseResult.Ok -> r2
                is ParseResult.Err -> {
                    val e1 = r.error
                    val e2 = r2.error
                    val merged =
                        if (e2.index > e1.index) e2
                        else if (e1.index > e2.index) e1
                        else ParseError(
                            index = e1.index,
                            expected = e1.expected + e2.expected,
                            message = e1.message ?: e2.message
                        )
                    ParseResult.Err(merged, consumed = false)
                }
            }
    }
}

/**
 * Makes this parser optional, returning null if it fails.
 *
 * @return Parser that produces the original result or null
 */
fun <A> Parser<A>.optional(): Parser<A?> = Parser { inp ->
    when (val r = this.parse(inp)) {
        is ParseResult.Ok  -> ParseResult.Ok<A?>(r.value, r.next)
        is ParseResult.Err -> ParseResult.Ok(null, inp)
    }
}

/**
 * Applies this parser zero or more times, collecting results in a list.
 *
 * @return Parser that produces a list of zero or more results
 */
fun <A> Parser<A>.many(): Parser<List<A>> = Parser { inp ->
    tailrec fun go(cur: Input, out: MutableList<A>): ParseResult<List<A>> {
        return when (val r = this.parse(cur)) {
            is ParseResult.Ok -> {
                if (r.next.index == cur.index) {
                    ParseResult.Err(
                        ParseError(r.next.index, setOf("progress")),
                        consumed = true
                    )
                } else {
                    out += r.value
                    go(r.next, out)
                }
            }
            is ParseResult.Err -> {
                if (r.consumed) r else ParseResult.Ok(out.toList(), cur)
            }
        }
    }
    go(inp, mutableListOf())
}

/**
 * Applies this parser one or more times, collecting results in a list.
 *
 * @return Parser that produces a list of one or more results
 */
fun <A> Parser<A>.many1(): Parser<List<A>> =
    this.many().flatMap { list -> if (list.isNotEmpty()) pure(list) else fail("one or more items") }

/**
 * Parses content between left and right delimiters.
 *
 * @param left Parser for the opening delimiter
 * @param right Parser for the closing delimiter
 * @param center Parser for the content between delimiters
 * @return Parser that produces the center content
 */
fun <A, B> between(left: Parser<B>, right: Parser<B>, center: Parser<A>): Parser<A> =
    left.skipL(center).skipR(right)

/**
 * Parses zero or more elements separated by a separator.
 *
 * @param element Parser for individual elements
 * @param sep Parser for the separator
 * @return Parser that produces a list of elements
 */
fun <A, S> sepBy(element: Parser<A>, sep: Parser<S>): Parser<List<A>> =
    element.flatMap { first ->
        (sep.skipL(element)).many().map { rest -> listOf(first) + rest }
    } or pure(emptyList())

/**
 * Parses one or more elements separated by a separator.
 *
 * @param element Parser for individual elements
 * @param sep Parser for the separator
 * @return Parser that produces a non-empty list of elements
 */
fun <A, S> sepBy1(element: Parser<A>, sep: Parser<S>): Parser<List<A>> =
    element.flatMap { first ->
        (sep.skipL(element)).many().map { rest -> listOf(first) + rest }
    }

/**
 * Creates a parser that always succeeds with the given value.
 *
 * @param a The value to return
 * @return Parser that produces the given value without consuming input
 */
fun <A> pure(a: A): Parser<A> = Parser { inp -> ParseResult.Ok(a, inp) }

/**
 * Creates a parser that always fails with the given error.
 *
 * @param expected Description of what was expected
 * @param message Optional error message
 * @return Parser that always fails
 */
fun <A> fail(expected: String, message: String? = null): Parser<A> = Parser { inp ->
    ParseResult.Err(ParseError(inp.index, setOf(expected), message), consumed = false)
}

/**
 * Creates a lazy parser for handling recursive grammars.
 *
 * @param build Function that constructs the parser when needed
 * @return Parser that defers construction until parse time
 */
fun <A> lazyParser(build: () -> Parser<A>): Parser<A> = Parser { inp -> build().parse(inp) }
