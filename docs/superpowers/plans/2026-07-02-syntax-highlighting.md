# Syntax Highlighting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Live Python syntax coloring (keywords, strings, comments, numbers) in code-cell editors.

**Architecture:** A dependency-free `PythonHighlighter` object with a pure `tokenize()` function (single combined-regex scan, leftmost match wins — strings/comments naturally shadow keywords) and a `highlight()` function that reapplies `ForegroundColorSpan`s on an `Editable`. Hooked into the existing `TextWatcher` in `CodeCellViewHolder`.

**Tech Stack:** Kotlin, Android spans, JUnit 4 (plain JVM unit test — `tokenize` touches no Android APIs at runtime).

**Spec:** `docs/superpowers/specs/2026-07-02-syntax-highlighting-design.md`

## Global Constraints

- No new dependencies.
- All gradle commands need `export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home` first (no system Java).
- Branch: `syntax-highlighting` (already created and checked out).
- The spec describes "ordered regex passes with claimed ranges"; this plan implements the same semantics as a single alternation regex scanned left-to-right — leftmost match wins, alternation order breaks ties. Equivalent result, less code.

---

### Task 1: Tokenizer

**Files:**
- Create: `app/src/main/kotlin/com/jupyterdroid/ui/PythonHighlighter.kt`
- Test: `app/src/test/kotlin/com/jupyterdroid/PythonHighlighterTest.kt` (new source set dir — plain JVM unit test; `tokenize` calls no Android methods, so android.jar stubs link fine)

**Interfaces:**
- Consumes: nothing.
- Produces: `PythonHighlighter.tokenize(text: String): List<Token>`, `enum class Kind { STRING, COMMENT, KEYWORD, NUMBER }`, `data class Token(val start: Int, val end: Int, val kind: Kind)` (end is exclusive). Task 2 adds `highlight()` and `Colors` to the same object.

- [x] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/jupyterdroid/PythonHighlighterTest.kt`:

```kotlin
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
        // "def" at 0..3, "return" at 14..20, "42" at 27..29
        assertEquals(listOf(0 to 3, 14 to 20, 27 to 29), tokens.map { it.start to it.end })
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
```

- [x] **Step 2: Run test to verify it fails**

```bash
export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: FAIL to compile — `unresolved reference: PythonHighlighter`.

- [x] **Step 3: Write the tokenizer**

Create `app/src/main/kotlin/com/jupyterdroid/ui/PythonHighlighter.kt`:

```kotlin
package com.jupyterdroid.ui

object PythonHighlighter {

    enum class Kind { STRING, COMMENT, KEYWORD, NUMBER }
    data class Token(val start: Int, val end: Int, val kind: Kind)

    private const val KEYWORDS =
        "False|None|True|and|as|assert|async|await|break|class|continue|def|del|" +
        "elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|" +
        "not|or|pass|raise|return|try|while|with|yield"

    // Single left-to-right scan; alternation order makes strings/comments shadow
    // keywords and numbers. ponytail: regex highlighter, swap for a real lexer
    // only if f-string internals or soft keywords ever matter.
    private val tokenRegex = Regex(
        "(?<str>\"\"\"[\\s\\S]*?(?:\"\"\"|$)|'''[\\s\\S]*?(?:'''|$)" +
            "|\"(?:\\\\.|[^\"\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\n])*'?)" +
            "|(?<com>#[^\n]*)" +
            "|(?<kw>\\b(?:$KEYWORDS)\\b)" +
            "|(?<num>\\b(?:0[xX][0-9a-fA-F]+|0[bB][01]+|\\d+(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)\\b)"
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
}
```

- [x] **Step 4: Run tests to verify they pass**

```bash
export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. If any assertion fails on exact offsets, recount the offsets in the test string before touching the regex — the test strings contain escapes.

- [x] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/ui/PythonHighlighter.kt app/src/test/kotlin/com/jupyterdroid/PythonHighlighterTest.kt
git commit -m "Add PythonHighlighter tokenizer with unit tests"
```

---

### Task 2: highlight() + colors + ViewHolder hook

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/ui/PythonHighlighter.kt` (add `Colors`, `highlight`)
- Modify: `app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt`
- Modify: `app/src/main/res/values/colors.xml`, `app/src/main/res/values-night/colors.xml`

**Interfaces:**
- Consumes: `PythonHighlighter.tokenize`, `Kind`, `Token` from Task 1.
- Produces: `PythonHighlighter.highlight(editable: Editable, colors: Colors)`, `data class Colors(val keyword: Int, val string: Int, val comment: Int, val number: Int)`.

- [x] **Step 1: Add color resources**

In `app/src/main/res/values/colors.xml`, add inside `<resources>`:

```xml
    <color name="syntax_keyword">#7B2D8E</color>
    <color name="syntax_string">#0B7A3E</color>
    <color name="syntax_comment">#8A8A8A</color>
    <color name="syntax_number">#1750EB</color>
```

In `app/src/main/res/values-night/colors.xml`, add inside `<resources>`:

```xml
    <color name="syntax_keyword">#C586C0</color>
    <color name="syntax_string">#6A9955</color>
    <color name="syntax_comment">#808080</color>
    <color name="syntax_number">#B5CEA8</color>
```

- [x] **Step 2: Add highlight() to PythonHighlighter**

Add imports at the top of `PythonHighlighter.kt`:

```kotlin
import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
```

Add inside the object, below `tokenize`:

```kotlin
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
```

- [x] **Step 3: Hook into CodeCellViewHolder**

In `CodeCellViewHolder.kt`, add two properties after `private var watcher: TextWatcher? = null` (line 22):

```kotlin
    private val syntaxColors = PythonHighlighter.Colors(
        keyword = ContextCompat.getColor(view.context, R.color.syntax_keyword),
        string = ContextCompat.getColor(view.context, R.color.syntax_string),
        comment = ContextCompat.getColor(view.context, R.color.syntax_comment),
        number = ContextCompat.getColor(view.context, R.color.syntax_number),
    )
    private var highlighting = false
```

Replace the existing `afterTextChanged` body:

```kotlin
            override fun afterTextChanged(s: Editable) {
                if (highlighting) return
                highlighting = true
                PythonHighlighter.highlight(s, syntaxColors)
                highlighting = false
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onSourceChanged(pos, s.toString())
            }
```

(Span-only changes don't fire `TextWatcher`, so the `highlighting` flag is belt-and-braces re-entrance protection.)

In `bind`, immediately after `sourceEdit.setText(cell.source)` (line 27), add:

```kotlin
        PythonHighlighter.highlight(sourceEdit.text, syntaxColors)
```

This colors the cell at load; the watcher handles every keystroke after.

- [x] **Step 4: Build**

```bash
export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home
./gradlew assembleDebug 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Run all tests**

```bash
export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home
./gradlew testDebugUnitTest 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/ui/PythonHighlighter.kt app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml
git commit -m "Wire syntax highlighting into code cells"
```

---

### Task 3: Manual verification (device)

**Files:** none — install `app/build/outputs/apk/debug/app-debug.apk` on a device/emulator.

- [x] **Step 1: Load coloring**

Open the sample Titanic notebook. Code cells show colored keywords (purple/violet), strings (green), comments (gray), numbers (blue) immediately, before any edit.

- [x] **Step 2: Live coloring**

Type `if x == "hi":  # test 42` into a cell. `if` colors as keyword while typing; the string, comment, and `42` color correctly. `if` inside the string/comment does NOT color as keyword.

- [x] **Step 3: Editing integrity**

Type, delete, and paste multi-line code — no crashes, no color "smearing" onto newly typed plain text after a colored token (SPAN_EXCLUSIVE_EXCLUSIVE should prevent it). Cursor and text selection behave normally.

- [x] **Step 4: Dark mode**

Toggle system dark mode, reopen the notebook — dark-variant colors show, readable on the dark code background.

- [x] **Step 5: Persistence sanity**

Edit a cell, save, reopen — text intact (spans must never leak into the saved `.ipynb` source; `s.toString()` strips them by construction).

All pass → feature complete. Any failure → note the step and return to the relevant task.

---

## Self-Review

**Spec coverage:**
- `PythonHighlighter` object, `highlight(editable, colors)` → Tasks 1–2. ✓ (spec's "ordered passes with claimed ranges" implemented as equivalent single alternation scan — noted in Global Constraints)
- Marker span subclass so only our spans are removed → Task 2 Step 2. ✓
- Triple-quoted, escaped, unterminated strings; comments; keywords; numbers → Task 1 regex + tests. ✓
- Hook in `afterTextChanged` with guard + at bind time → Task 2 Step 3. ✓
- Four colors, light + night → Task 2 Step 1, exact hex from spec. ✓
- Unit test covering shadowing rules → Task 1 Step 1. ✓
- `ponytail:` upgrade-path comment → Task 1 Step 3. ✓

**Placeholder scan:** No TBD/TODO; every code step shows complete code; commands include expected output.

**Type consistency:** `Colors` field order (keyword, string, comment, number) matches construction in Task 2 Step 3 (named args). `Token(start, end exclusive)` matches test offset assertions. `tokenize` referenced by `highlight` matches Task 1 signature.
