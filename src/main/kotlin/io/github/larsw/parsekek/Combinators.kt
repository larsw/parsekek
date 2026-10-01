package io.github.larsw.parsekek

/**
 * Maps the result of this parser using the given function.
 *
 * @param f Function to transform the parsed value
 * @return Parser that produces the transformed value
 */
fun <T : Any, A, B> GenericParser<T, A>.map(f: (A) -> B): GenericParser<T, B> = GenericParser { inp ->
    when (val r = this.parse(inp)) {
        is ParseResult.Ok  -> ParseResult.Ok(f(r.value), r.next, r.hint)
        is ParseResult.Err -> r
    }
}

/**
 * Monadic bind operation for parsers. If this parser consumed input, a failure of the parser
 * returned by [f] counts as consumed too, so an enclosing [or] won't backtrack past it.
 *
 * @param f Function that takes the parsed value and returns a new parser
 * @return Parser that sequences this parser with the result of f
 */
fun <T : Any, A, B> GenericParser<T, A>.flatMap(f: (A) -> GenericParser<T, B>): GenericParser<T, B> =
    GenericParser { inp ->
        when (val first = this.parse(inp)) {
            is ParseResult.Err -> first
            is ParseResult.Ok -> when (val second = f(first.value).parse(first.next)) {
                is ParseResult.Ok ->
                    if (second.next.index == first.next.index) second.copy(hint = mergeHints(first.hint, second.hint))
                    else second
                is ParseResult.Err ->
                    if (second.consumed) second
                    else ParseResult.Err(merge(first.hint, second.error), consumed = first.next.index > inp.index)
            }
        }
    }

/**
 * Sequences two parsers, keeping both results as a pair.
 *
 * @param other The parser to run after this one
 * @return Parser that produces a pair of both results
 */
infix fun <T : Any, A, B> GenericParser<T, A>.then(other: GenericParser<T, B>): GenericParser<T, Pair<A, B>> =
    this.flatMap { a -> other.map { b -> a to b } }

/**
 * Sequences two parsers, keeping only the result of the second.
 *
 * @param other The parser whose result to keep
 * @return Parser that produces only the second result
 */
infix fun <T : Any, B> GenericParser<T, *>.skipL(other: GenericParser<T, B>): GenericParser<T, B> =
    this.flatMap { other }

/**
 * Sequences two parsers, keeping only the result of the first.
 *
 * @param other The parser whose result to discard
 * @return Parser that produces only the first result
 */
infix fun <T : Any, A> GenericParser<T, A>.skipR(other: GenericParser<T, *>): GenericParser<T, A> =
    this.flatMap { a -> other.map { a } }

/**
 * Creates a choice between two parsers with backtracking control.
 * Tries the second parser only if the first fails without consuming input. Wrap the first
 * parser in [attempt] to try the second even when the first consumed input.
 *
 * @param other The alternative parser to try
 * @return Parser that tries this parser first, then the alternative
 */
infix fun <T : Any, A> GenericParser<T, A>.or(other: GenericParser<T, A>): GenericParser<T, A> = GenericParser { inp ->
    when (val left = this.parse(inp)) {
        is ParseResult.Ok -> left
        is ParseResult.Err ->
            if (left.consumed) left
            else when (val right = other.parse(inp)) {
                is ParseResult.Ok ->
                    if (right.next.index == inp.index) right.copy(hint = mergeHints(left.error, right.hint))
                    else right
                is ParseResult.Err ->
                    if (right.consumed) right
                    else ParseResult.Err(left.error.merge(right.error), consumed = false)
            }
    }
}

/**
 * Tries each parser in order, like chaining them with [or].
 *
 * @param alternatives The parsers to try; at least one
 * @return Parser that produces the result of the first alternative that succeeds
 */
fun <T : Any, A> choice(vararg alternatives: GenericParser<T, A>): GenericParser<T, A> {
    require(alternatives.isNotEmpty()) { "choice needs at least one alternative" }
    return alternatives.reduce { acc, p -> acc or p }
}

/**
 * Runs [p], and if it fails after consuming input, pretends it consumed nothing. This is how to
 * opt into backtracking: `attempt(string("ab") then char('c')) or string("abd")`.
 *
 * @param p The parser to run
 * @return Parser that never fails with consumed input
 */
fun <T : Any, A> attempt(p: GenericParser<T, A>): GenericParser<T, A> = GenericParser { inp ->
    when (val r = p.parse(inp)) {
        is ParseResult.Ok  -> r
        is ParseResult.Err -> if (r.consumed) r.copy(consumed = false) else r
    }
}

/**
 * Runs [p] without consuming input. Succeeds with its value if it succeeds.
 *
 * @param p The parser to run
 * @return Parser that looks ahead without moving the input
 */
fun <T : Any, A> lookAhead(p: GenericParser<T, A>): GenericParser<T, A> = GenericParser { inp ->
    when (val r = p.parse(inp)) {
        is ParseResult.Ok  -> ParseResult.Ok(r.value, inp)
        is ParseResult.Err -> r
    }
}

/**
 * Succeeds, without consuming input, only if [p] fails here. Useful for word boundaries:
 * `string("if") skipR notFollowedBy(letter)`.
 *
 * @param p The parser that must not match
 * @return Parser that produces Unit
 */
fun <T : Any> notFollowedBy(p: GenericParser<T, *>): GenericParser<T, Unit> = GenericParser { inp ->
    when (val r = p.parse(inp)) {
        is ParseResult.Ok -> {
            val matched = describeRange(inp, r.next.index)
            ParseResult.Err(ParseError(inp.index, emptySet(), "unexpected $matched"), consumed = false)
        }
        is ParseResult.Err -> ParseResult.Ok(Unit, inp)
    }
}

// Text is quoted as written; other elements are listed.
private fun describeRange(start: Input<*>, end: Int): String {
    val source = start.source
    if (source is CharSource) return "\"${escape(source.slice(start.index, end))}\""
    return (start.index until end).joinToString(" ") { i ->
        val element = source[i]
        if (element is Token) element.describe() else element.toString()
    }
}

/**
 * Names this parser in error messages. When it fails without consuming input, the error
 * expects [name] instead of whatever the parser's parts expected.
 *
 * @param name What to call this parser, e.g. "expression"
 * @return Parser with a friendlier error message
 */
fun <T : Any, A> GenericParser<T, A>.label(name: String): GenericParser<T, A> = GenericParser { inp ->
    when (val r = this.parse(inp)) {
        is ParseResult.Ok ->
            if (r.next.index == inp.index && r.hint != null) r.copy(hint = r.hint.copy(expected = setOf(name)))
            else r
        is ParseResult.Err ->
            if (!r.consumed && r.error.index == inp.index) r.copy(error = r.error.copy(expected = setOf(name)))
            else r
    }
}

/**
 * Makes this parser optional, returning null if it fails without consuming input. A failure
 * after consuming input is still a failure.
 *
 * @return Parser that produces the original result or null
 */
fun <T : Any, A> GenericParser<T, A>.optional(): GenericParser<T, A?> = GenericParser { inp ->
    when (val r = this.parse(inp)) {
        is ParseResult.Ok  -> r
        is ParseResult.Err -> if (r.consumed) r else ParseResult.Ok(null, inp, r.error)
    }
}

/**
 * Applies this parser zero or more times, collecting results in a list. Stops when the parser
 * fails without consuming input; a failure after consuming input fails the whole parser.
 *
 * @return Parser that produces a list of zero or more results
 * @throws IllegalStateException at parse time if this parser succeeds without consuming input,
 *   since repeating it would never end
 */
fun <T : Any, A> GenericParser<T, A>.many(): GenericParser<T, List<A>> = GenericParser { inp ->
    val out = mutableListOf<A>()
    var cur = inp
    var hint: ParseError? = null
    var r = this.parse(cur)
    while (r is ParseResult.Ok) {
        check(r.next.index > cur.index) {
            "many() was applied to a parser that succeeded without consuming input at index ${cur.index}"
        }
        out += r.value
        cur = r.next
        hint = r.hint
        r = this.parse(cur)
    }
    val failure = r as ParseResult.Err
    if (failure.consumed) failure else ParseResult.Ok(out, cur, merge(hint, failure.error))
}

/**
 * Applies this parser one or more times, collecting results in a list.
 *
 * @return Parser that produces a list of one or more results
 */
fun <T : Any, A> GenericParser<T, A>.many1(): GenericParser<T, List<A>> =
    this.flatMap { first -> this.many().map { rest -> listOf(first) + rest } }

/**
 * Parses content between left and right delimiters.
 *
 * @param left Parser for the opening delimiter
 * @param right Parser for the closing delimiter
 * @param center Parser for the content between delimiters
 * @return Parser that produces the center content
 */
fun <T : Any, A> between(left: GenericParser<T, *>, right: GenericParser<T, *>, center: GenericParser<T, A>): GenericParser<T, A> =
    left skipL center skipR right

/**
 * Parses zero or more elements separated by a separator. A trailing separator is an error.
 *
 * @param element Parser for individual elements
 * @param sep Parser for the separator
 * @return Parser that produces a list of elements
 */
fun <T : Any, A> sepBy(element: GenericParser<T, A>, sep: GenericParser<T, *>): GenericParser<T, List<A>> =
    sepBy1(element, sep) or pure(emptyList())

/**
 * Parses one or more elements separated by a separator. A trailing separator is an error.
 *
 * @param element Parser for individual elements
 * @param sep Parser for the separator
 * @return Parser that produces a non-empty list of elements
 */
fun <T : Any, A> sepBy1(element: GenericParser<T, A>, sep: GenericParser<T, *>): GenericParser<T, List<A>> =
    element.flatMap { first ->
        (sep skipL element).many().map { rest -> listOf(first) + rest }
    }

/**
 * Parses one or more [operand]s separated by [operator]s and combines them left to right, so
 * `1 - 2 - 3` becomes `(1 - 2) - 3`. Use one chainl1 per precedence level.
 *
 * @param operand Parser for the operands
 * @param operator Parser that produces the function combining two operands
 * @return Parser that produces the combined value
 */
fun <T : Any, A> chainl1(operand: GenericParser<T, A>, operator: GenericParser<T, (A, A) -> A>): GenericParser<T, A> =
    operand.flatMap { first ->
        (operator then operand).many().map { rest ->
            rest.fold(first) { acc, (combine, rhs) -> combine(acc, rhs) }
        }
    }

/**
 * Creates a parser that always succeeds with the given value.
 *
 * @param a The value to return
 * @return Parser that produces the given value without consuming input
 */
fun <T : Any, A> pure(a: A): GenericParser<T, A> = GenericParser { inp -> ParseResult.Ok(a, inp) }

/**
 * Creates a parser that always fails with the given error.
 *
 * @param expected Description of what was expected
 * @param message Optional error message
 * @return Parser that always fails
 */
fun <T : Any, A> fail(expected: String, message: String? = null): GenericParser<T, A> = GenericParser { inp ->
    ParseResult.Err(ParseError(inp.index, setOf(expected), message), consumed = false)
}

/**
 * Succeeds only at the end of the input. [eof] is the text version.
 *
 * @return Parser that produces Unit at the end of the input
 */
fun <T : Any> endOfInput(): GenericParser<T, Unit> = GenericParser { inp ->
    if (inp.isEof) ParseResult.Ok(Unit, inp)
    else ParseResult.Err(ParseError.expected(inp.index, "end of input"), consumed = false)
}

/**
 * Creates a parser whose definition is looked up the first time it runs. Use it to refer to a
 * parser that is defined later, as recursive grammars need. It does not make deep recursion
 * stack-safe: very deeply nested input can still overflow the stack.
 *
 * @param build Function that constructs the parser; called at most once
 * @return Parser that defers construction until parse time
 */
fun <T : Any, A> lazyParser(build: () -> GenericParser<T, A>): GenericParser<T, A> {
    val parser by lazy(build)
    return GenericParser { inp -> parser.parse(inp) }
}

/**
 * Builds a parser from steps written as ordinary sequential code. Inside [block], call
 * [ParserScope.bind] on a parser to run it and get its value. The first step that fails
 * fails the whole parser. [ParserScope.bind] leaves the block by throwing, so don't wrap
 * it in a `try` that catches all exceptions.
 *
 * ```
 * val assignment: Parser<Pair<String, Int>> = parser {
 *     val name = identifier.lexeme().bind()
 *     char('=').lexeme().bind()
 *     name to int.lexeme().bind()
 * }
 * ```
 *
 * @param block The steps
 * @return Parser that produces the block's result
 */
fun <T : Any, A> parser(block: ParserScope<T>.() -> A): GenericParser<T, A> = GenericParser { inp ->
    val scope = ParserScope(inp)
    try {
        val value = scope.block()
        ParseResult.Ok(value, scope.input, scope.hint)
    } catch (failure: ParserScope.Failure) {
        if (failure.scope !== scope) throw failure
        ParseResult.Err(failure.error, consumed = failure.consumed || scope.input.index > inp.index)
    }
}

/** The receiver of a [parser] block. */
class ParserScope<T : Any> internal constructor(internal var input: Input<T>) {
    internal var hint: ParseError? = null

    /** Runs this parser at the current position and returns its value, or fails the block. */
    fun <A> GenericParser<T, A>.bind(): A = when (val r = this.parse(input)) {
        is ParseResult.Ok -> {
            hint = if (r.next.index == input.index) mergeHints(hint, r.hint) else r.hint
            input = r.next
            r.value
        }
        is ParseResult.Err ->
            throw Failure(this@ParserScope, if (r.consumed) r.error else merge(hint, r.error), r.consumed)
    }

    // Thrown to leave the block early. It carries no stack trace because it is not a bug.
    internal class Failure(val scope: ParserScope<*>, val error: ParseError, val consumed: Boolean) :
        RuntimeException(null, null, false, false)
}
