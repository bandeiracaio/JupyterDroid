package com.jupyterdroid

import com.jupyterdroid.ui.PythonHighlighter
import com.jupyterdroid.ui.PythonHighlighter.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class PythonHighlighterTest {

    @Test
    fun keywordsAndNumbers() {
        val tokens = PythonHighlighter.tokenize("def f(x):\n    return x + 42")
        assertEquals(
            listOf(Kind.KEYWORD, Kind.KEYWORD, Kind.NUMBER),
            tokens.map { it.kind }
        )
        // "def" at 0..3, "return" at 14..20, "42" at 25..27
        assertEquals(listOf(0 to 3, 14 to 20, 25 to 27), tokens.map { it.start to it.end })
    }

    @Test
    fun keywordInsideStringIsNotAKeyword() {
        val tokens = PythonHighlighter.tokenize("x = \"if else\"")
        assertEquals(listOf(Kind.STRING), tokens.map { it.kind })
        assertEquals(4, tokens[0].start)
        assertEquals(13, tokens[0].end)
    }

    @Test
    fun keywordInsideCommentIsNotAKeyword() {
        val tokens = PythonHighlighter.tokenize("y = 1  # not if here")
        assertEquals(listOf(Kind.NUMBER, Kind.COMMENT), tokens.map { it.kind })
        assertEquals(7 to 20, tokens[1].start to tokens[1].end)
    }

    @Test
    fun hashInsideStringIsString() {
        val tokens = PythonHighlighter.tokenize("s = \"a # b\"")
        assertEquals(listOf(Kind.STRING), tokens.map { it.kind })
    }

    @Test
    fun apostropheInCommentStaysComment() {
        val tokens = PythonHighlighter.tokenize("# don't stop")
        assertEquals(listOf(Kind.COMMENT), tokens.map { it.kind })
        assertEquals(0 to 12, tokens[0].start to tokens[0].end)
    }

    @Test
    fun tripleQuotedStringSpansLines() {
        val src = "\"\"\"doc\nif x\n\"\"\"\npass"
        val tokens = PythonHighlighter.tokenize(src)
        assertEquals(listOf(Kind.STRING, Kind.KEYWORD), tokens.map { it.kind })
        assertEquals(0 to 15, tokens[0].start to tokens[0].end)
    }

    @Test
    fun unterminatedStringRunsToEndOfLine() {
        val tokens = PythonHighlighter.tokenize("s = \"oops\nz = 1")
        assertEquals(listOf(Kind.STRING, Kind.NUMBER), tokens.map { it.kind })
        assertEquals(4 to 9, tokens[0].start to tokens[0].end)
    }

    @Test
    fun numberFormats() {
        val tokens = PythonHighlighter.tokenize("a = 42 + 3.14e2 + 0xFF + 0b101")
        assertEquals(List(4) { Kind.NUMBER }, tokens.map { it.kind })
    }

    @Test
    fun escapedQuoteStaysInsideString() {
        val tokens = PythonHighlighter.tokenize("s = \"a\\\"b\" + 1")
        assertEquals(listOf(Kind.STRING, Kind.NUMBER), tokens.map { it.kind })
        assertEquals(4 to 10, tokens[0].start to tokens[0].end)
    }
}
