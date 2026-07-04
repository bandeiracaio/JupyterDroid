package com.jupyterdroid.ui

import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan

object PythonHighlighter {

    enum class Kind { STRING, COMMENT, KEYWORD, NUMBER }
    data class Token(val start: Int, val end: Int, val kind: Kind)

    private const val KEYWORDS =
        "False|None|True|and|as|assert|async|await|break|class|continue|def|del|" +
        "elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|" +
        "not|or|pass|raise|return|try|while|with|yield"

    // Single left-to-right scan; alternation order makes strings/comments shadow
    // keywords and numbers. Numbers allow `_` separators (1_000, 0xFF_FF) and
    // hex/octal/binary bases; a leading `@name`/`@a.b` reads as a decorator and
    // is coloured like a keyword. ponytail: regex highlighter, swap for a real
    // lexer only if f-string internals, leading-dot floats (.5), or soft
    // keywords ever matter; `a@b` matmul without spaces mis-reads as a decorator.
    private val tokenRegex = Regex(
        "(?<str>\"\"\"[\\s\\S]*?(?:\"\"\"|$)|'''[\\s\\S]*?(?:'''|$)" +
            "|\"(?:\\\\.|[^\"\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\n])*'?)" +
            "|(?<com>#[^\n]*)" +
            "|(?<kw>\\b(?:$KEYWORDS)\\b|@\\w+(?:\\.\\w+)*)" +
            "|(?<num>\\b(?:0[xX][0-9a-fA-F](?:_?[0-9a-fA-F])*" +
            "|0[oO][0-7](?:_?[0-7])*" +
            "|0[bB][01](?:_?[01])*" +
            "|\\d(?:_?\\d)*(?:\\.(?:\\d(?:_?\\d)*)?)?(?:[eE][+-]?\\d(?:_?\\d)*)?)\\b)"
    )

    fun tokenize(text: String): List<Token> =
        tokenRegex.findAll(text).map { m ->
            val kind = when {
                m.groups["str"] != null -> Kind.STRING
                m.groups["com"] != null -> Kind.COMMENT
                m.groups["kw"] != null -> Kind.KEYWORD
                else -> Kind.NUMBER
            }
            Token(m.range.first, m.range.last + 1, kind)
        }.toList()

    data class Colors(val keyword: Int, val string: Int, val comment: Int, val number: Int)

    // Marker subclass so we only ever remove our own spans.
    private class HighlightSpan(color: Int) : ForegroundColorSpan(color)

    fun highlight(editable: Editable, colors: Colors) {
        editable.getSpans(0, editable.length, HighlightSpan::class.java)
            .forEach(editable::removeSpan)
        for (t in tokenize(editable.toString())) {
            val color = when (t.kind) {
                Kind.STRING -> colors.string
                Kind.COMMENT -> colors.comment
                Kind.KEYWORD -> colors.keyword
                Kind.NUMBER -> colors.number
            }
            editable.setSpan(HighlightSpan(color), t.start, t.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
