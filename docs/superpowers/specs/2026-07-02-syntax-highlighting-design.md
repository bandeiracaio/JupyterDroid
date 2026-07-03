# Syntax Highlighting in Code Cells — Design

**Date:** 2026-07-02
**Status:** Approved

## Goal

Live Python syntax coloring inside code-cell editors. Keywords, strings, comments, and numbers get distinct colors as the user types. No new dependencies, no UI restructuring.

## Approach

Keep the existing `EditText` in `item_cell_code.xml`. Add a small regex-based highlighter that recolors the `Editable` in place via `ForegroundColorSpan`s.

Rejected alternatives:
- **Prism4j** — grammar-accurate but adds an annotation-processor dependency and targets static rendering; we'd still write the live-editing glue.
- **sora-editor** — full editor widget; heavier dependency and UI change than the feature warrants.

## Components

### `ui/PythonHighlighter.kt` (new)

Single object with one public function:

```kotlin
object PythonHighlighter {
    fun highlight(editable: Editable, colors: Colors)
    data class Colors(val keyword: Int, val string: Int, val comment: Int, val number: Int)
}
```

Behavior:
1. Remove all `ForegroundColorSpan`s previously applied by the highlighter (track via a marker: only remove spans the highlighter itself added — use a private `ForegroundColorSpan` subclass so user/system spans are untouched).
2. Tokenize with ordered regexes — earlier matches claim their range; later patterns skip claimed ranges:
   1. Triple-quoted strings (`"""…"""`, `'''…'''`, non-greedy, DOTALL; unterminated runs to end of text)
   2. Single-quoted strings (`"…"`, `'…'` with `\\.` escapes, terminated at newline)
   3. Comments (`#` to end of line)
   4. Keywords (Python 3 keyword list, `\b`-bounded)
   5. Numbers (int, float, hex, binary, scientific, `\b`-bounded)
3. Apply one span per token.

Whole-text re-highlight on every call. `// ponytail:` comment marks incremental highlighting as the upgrade path if cells ever hold thousands of lines.

### `CodeCellViewHolder.kt` (modified)

- Resolve the four colors once per ViewHolder from theme resources.
- Call `PythonHighlighter.highlight(...)` in the existing `afterTextChanged`, guarded by a `highlighting` boolean in case span mutation ever re-enters.
- Call it once at bind time after `setText`, so cells are colored on load, not only after the first edit.

### Colors

Four new resources in `values/colors.xml` and `values-night/colors.xml`:

| Resource | Light | Dark |
|---|---|---|
| `syntax_keyword` | `#7B2D8E` (purple) | `#C586C0` |
| `syntax_string` | `#0B7A3E` (green) | `#6A9955` |
| `syntax_comment` | `#8A8A8A` (gray) | `#808080` |
| `syntax_number` | `#1750EB` (blue) | `#B5CEA8` |

## Scope

- Python only (the app runs one Python kernel; markdown cells render via Markwon already).
- No line numbers, bracket matching, or auto-indent.
- Output/error text stays unstyled.

## Testing

One unit test (`PythonHighlighterTest`, plain JVM if `Editable` can be avoided — otherwise androidTest with `SpannableStringBuilder`): feed a snippet containing a keyword inside a string, a comment containing a keyword, a triple-quoted string spanning lines, and a number; assert span ranges and colors. This locks the ordering rule (strings/comments shadow keywords).

## Risks

- Regex highlighting is approximate (e.g. f-string interior expressions colored as string). Accepted — cosmetic feature.
- Per-keystroke whole-cell pass: fine at notebook cell sizes.
