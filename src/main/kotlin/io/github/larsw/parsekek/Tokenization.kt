package io.github.larsw.parsekek

import java.math.BigDecimal

/** Represents a source span with start and end positions */
data class Span(val start: Int, val end: Int) // [start, end)

/**
 * Sealed interface for tokens with position information.
 */
sealed interface Tok {
    /** The source span this token covers */
    val span: Span

    /** Identifier token */
    data class Ident(val name: String, override val span: Span) : Tok

    /** Numeric literal token */
    data class Num(val value: BigDecimal, override val span: Span) : Tok

    /** Keyword token */
    data class Kw(val kw: String, override val span: Span) : Tok

    /** Symbol/operator token */
    data class Sym(val sym: String, override val span: Span) : Tok
}

private fun <A> spanned(p: Parser<A>): Parser<Pair<A, Span>> = Parser { inp ->
    val start = inp.index
    when (val r = p.parse(inp)) {
        is ParseResult.Ok  -> ParseResult.Ok(r.value to Span(start, r.next.index), r.next)
        is ParseResult.Err -> r
    }
}

/**
 * Creates a parser that matches any of the given symbol strings, choosing the longest match.
 *
 * @param syms Variable number of symbol strings to match
 * @return Parser that produces a symbol token
 */
fun symbols(vararg syms: String): Parser<Tok.Sym> = Parser { inp ->
    // Try all symbols and find the longest successful match
    var bestMatch: ParseResult.Ok<Tok.Sym>? = null
    var longestLength = 0

    for (sym in syms) {
        val parser = spanned(string(sym)).map { (s, sp) -> Tok.Sym(s, sp) }
        when (val result = parser.parse(inp)) {
            is ParseResult.Ok -> {
                if (sym.length > longestLength) {
                    bestMatch = result
                    longestLength = sym.length
                }
            }
            is ParseResult.Err -> {
                // Continue trying other symbols
            }
        }
    }

    bestMatch?.let { match ->
        // Apply lexeme (whitespace trimming) to the result
        spaces.parse(match.next).let { wsResult ->
            when (wsResult) {
                is ParseResult.Ok -> ParseResult.Ok(match.value, wsResult.next)
                is ParseResult.Err -> match // If whitespace parsing fails, just return the match
            }
        }
    } ?: ParseResult.Err(
        ParseError.expected(inp.index, *syms.map { "\"$it\"" }.toTypedArray()),
        consumed = false
    )
}

/** Parser that matches identifier tokens with position information */
val identTok: Parser<Tok.Ident> =
    spanned(identifier).map { (name, sp) -> Tok.Ident(name, sp) }

/** Parser that matches numeric tokens with position information */
val numberTok: Parser<Tok.Num> = Parser { inp ->
    val start = inp.index

    // Parse the number lexeme first to determine if it's an integer or decimal
    when (val doubleResult = double.parse(inp)) {
        is ParseResult.Ok -> {
            val lexeme = inp.text.substring(inp.index, doubleResult.next.index)
            val value = if (lexeme.contains('.') || lexeme.contains('e') || lexeme.contains('E')) {
                // It's a decimal number
                BigDecimal.valueOf(doubleResult.value)
            } else {
                // It's an integer, convert to BigDecimal without decimal point
                BigDecimal.valueOf(doubleResult.value.toLong())
            }

            // Apply lexeme (whitespace trimming)
            when (val lexemeResult = spaces.parse(doubleResult.next)) {
                is ParseResult.Ok -> ParseResult.Ok(
                    Tok.Num(value, Span(start, lexemeResult.next.index)),
                    lexemeResult.next
                )
                is ParseResult.Err -> ParseResult.Ok(
                    Tok.Num(value, Span(start, doubleResult.next.index)),
                    doubleResult.next
                )
            }
        }
        is ParseResult.Err -> doubleResult
    }
}

/**
 * Creates a parser that matches keyword tokens from the given strings.
 *
 * @param kws Variable number of keyword strings to match
 * @return Parser that produces keyword tokens
 */
fun keywordTok(vararg kws: String): Parser<Tok.Kw> {
    val alts = kws.map { kw ->
        spanned(keyword(kw)).map { (_, sp) -> Tok.Kw(kw, sp) }
    }
    return alts.reduce { acc, p -> acc or p }
}

private fun <T : Tok> upcast(p: Parser<T>): Parser<Tok> = p.map { it as Tok }

/**
 * Creates a tokenizer for the entire input with given keywords and symbols.
 *
 * @param keywords Set of keyword strings to recognize
 * @param symbols Set of symbol strings to recognize
 * @return Parser that produces a list of tokens
 */
fun tokenize(
    keywords: Set<String> = emptySet(),
    symbols: Set<String> = emptySet()
): Parser<List<Tok>> {
    val kw =
        if (keywords.isEmpty()) fail<Tok.Kw>("keywords")
        else keywordTok(*keywords.toTypedArray())

    val sym =
        if (symbols.isEmpty()) fail<Tok.Sym>("symbols")
        else symbols(*symbols.toTypedArray())

    val one: Parser<Tok> =
        upcast(kw) or upcast(identTok) or upcast(numberTok) or upcast(sym)
    val gap = spaces
    return gap.skipL(one).many().skipR(gap).skipR(eof)
}
