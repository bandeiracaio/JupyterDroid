# Unified Error-Copy Mechanism Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every error surface in the app (file open/save failures, kernel crashes, cell execution errors) gets a one-tap way to copy a full diagnostic bundle (timestamp, action, exception detail, stack trace, cell source) to the clipboard.

**Architecture:** One new utility object, `ErrorReporter`, owns formatting the diagnostic text block and writing it to the clipboard. Each existing error call site (currently a bare `Toast` or an uninformative `Snackbar`) is changed to show a `Snackbar`/button with a "Copy details" action that calls into `ErrorReporter`, then confirms with a `Toast`. No new screens, no persisted error history.

**Tech Stack:** Kotlin, Android SDK (`ClipboardManager`, `Snackbar` from Material Components, `BuildConfig`), JUnit4 for the one pure-logic unit test.

## Global Constraints

- No persistent error log, history list, or crash-reporting service — every copy action surfaces only the error that just happened.
- No change to *what* triggers an error — only how existing error text/exceptions are surfaced and copied.
- Copied text format is a plain-text block: timestamp, action, message, stack trace (if available), cell source (if relevant), app version — see the spec's example block.
- After any copy, show `Toast.makeText(context, "Copied — paste to Claude", Toast.LENGTH_SHORT).show()`.
- Snackbars that offer a copy action use `Snackbar.LENGTH_INDEFINITE` (must not disappear before the user can tap "Copy details").

---

### Task 1: `ErrorReporter` — format and clipboard write

**Files:**
- Create: `app/src/main/kotlin/com/jupyterdroid/util/ErrorReporter.kt`
- Test: `app/src/test/kotlin/com/jupyterdroid/ErrorReporterTest.kt` (new file)

**Interfaces:**
- Produces: `ErrorReporter.format(action: String, message: String, stackTrace: String?, extra: String?, appVersion: String): String` — pure function, no Android framework dependency, fully unit-testable.
- Produces: `ErrorReporter.copyFromThrowable(context: Context, action: String, throwable: Throwable, extra: String? = null)`
- Produces: `ErrorReporter.copyFromText(context: Context, action: String, errorText: String, extra: String? = null)`

`format` takes `appVersion` as a plain parameter (not read from `BuildConfig` internally) so it stays a pure, JVM-testable function — `copyFromThrowable`/`copyFromText` are the only two places that read `BuildConfig.VERSION_NAME`/`VERSION_CODE`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/jupyterdroid/ErrorReporterTest.kt`:

```kotlin
package com.jupyterdroid

import com.jupyterdroid.util.ErrorReporter
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorReporterTest {

    @Test
    fun `format includes action, message, stack trace, extra, and version`() {
        val text = ErrorReporter.format(
            action = "Kernel crash",
            message = "ZeroDivisionError: division by zero",
            stackTrace = "line 1\nline 2",
            extra = "print(1/0)",
            appVersion = "1.2 (3)"
        )

        assertTrue(text.contains("Kernel crash"))
        assertTrue(text.contains("ZeroDivisionError: division by zero"))
        assertTrue(text.contains("line 1\nline 2"))
        assertTrue(text.contains("print(1/0)"))
        assertTrue(text.contains("1.2 (3)"))
    }

    @Test
    fun `format omits stack trace and extra sections when null`() {
        val text = ErrorReporter.format(
            action = "Open notebook",
            message = "Permission denied",
            stackTrace = null,
            extra = null,
            appVersion = "1.2 (3)"
        )

        assertTrue(text.contains("Open notebook"))
        assertTrue(text.contains("Permission denied"))
        assertTrue(!text.contains("Stack trace:"))
        assertTrue(!text.contains("Cell source:"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew testDebugUnitTest --tests "com.jupyterdroid.ErrorReporterTest"`
Expected: FAIL — `ErrorReporter` is an unresolved reference (compile error).

- [ ] **Step 3: Implement `ErrorReporter.kt`**

Create `app/src/main/kotlin/com/jupyterdroid/util/ErrorReporter.kt`:

```kotlin
package com.jupyterdroid.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.jupyterdroid.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorReporter {

    fun format(
        action: String,
        message: String,
        stackTrace: String?,
        extra: String?,
        appVersion: String
    ): String = buildString {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("[$timestamp] $action")
        appendLine("App: JupyterDroid v$appVersion")
        appendLine("Message: $message")
        if (!stackTrace.isNullOrEmpty()) {
            appendLine("Stack trace:")
            appendLine(stackTrace)
        }
        if (!extra.isNullOrEmpty()) {
            appendLine("Cell source:")
            appendLine(extra)
        }
    }.trimEnd()

    fun copyFromThrowable(context: Context, action: String, throwable: Throwable, extra: String? = null) {
        copyToClipboard(
            context,
            format(
                action = action,
                message = throwable.message ?: throwable.toString(),
                stackTrace = Log.getStackTraceString(throwable),
                extra = extra,
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            )
        )
    }

    fun copyFromText(context: Context, action: String, errorText: String, extra: String? = null) {
        copyToClipboard(
            context,
            format(
                action = action,
                message = errorText,
                stackTrace = null,
                extra = extra,
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            )
        )
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("JupyterDroid error", text))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew testDebugUnitTest --tests "com.jupyterdroid.ErrorReporterTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/util/ErrorReporter.kt app/src/test/kotlin/com/jupyterdroid/ErrorReporterTest.kt
git commit -m "Add ErrorReporter: format and copy diagnostic bundles to clipboard"
```

---

### Task 2: Wire `MainActivity` — open-file failure

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/MainActivity.kt`

**Interfaces:**
- Consumes: `ErrorReporter.copyFromThrowable(context, action, throwable, extra)` from Task 1.

No dedicated test — this is a one-catch-block wiring change in an `Activity`, verified by compiling and by manual check in Task 5.

- [ ] **Step 1: Replace the Toast with a Snackbar + copy action**

In `app/src/main/kotlin/com/jupyterdroid/MainActivity.kt`, replace `openFromUri` (current lines 54–64) with:

```kotlin
    private fun openFromUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            openNotebook(uri.toString())
        } catch (e: Exception) {
            Snackbar.make(
                findViewById(R.id.recentFilesRecyclerView),
                "Cannot open file",
                Snackbar.LENGTH_INDEFINITE
            ).setAction("Copy details") {
                ErrorReporter.copyFromThrowable(this, "Open notebook", e)
                Toast.makeText(this, "Copied — paste to Claude", Toast.LENGTH_SHORT).show()
            }.show()
        }
    }
```

Add imports at the top of the file:

```kotlin
import com.google.android.material.snackbar.Snackbar
import com.jupyterdroid.util.ErrorReporter
```

- [ ] **Step 2: Build and confirm no compile errors**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/MainActivity.kt
git commit -m "MainActivity: replace open-file Toast with copyable Snackbar"
```

---

### Task 3: Wire `NotebookActivity` — open, save, and kernel-crash failures

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt`

**Interfaces:**
- Consumes: `ErrorReporter.copyFromThrowable(context, action, throwable, extra)` from Task 1.

Five call sites in one file: two "open" catch blocks (Uri path, File path — both get action `"Open notebook"`), two "save" catch blocks (Uri path, File path — both get action `"Save notebook"`), and the kernel-crash catch in `runCell` (action `"Kernel crash"`, with the cell's source as `extra`). No dedicated test — verified by compiling and manual check in Task 5.

- [ ] **Step 1: Replace both "Failed to open" Toasts**

In `app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt`, replace the `cells` loading block (current lines 44–68) with:

```kotlin
        val cells: MutableList<Cell> = when {
            intent.hasExtra(EXTRA_URI) -> {
                currentUri = Uri.parse(intent.getStringExtra(EXTRA_URI))
                try {
                    val (json, loaded) = NotebookFile.read(contentResolver, currentUri!!)
                    notebookJson = json
                    loaded.toMutableList()
                } catch (e: Exception) {
                    showCopyableError("Open notebook", e)
                    mutableListOf()
                }
            }
            intent.hasExtra(EXTRA_FILE_PATH) -> {
                currentFile = File(intent.getStringExtra(EXTRA_FILE_PATH)!!)
                try {
                    val (json, loaded) = NotebookFile.read(currentFile!!)
                    notebookJson = json
                    loaded.toMutableList()
                } catch (e: Exception) {
                    showCopyableError("Open notebook", e)
                    mutableListOf()
                }
            }
            else -> mutableListOf()
        }
```

- [ ] **Step 2: Replace both "Save failed" Toasts**

Replace `save()` (current lines 138–158) with:

```kotlin
    private fun save() {
        currentUri?.let { uri ->
            try {
                NotebookFile.write(contentResolver, uri, notebookJson, adapter.cells)
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showCopyableError("Save notebook", e)
            }
            return
        }
        val file = currentFile ?: run {
            val dir = getExternalFilesDir(null) ?: filesDir
            File(dir, "notebook_${System.currentTimeMillis()}.ipynb").also { currentFile = it }
        }
        try {
            NotebookFile.write(notebookJson, adapter.cells, file)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showCopyableError("Save notebook", e)
        }
    }
```

- [ ] **Step 3: Replace the bare "Kernel restarted" Snackbar**

Replace `runCell` (current lines 102–122) with:

```kotlin
    private fun runCell(position: Int) {
        val cell = adapter.cells.getOrNull(position) as? Cell.Code ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    km.execute(cell.source)
                } catch (e: Exception) {
                    km.reset()
                    withContext(Dispatchers.Main) {
                        showCopyableError("Kernel crash", e, extra = cell.source)
                    }
                    null
                }
            }
            result?.let { adapter.updateCellOutput(position, it) }
        }
    }
```

- [ ] **Step 4: Add the shared `showCopyableError` helper**

Add this private function to `NotebookActivity` (e.g. directly above `private fun save()`):

```kotlin
    private fun showCopyableError(action: String, throwable: Throwable, extra: String? = null) {
        Snackbar.make(
            findViewById(R.id.cellsRecyclerView),
            action,
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Copy details") {
            ErrorReporter.copyFromThrowable(this, action, throwable, extra)
            Toast.makeText(this, "Copied — paste to Claude", Toast.LENGTH_SHORT).show()
        }.show()
    }
```

Add the import at the top of the file:

```kotlin
import com.jupyterdroid.util.ErrorReporter
```

- [ ] **Step 5: Build and confirm no compile errors**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt
git commit -m "NotebookActivity: copyable Snackbar for open, save, and kernel-crash errors"
```

---

### Task 4: Wire `CodeCellViewHolder` — cell execution error copy button

**Files:**
- Modify: `app/src/main/res/layout/item_cell_code.xml`
- Modify: `app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt`

**Interfaces:**
- Consumes: `ErrorReporter.copyFromText(context, action, errorText, extra)` from Task 1.

Cell stderr is already persistently visible (not transient), so this uses an inline button instead of a Snackbar. No dedicated test — `CodeCellViewHolder` has no existing test file and this is a thin View-binding change; verified by compiling and manual check in Task 5.

- [ ] **Step 1: Add a "Copy error" button to the cell layout**

In `app/src/main/res/layout/item_cell_code.xml`, add a button after the `outputText` `TextView` (i.e. as the last child of the root `LinearLayout`):

```xml
    <Button
        android:id="@+id/copyErrorButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end"
        android:text="Copy error"
        android:textSize="12sp"
        android:visibility="gone"
        style="?android:attr/borderlessButtonStyle" />
```

- [ ] **Step 2: Wire the button in `CodeCellViewHolder`**

In `app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt`, add a field for the new button:

```kotlin
    val copyErrorButton: Button = view.findViewById(R.id.copyErrorButton)
```

(place it next to the existing `outputText` field declaration)

Replace the `when` block inside `bind` (current lines 32–47) with:

```kotlin
        when {
            cell.error.isNotEmpty() -> {
                outputText.visibility = View.VISIBLE
                outputText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.error_red)
                )
                outputText.text = cell.error
                copyErrorButton.visibility = View.VISIBLE
                copyErrorButton.setOnClickListener {
                    ErrorReporter.copyFromText(itemView.context, "Cell execution", cell.error, extra = cell.source)
                    Toast.makeText(itemView.context, "Copied — paste to Claude", Toast.LENGTH_SHORT).show()
                }
            }
            cell.output.isNotEmpty() -> {
                outputText.visibility = View.VISIBLE
                outputText.setTextColor(
                    ContextCompat.getColor(itemView.context, android.R.color.black)
                )
                outputText.text = cell.output
                copyErrorButton.visibility = View.GONE
            }
            else -> {
                outputText.visibility = View.GONE
                copyErrorButton.visibility = View.GONE
            }
        }
```

Add imports at the top of the file:

```kotlin
import android.widget.Button
import android.widget.Toast
import com.jupyterdroid.util.ErrorReporter
```

- [ ] **Step 3: Build and confirm no compile errors**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/item_cell_code.xml app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt
git commit -m "CodeCellViewHolder: add copy-error button for cell execution errors"
```

---

### Task 5: Manual verification on device

**Files:** none (verification only)

This can be run in the same on-device session as the write-back-to-original-file feature's own manual verification (same branch, same build).

- [ ] **Step 1: Build and install the debug APK**

Run: `./gradlew installDebug`
Expected: BUILD SUCCESSFUL, app installed on the connected device.

- [ ] **Step 2: Trigger and copy an open-file failure**

Revoke the app's access to a previously-picked file (e.g. delete that file, or use another file manager to move it), then reopen it from Recents in the app.
Expected: a Snackbar reading "Open notebook" appears and stays on screen (does not auto-dismiss). Tap **Copy details**. Expected: a "Copied — paste to Claude" toast appears. Paste the clipboard into a notes app and confirm it contains a timestamp, "Open notebook", an exception message, and a stack trace.

- [ ] **Step 3: Trigger and copy a kernel crash**

Open any notebook, add a code cell with `import os; os._exit(1)` (or any input guaranteed to crash the Python process — check with a division by zero first: `1/0` triggers a normal Python exception which is *not* a kernel crash, it's cell-level stderr; a true kernel crash needs the process itself to die, e.g. `os._exit(1)`), run it.
Expected: a Snackbar reading "Kernel crash" appears and stays on screen. Tap **Copy details**. Paste and confirm the clipboard contains the exception detail and the cell's source (`import os; os._exit(1)`) under "Cell source:".

- [ ] **Step 4: Trigger and copy a cell execution error**

Add a code cell with `1/0` and run it.
Expected: red error text appears under the cell, and a "Copy error" button appears below it (button is not visible for cells with no error, e.g. a cell with `print(1)`). Tap **Copy error**. Expected: "Copied — paste to Claude" toast. Paste and confirm the clipboard contains "Cell execution", the `ZeroDivisionError` text, and `1/0` under "Cell source:".

- [ ] **Step 5: Trigger and copy a save failure**

Revoke write access to a picked file if possible (e.g. via a file manager that can toggle read-only), or skip this step if it can't be reproduced easily on this device — note in the report if skipped.
Expected (if reproduced): a Snackbar reading "Save notebook" appears, Copy details works the same way.

- [ ] **Step 6: Report results**

If all checks pass, this feature is complete. If any check fails, note which step and return to the relevant task above to fix before proceeding.

---

## Self-Review

**Spec coverage:**
- `ErrorReporter.format`/`copyFromThrowable`/`copyFromText` → Task 1. ✓
- MainActivity open-file Toast → Snackbar with copy action → Task 2. ✓
- NotebookActivity open (×2), save (×2), kernel-crash Toasts/Snackbar → copyable Snackbar → Task 3. ✓
- CodeCellViewHolder cell-error copy button → Task 4. ✓
- Copied text format (timestamp, action, message, stack trace, extra, app version) → implemented exactly per spec's example in Task 1's `format`. ✓
- `LENGTH_INDEFINITE` for all Snackbars offering a copy action → Tasks 2 and 3. ✓
- Confirmation toast text ("Copied — paste to Claude") → used identically in Tasks 2, 3, and 4. ✓
- No persistent log/history — confirmed no such storage introduced anywhere in this plan. ✓
- Manual, on-device confirmation of all four error paths → Task 5. ✓

**Placeholder scan:** No TBD/TODO; every step has literal code or an exact command with expected output.

**Type consistency:** `ErrorReporter.copyFromThrowable(context, action, throwable, extra)` and `copyFromText(context, action, errorText, extra)` signatures from Task 1 are used identically in Tasks 2, 3, and 4. The shared `showCopyableError(action, throwable, extra)` helper introduced in Task 3 is used consistently across all three of `NotebookActivity`'s call sites in that same task.
