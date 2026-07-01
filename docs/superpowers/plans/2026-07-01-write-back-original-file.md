# Write Back to Original File Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Notebooks opened via the Android file picker save back to the original file the user picked, instead of a private cache copy.

**Architecture:** `NotebookFile` (existing util object, currently File-only I/O) grows a second pair of read/write functions that operate on `ContentResolver` + `Uri` instead of `java.io.File`. `NotebookActivity` and `MainActivity` are wired to use the Uri path when a notebook was opened via the picker, and the existing File path unchanged for notebooks created inside the app. The cache-copy step in `MainActivity.openFromUri` is deleted — no code should write a copy of a picker-opened notebook into cache.

**Tech Stack:** Kotlin, Android SDK (`ContentResolver`, `Uri`, `DocumentFile` from `androidx.documentfile`), `kotlinx.serialization`, JUnit4 (unit tests under `app/src/test`), AndroidJUnit4 (instrumented tests under `app/src/androidTest`).

## Global Constraints

- Only the file-picker flow changes. Notebooks created via "New notebook" keep saving into `getExternalFilesDir(null)` (or `filesDir` fallback) exactly as today — no SAF save picker for that path.
- No cache copy for picker-opened notebooks — read and write go straight through the picked Uri.
- Use `contentResolver.openOutputStream(uri, "wt")` for writes (truncate-on-write is required, not the default `"w"` mode, per Android's SAF documentation).
- `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)` must be called at pick time so access survives app restarts.
- I/O failures show a `Toast` with the OS error message — same pattern already used elsewhere in `NotebookActivity`/`MainActivity`. No fallback to a cache copy.
- No new sealed classes/interfaces for the File/Uri duality — a second nullable field (`currentUri: Uri?`) mirrors the existing `currentFile: File?` field.

---

### Task 1: `NotebookFile` — extract pure `serialize`/`read(text)` helpers

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt`
- Test: `app/src/test/kotlin/com/jupyterdroid/NotebookFileTest.kt`

**Interfaces:**
- Produces: `NotebookFile.serialize(notebookJson: NotebookJson, cells: List<Cell>): String`
- Produces: `NotebookFile.read(text: String): Pair<NotebookJson, List<Cell>>`
- Existing `NotebookFile.read(file: File)` and `NotebookFile.write(notebookJson, cells, file: File)` keep their signatures, now implemented in terms of the two functions above.

This is a pure refactor (no behavior change) that Task 2's Uri functions will reuse, avoiding duplicated serialization logic between the File and Uri code paths.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/kotlin/com/jupyterdroid/NotebookFileTest.kt` (inside the existing `NotebookFileTest` class, alongside the current two tests):

```kotlin
    @Test
    fun `serialize and read(text) round trip through a string`() {
        val fixture = File(javaClass.classLoader!!.getResource("fixture.ipynb")!!.file)
        val (notebookJson, cells) = NotebookFile.read(fixture)

        val text = NotebookFile.serialize(notebookJson, cells)
        val (rereadJson, rereadCells) = NotebookFile.read(text)

        assertEquals(notebookJson, rereadJson)
        assertEquals(cells, rereadCells)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.jupyterdroid.NotebookFileTest"`
Expected: FAIL — `serialize` and `read(text)` are unresolved references (compile error).

- [ ] **Step 3: Refactor `NotebookFile.kt` to add the two functions**

Replace the `read`/`write` section of `app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt` (lines 18–26) with:

```kotlin
    fun read(file: File): Pair<NotebookJson, List<Cell>> = read(file.readText())

    fun read(text: String): Pair<NotebookJson, List<Cell>> {
        val nb = json.decodeFromString<NotebookJson>(text)
        return Pair(nb, nb.cells.map { it.toCell() })
    }

    fun write(notebookJson: NotebookJson, cells: List<Cell>, file: File) {
        file.writeText(serialize(notebookJson, cells))
    }

    fun serialize(notebookJson: NotebookJson, cells: List<Cell>): String {
        val updated = notebookJson.copy(cells = cells.map { it.toCellJson() })
        return json.encodeToString(updated)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.jupyterdroid.NotebookFileTest"`
Expected: PASS (all three tests in the class, including the two pre-existing ones — this confirms the refactor didn't change File-based behavior).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt app/src/test/kotlin/com/jupyterdroid/NotebookFileTest.kt
git commit -m "Extract serialize()/read(text) helpers in NotebookFile"
```

---

### Task 2: `NotebookFile` — add Uri-based read/write via `ContentResolver`

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt`
- Test: `app/src/androidTest/kotlin/com/jupyterdroid/NotebookFileUriTest.kt` (new file)

**Interfaces:**
- Consumes: `NotebookFile.serialize(notebookJson, cells): String` and `NotebookFile.read(text: String)` from Task 1.
- Produces: `NotebookFile.read(resolver: ContentResolver, uri: Uri): Pair<NotebookJson, List<Cell>>`
- Produces: `NotebookFile.write(resolver: ContentResolver, uri: Uri, notebookJson: NotebookJson, cells: List<Cell>)`

This needs a real Android `ContentResolver`, so the test is instrumented (`androidTest`), not a JVM unit test. `Uri.fromFile(tempFile)` is used as the test double — `ContentResolver.openInputStream`/`openOutputStream` both handle `file://` scheme URIs directly (no content provider needed), so this exercises the exact code path production will use, just with a `file://` Uri instead of a `content://` one.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/kotlin/com/jupyterdroid/NotebookFileUriTest.kt`:

```kotlin
package com.jupyterdroid

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.NotebookJson
import com.jupyterdroid.util.NotebookFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NotebookFileUriTest {

    private val resolver = InstrumentationRegistry.getInstrumentation()
        .targetContext.contentResolver

    @Test
    fun writeThenReadRoundTripsThroughUri() {
        val tempFile = File.createTempFile("uri_test", ".ipynb")
        val uri = Uri.fromFile(tempFile)
        val cells = listOf(Cell.Code(source = "print('hi')"), Cell.Markdown(source = "# Title"))

        NotebookFile.write(resolver, uri, NotebookJson(), cells)
        val (_, readCells) = NotebookFile.read(resolver, uri)

        assertEquals(cells, readCells)
    }

    @Test
    fun writeTruncatesPreviousLongerContent() {
        val tempFile = File.createTempFile("uri_truncate_test", ".ipynb")
        val uri = Uri.fromFile(tempFile)

        NotebookFile.write(
            resolver, uri, NotebookJson(),
            listOf(Cell.Code(source = "a very long line of source code that will be overwritten"))
        )
        NotebookFile.write(resolver, uri, NotebookJson(), listOf(Cell.Code(source = "x")))

        val (_, cells) = NotebookFile.read(resolver, uri)
        assertEquals(1, cells.size)
        assertEquals("x", (cells[0] as Cell.Code).source)
        // No leftover bytes from the longer first write past the JSON's closing brace.
        assertTrue(tempFile.readText().trim().endsWith("}"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest --tests "com.jupyterdroid.NotebookFileUriTest"` (requires a connected device/emulator)
Expected: FAIL — compile error, `NotebookFile.read(resolver, uri)` / `NotebookFile.write(resolver, uri, ...)` unresolved.

- [ ] **Step 3: Add the Uri-based functions**

Add to `app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt`, top of file add imports:

```kotlin
import android.content.ContentResolver
import android.net.Uri
```

Add these two functions inside the `NotebookFile` object, after the `write(notebookJson, cells, file: File)` function:

```kotlin
    fun read(resolver: ContentResolver, uri: Uri): Pair<NotebookJson, List<Cell>> {
        val text = resolver.openInputStream(uri)!!.use { it.reader().readText() }
        return read(text)
    }

    fun write(resolver: ContentResolver, uri: Uri, notebookJson: NotebookJson, cells: List<Cell>) {
        resolver.openOutputStream(uri, "wt")!!.use { it.write(serialize(notebookJson, cells).toByteArray()) }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew connectedDebugAndroidTest --tests "com.jupyterdroid.NotebookFileUriTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt app/src/androidTest/kotlin/com/jupyterdroid/NotebookFileUriTest.kt
git commit -m "Add ContentResolver/Uri read-write to NotebookFile"
```

---

### Task 3: `NotebookActivity` — load/save via Uri when opened from the picker

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt`
- Modify: `app/build.gradle.kts` (add `androidx.documentfile` dependency)

**Interfaces:**
- Consumes: `NotebookFile.read(resolver: ContentResolver, uri: Uri)` and `NotebookFile.write(resolver: ContentResolver, uri: Uri, notebookJson: NotebookJson, cells: List<Cell>)` from Task 2.
- Produces: `NotebookActivity.EXTRA_URI` (companion `const val`, a `String` extra key holding `uri.toString()`), alongside the existing `EXTRA_FILE_PATH`.

No dedicated test for this task — `NotebookActivity`'s save/run flow is triggered through `BottomAppBar` menu clicks and there's no Espresso dependency in this project (only `androidx.test.ext:junit` + `androidx.test:runner`, no UI-interaction testing library). The underlying I/O was already verified in Task 2. This task is wiring, verified manually in Task 5.

- [ ] **Step 1: Add the `androidx.documentfile` dependency**

In `app/build.gradle.kts`, in the `dependencies { ... }` block, add alongside the other `implementation(...)` lines:

```kotlin
    implementation("androidx.documentfile:documentfile:1.0.1")
```

- [ ] **Step 2: Sync and confirm the build still compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no source changes yet — this just confirms the new dependency resolves).

- [ ] **Step 3: Add `EXTRA_URI` and `currentUri` field**

In `app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt`, update the companion object and fields (around lines 25–32):

```kotlin
    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_URI = "uri"
    }

    private lateinit var adapter: NotebookAdapter
    private lateinit var km: KernelManager
    private var notebookJson: NotebookJson = NotebookJson()
    private var currentFile: File? = null
    private var currentUri: Uri? = null
```

Add imports at the top of the file:

```kotlin
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
```

- [ ] **Step 4: Route loading through the Uri path when `EXTRA_URI` is present**

Replace the cell-loading block in `onCreate` (current lines 40–50) with:

```kotlin
        val cells: MutableList<Cell> = when {
            intent.hasExtra(EXTRA_URI) -> {
                currentUri = Uri.parse(intent.getStringExtra(EXTRA_URI))
                try {
                    val (json, loaded) = NotebookFile.read(contentResolver, currentUri!!)
                    notebookJson = json
                    loaded.toMutableList()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to open: ${e.message}", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "Failed to open: ${e.message}", Toast.LENGTH_LONG).show()
                    mutableListOf()
                }
            }
            else -> mutableListOf()
        }
```

- [ ] **Step 5: Route saving through the Uri path when `currentUri` is set**

Replace `save()` (current lines 118–129) with:

```kotlin
    private fun save() {
        currentUri?.let { uri ->
            try {
                NotebookFile.write(contentResolver, uri, notebookJson, adapter.cells)
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
```

- [ ] **Step 6: Use the DocumentFile display name for the title bar**

Replace the title line (current line 79, `title = currentFile?.name ?: "Untitled.ipynb"`) with:

```kotlin
        title = currentFile?.name
            ?: currentUri?.let { DocumentFile.fromSingleUri(this, it)?.name }
            ?: "Untitled.ipynb"
```

- [ ] **Step 7: Build and confirm no compile errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt
git commit -m "NotebookActivity: load/save via Uri for picker-opened notebooks"
```

---

### Task 4: `MainActivity` — persist Uri permission, drop cache copy, update Recents

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/MainActivity.kt`

**Interfaces:**
- Consumes: `NotebookActivity.EXTRA_URI` (from Task 3).

No dedicated automated test — this is SharedPreferences + Intent wiring plus a real SAF picker interaction, which can't be driven without Espresso/UiAutomator (not present in this project, and adding one for a single flow would be new test infra beyond this task's scope). Verified manually in Task 5.

- [ ] **Step 1: Take persistable permission and skip the cache copy in `openFromUri`**

Replace `openFromUri` (current lines 54–64) in `app/src/main/kotlin/com/jupyterdroid/MainActivity.kt` with:

```kotlin
    private fun openFromUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            openNotebook(uri.toString())
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
```

- [ ] **Step 2: Route `openNotebook` by scheme (Uri string vs. plain file path)**

Replace `openNotebook` (current lines 66–72) with:

```kotlin
    private fun openNotebook(path: String) {
        saveRecent(path)
        val intent = Intent(this, NotebookActivity::class.java)
        if (path.startsWith("content://")) {
            intent.putExtra(NotebookActivity.EXTRA_URI, path)
        } else {
            intent.putExtra(NotebookActivity.EXTRA_FILE_PATH, path)
        }
        startActivity(intent)
    }
```

- [ ] **Step 3: Fix `loadRecent`'s sort — `File(it).lastModified()` breaks for `content://` strings**

`loadRecent` currently sorts recents by `File(it).lastModified()` (current line 77), which returns `0` for a `content://` string (not a valid file path) rather than throwing — every Uri-backed entry would sort as "oldest". Since there's no reliable last-modified timestamp available for an arbitrary SAF Uri without an extra query per item, switch to insertion order instead: track recents as an ordered list (most-recent-first) rather than a `Set`.

Replace `loadRecent`/`saveRecent` (current lines 74–85) with:

```kotlin
    private fun loadRecent(): List<String> =
        getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString(recentKey, "")
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun saveRecent(path: String) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val existing = loadRecent().filter { it != path }
        val updated = (listOf(path) + existing).take(20)
        prefs.edit().putString(recentKey, updated.joinToString("\n")).apply()
    }
```

(`take(20)` caps the list — matches the existing implicit no-growth-bound-was-fine-because-Set-dedup behavior closely enough without unbounded growth; this is the smallest change that fixes the sort bug, not a scope expansion.)

- [ ] **Step 4: Display names for `content://` entries in Recents**

Replace `RecentAdapter.onBindViewHolder` (current lines 99–103) with:

```kotlin
        override fun onBindViewHolder(holder: VH, position: Int) {
            val path = paths[position]
            holder.text.text = if (path.startsWith("content://")) {
                DocumentFile.fromSingleUri(holder.itemView.context, Uri.parse(path))?.name ?: path
            } else {
                path.substringAfterLast("/")
            }
            holder.itemView.setOnClickListener { onClick(path) }
        }
```

Add import at the top of the file:

```kotlin
import androidx.documentfile.provider.DocumentFile
```

- [ ] **Step 5: Build and confirm no compile errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/MainActivity.kt
git commit -m "MainActivity: persist Uri permission, drop cache copy, fix Recents for picker-opened notebooks"
```

---

### Task 5: Manual verification on device

**Files:** none (verification only)

- [ ] **Step 1: Build and install the debug APK**

Run: `./gradlew installDebug`
Expected: BUILD SUCCESSFUL, app installed on the connected device.

- [ ] **Step 2: Create a test `.ipynb` file outside the app**

On the device (or via `adb push`), place a small notebook file somewhere visible to the file picker (e.g. `Downloads/manual_test.ipynb`) with content:

```json
{"nbformat": 4, "nbformat_minor": 5, "metadata": {}, "cells": []}
```

- [ ] **Step 3: Open it via the picker, edit, save**

In the app: **Open Notebook** → pick `manual_test.ipynb` from Downloads → add a code cell with `print("write-back works")` → tap **Save**.
Expected: "Saved" toast, no error.

- [ ] **Step 4: Confirm the original file changed, not a cache copy**

Run: `adb shell run-as com.jupyterdroid ls cache/` — expected: no `opened_*.ipynb` files (the cache-copy step is gone).
Then inspect `manual_test.ipynb` directly (e.g. `adb pull` it, or open it in another app) and confirm it now contains the `print("write-back works")` cell.

- [ ] **Step 5: Confirm Recents reopen works and still writes back**

Force-close and reopen the app. Tap the `manual_test.ipynb` entry in Recents. Expected: it opens with the previously saved cell content, title bar shows `manual_test.ipynb`. Edit again, save, and re-confirm the change lands in the original file (repeat Step 4's check).

- [ ] **Step 6: Confirm "New notebook" is unaffected**

Tap **New Notebook**, add a cell, save. Expected: unchanged behavior — saves into the app's private external-files folder, no picker prompt, no crash.

- [ ] **Step 7: Report results**

If all checks pass, this feature is complete — no commit needed for this task (verification only). If any check fails, note which step and return to the relevant task above to fix before proceeding.

---

## Self-Review

**Spec coverage:**
- `NotebookFile.kt` `serialize`/`read(text)` split → Task 1. ✓
- Uri-based read/write via `ContentResolver` → Task 2. ✓
- `MainActivity` persistable permission + no cache copy → Task 4, Step 1. ✓
- Recents storage as Uri string + scheme detection on reopen → Task 4, Steps 2–3. ✓
- Recents display name via `DocumentFile` → Task 4, Step 4. ✓
- `NotebookActivity` `EXTRA_URI`/`currentUri`, load/save routing, `"wt"` truncate mode, DocumentFile title → Task 3. ✓
- Error handling via Toast, no fallback to cache → Task 3 Steps 4–5 (try/catch + Toast, matching existing pattern). ✓
- New notebooks unaffected (no SAF save picker) → confirmed unchanged in Task 3 Step 5 (File branch untouched) and verified in Task 5 Step 6. ✓

**Placeholder scan:** No TBD/TODO markers; every step has literal code or an exact command with expected output.

**Type consistency:** `NotebookFile.read`/`write`/`serialize` signatures introduced in Task 1–2 are used identically in Task 3 (`NotebookFile.read(contentResolver, currentUri!!)`, `NotebookFile.write(contentResolver, uri, notebookJson, adapter.cells)`). `EXTRA_URI` defined in Task 3 is consumed with the same string key in Task 4 (`intent.putExtra(NotebookActivity.EXTRA_URI, path)`).

**One bug found and fixed during planning:** `loadRecent`'s existing sort-by-`File(it).lastModified()` silently breaks once Uri strings enter the recents list (returns 0, not an exception) — addressed in Task 4 Step 3 by switching to an ordered list instead of sorting a `Set`. This was in scope because Task 4 is the task introducing Uri strings into that same list.
