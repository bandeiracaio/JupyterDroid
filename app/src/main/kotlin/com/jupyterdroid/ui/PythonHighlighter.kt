package com.jupyterdroid.ui

import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan

object PythonHighlighter {

    // VS Code Dark+ palette
    private val BLUE   = Color.parseColor("#569CD6")  // keywords
    private val YELLOW = Color.parseColor("#DCDCAA")  // builtins / functions
    private val ORANGE = Color.parseColor("#CE9178")  // strings
    private val GREEN  = Color.parseColor("#6A9955")  // comments
    private val LIME   = Color.parseColor("#B5CEA8")  // numbers
    private val PURPLE = Color.parseColor("#C586C0")  // def / class names

    private val RX_COMMENT  = Regex("""#[^\n]*""")
    private val RX_STR3_DQ  = Regex("""\"\"\".*?\"\"\"""", RegexOption.DOT_MATCHES_ALL)
    private val RX_STR3_SQ  = Regex("""\'\'\'.*?\'\'\'""", RegexOption.DOT_MATCHES_ALL)
    private val RX_STR_DQ   = Regex(""""(?:[^"\\]|\\.)*"""")
    private val RX_STR_SQ   = Regex("""'(?:[^'\\]|\\.)*'""")
    private val RX_KEYWORD  = Regex("""\b(False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield)\b""")
    private val RX_BUILTIN  = Regex("""\b(abs|all|any|bin|bool|bytearray|bytes|callable|chr|dict|dir|divmod|enumerate|eval|exec|filter|float|format|frozenset|getattr|globals|hasattr|hash|help|hex|id|input|int|isinstance|issubclass|iter|len|list|locals|map|max|memoryview|min|next|object|oct|open|ord|pow|print|property|range|repr|reversed|round|set|setattr|slice|sorted|staticmethod|str|sum|super|tuple|type|vars|zip)\b""")
    private val RX_DEFNAME  = Regex("""\b(?:def|class)\s+(\w+)""")
    private val RX_NUMBER   = Regex("""\b\d+\.?\d*([eE][+-]?\d+)?\b""")

    fun highlight(editable: Editable) {
        // Clear all existing colour spans
        editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }

        val text = editable.toString()

        // Track ranges already coloured (strings + comments take priority)
        val taken = mutableListOf<IntRange>()

        fun isFree(range: IntRange) = taken.none { it.first <= range.first && it.last >= range.last }

        fun paint(range: IntRange, color: Int) {
            editable.setSpan(
                ForegroundColorSpan(color),
                range.first, range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Strings and comments first (highest priority)
        for (rx in listOf(RX_STR3_DQ, RX_STR3_SQ, RX_STR_DQ, RX_STR_SQ)) {
            rx.findAll(text).forEach { m ->
                if (isFree(m.range)) { paint(m.range, ORANGE); taken.add(m.range) }
            }
        }
        RX_COMMENT.findAll(text).forEach { m ->
            if (isFree(m.range)) { paint(m.range, GREEN); taken.add(m.range) }
        }

        // def/class names
        RX_DEFNAME.findAll(text).forEach { m ->
            val nameGroup = m.groups[1]!!
            if (isFree(nameGroup.range)) paint(nameGroup.range, YELLOW)
        }

        // Keywords, builtins, numbers
        RX_KEYWORD.findAll(text).forEach { m -> if (isFree(m.range)) paint(m.range, BLUE) }
        RX_BUILTIN.findAll(text).forEach { m -> if (isFree(m.range)) paint(m.range, YELLOW) }
        RX_NUMBER.findAll(text) .forEach { m -> if (isFree(m.range)) paint(m.range, LIME) }
    }
}
