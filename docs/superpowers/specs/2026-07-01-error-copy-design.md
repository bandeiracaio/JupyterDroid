# Unified error-copy mechanism

## Problem

While testing the app on a phone, communicating an error back for debugging means retyping a Toast message or a vague "Kernel restarted" Snackbar by hand — full stack traces and cell context are lost. There's no way to get the actual exception detail off the device.

## Scope

One small utility (`ErrorReporter`), wired into the three existing error surfaces. No new screens, no persistent error history/log file, no crash-reporting service — this is about making an error easy to grab in the moment, not building observability infrastructure.

## Design

### `ErrorReporter.kt` (new file, `app/src/main/kotlin/com/jupyterdroid/util/`)

A single formatter plus two thin entry points — one for call sites that have a `Throwable`, one for call sites that only have error text (cell stderr, which already contains the Python traceback as text, not a Kotlin exception).

```kotlin
object ErrorReporter {
    fun format(action: String, message: String, stackTrace: String?, extra: String?): String {
        // timestamp (ISO-8601), action, message, stackTrace (if present),
        // extra (if present, labeled "Cell source:"), app version (BuildConfig.VERSION_NAME
        // + VERSION_CODE), joined into one plain-text block.
    }

    fun copyFromThrowable(context: Context, action: String, throwable: Throwable, extra: String? = null) {
        // format(action, throwable.message ?: throwable.toString(), Log.getStackTraceString(throwable), extra)
        // then write to ClipboardManager
    }

    fun copyFromText(context: Context, action: String, errorText: String, extra: String? = null) {
        // format(action, errorText, stackTrace = null, extra)
        // then write to ClipboardManager
    }
}
```

Both `copy*` functions write directly to `ClipboardManager` — they do not show any UI themselves. Each call site decides how to prompt the user to copy (Snackbar action vs. an inline button), per its own existing UI pattern, then calls `ErrorReporter.copy*` and shows a confirmation Toast ("Copied — paste to Claude").

### Wiring into existing error surfaces

1. **`MainActivity.openFromUri` catch block** — replace the current `Toast.makeText(this, "Cannot open file: ${e.message}", ...)` with a Snackbar (`Snackbar.make(rootView, "Cannot open file", Snackbar.LENGTH_INDEFINITE)`) with a "Copy details" action that calls `ErrorReporter.copyFromThrowable(this, "Open notebook", e)` then shows the confirmation Toast.

2. **`NotebookActivity` open/save catch blocks** — same pattern: existing Toasts (`"Failed to open: ${e.message}"`, `"Save failed: ${e.message}"`) become `Snackbar.LENGTH_INDEFINITE` with a "Copy details" action wired to `ErrorReporter.copyFromThrowable`, action labels `"Open notebook"` / `"Save notebook"` respectively.

3. **`NotebookActivity.runCell` kernel-crash path** — currently shows a bare `Snackbar.make(..., "Kernel restarted", Snackbar.LENGTH_SHORT)` after catching the exception and calling `km.reset()`. Change to `LENGTH_INDEFINITE` with a "Copy details" action calling `ErrorReporter.copyFromThrowable(this, "Kernel crash", e, extra = cell.source)` — includes the cell's source code as context since that's what caused the crash.

4. **Cell execution error (`CodeCellViewHolder`)** — the red stderr text is already persistently visible under the cell (not transient like the above three), so instead of a Snackbar, add a small "Copy" text-button in `item_cell_code.xml`, shown only when `cell.error.isNotEmpty()`, calling `ErrorReporter.copyFromText(context, "Cell execution", cell.error, extra = cell.source)` directly from `CodeCellViewHolder.bind` (no adapter/activity plumbing needed — clipboard writes are stateless).

### Confirmation

After any copy, show `Toast.makeText(context, "Copied — paste to Claude", Toast.LENGTH_SHORT).show()`.

### Copied text format (example)

```
[2026-07-01 14:32:10] Kernel crash
App: JupyterDroid v1.2 (3)
Message: ZeroDivisionError: division by zero
Stack trace:
  <full stack trace lines>
Cell source:
  print(1/0)
```

## Error handling

This feature *is* the error-handling improvement — no separate error handling needed within it. `ErrorReporter` itself does not throw; clipboard writes are simple, non-failing Android API calls.

## Explicitly out of scope

- No persistent error log or history — each Snackbar/button surfaces only the error that just happened.
- No automatic error reporting/telemetry to any external service.
- No changes to *what* triggers an error (file I/O failures, kernel crashes, Python exceptions) — only how the existing error text/exception is surfaced and copied.
