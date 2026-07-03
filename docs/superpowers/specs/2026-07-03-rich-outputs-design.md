# Rich Outputs (Plots + Expression Echo) — Design

**Date:** 2026-07-03
**Status:** Approved

## Goal

Matplotlib figures render as images inside the cell and persist into the `.ipynb` as standard `image/png` outputs. Cells echo their last expression like Jupyter — a bare `df` or `x + 1` at the end of a cell shows its `repr()` without `print()`. Pandas tables therefore appear as monospace text; styled HTML tables are out of scope for this slice.

## Approach

**Plot capture: post-exec figure sweep.** Force the `Agg` backend via the `MPLBACKEND=agg` environment variable at kernel-module import (harmless when matplotlib is absent). After each `exec`, if `matplotlib` is already in `sys.modules`, sweep every open figure: `savefig` to an in-memory PNG buffer, base64-encode, then `plt.close('all')`. Captures plots whether or not the user calls `plt.show()`.

Rejected alternatives:
- **Patch `plt.show()`** — notebook users rarely call it; misses the common case.
- **IPython display machinery** — real Jupyter behavior, but imports IPython for a fraction of its function.

**Expression echo: ast split.** Parse the cell with `ast.parse`. If the last top-level statement is an `ast.Expr`, compile-and-exec everything before it, then compile-and-eval the last expression; when the value is not `None`, append `repr(value)` to the cell output (after any printed stdout). Exact Jupyter semantics. Syntax errors surface exactly as today (the parse failure's traceback becomes the cell error).

## Components

### `kernel_runner.py` (modified)

- At module top: `os.environ.setdefault("MPLBACKEND", "agg")` (before any user code can import matplotlib).
- `execute()` result dict gains `"images": [<base64 png str>, ...]` (empty list when no figures).
- Echo folds into the existing execute flow; the interrupt/async-exc guards from the kernel-interrupt feature must keep covering the new eval path (echo runs inside the same outer `try/except BaseException`).
- Sweep helper:

```python
def _capture_figures():
    if "matplotlib" not in sys.modules:
        return []
    import matplotlib.pyplot as plt
    images = []
    for num in plt.get_fignums():
        buf = io.BytesIO()
        plt.figure(num).savefig(buf, format="png")
        images.append(base64.b64encode(buf.getvalue()).decode("ascii"))
    plt.close("all")
    return images
```

A failure inside the sweep (broken figure, OOM on a huge canvas) is caught and reported as the cell's error, never crashes the bridge.

### Kotlin model + bridge

- `Cell.Code` gains `val images: List<String> = emptyList()` (base64 PNGs).
- `ExecutionResult` gains `val images: List<String>`.
- `KernelManager.execute()` reads the `images` list from the Python dict.

### `CodeCellViewHolder` (modified)

- New vertical `LinearLayout` container below `outputText` in `item_cell_code.xml`.
- Bind: clear container; for each image, decode base64 → `Bitmap` → `ImageView` with `adjustViewBounds = true`, width `MATCH_PARENT`. A decode failure shows nothing for that image (log only) — never crashes the bind.

### nbformat persistence (`NotebookJson` / `NotebookFile`)

- `CellOutputJson` gains `data: Map<String, JsonElement>? = null` so `display_data` outputs parse.
- Write: each image becomes `{"output_type": "display_data", "data": {"image/png": "<b64>\n"}, "metadata": {}}` after the stream output.
- Read: collect `image/png` from `display_data` (and `execute_result`) outputs into `Cell.Code.images`. `image/png` values may be a string or a list of lines per nbformat — join lists.
- Unknown output types continue to be ignored on read and dropped on write (existing behavior).

## Behavior summary

| Cell code | Output shown |
|---|---|
| `1 + 1` | `2` (echo) |
| `x = 5` | nothing (assignment, no echo) |
| `print("a"); 3` | `a` then `3` |
| `df` (pandas installed) | monospace text repr of the DataFrame |
| `plt.plot([1,2]); plt.show()` | the plot as an image |
| two figures created | two images, in figure order |

Echo of `None` shows nothing (Jupyter behavior). Images persist across save/reopen and display in desktop Jupyter.

## Scope

- No HTML table rendering (no WebView) — pandas is text via echo.
- No image zoom/pan/save-to-gallery interactions.
- No `execute_result` vs `display_data` distinction on write — everything the sweep captures is `display_data`; the echo value is plain text appended to stdout stream output. (Desktop Jupyter renders both fine.)

## Testing

- **Host python** (`app/src/test/python/test_kernel_runner.py` extended): echo cases (expression echoed, assignment silent, `None` silent, stdout+echo ordering, syntax error unaffected, echo value with `repr` containing unicode). Host has **no matplotlib** (verified: Python 3.9.6 without it), so the sweep test asserts the no-matplotlib fast path returns `[]`; the with-matplotlib path is emulator-verified.
- **JVM unit test**: nbformat round-trip — a cell with one image writes `display_data` with `image/png` and reads back to the same base64; a notebook from desktop Jupyter with list-of-lines `image/png` parses.
- **Emulator**: `pip install matplotlib`, run a plot cell, image appears; save/reopen keeps it; echo behaviors from the table above.

## Risks

- matplotlib install size/time on device — user-initiated via existing pip UI; not our path.
- Large figures → large base64 strings in memory and file; accepted at notebook scale. `// ponytail:` note: downscale/compress if files balloon.
- Echo `repr()` of huge objects (e.g. million-row df) produces big text; pandas truncates its repr by default; accepted.
