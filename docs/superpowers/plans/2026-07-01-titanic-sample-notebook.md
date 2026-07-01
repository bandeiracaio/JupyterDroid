# Bundled Titanic Sample Notebook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** New users get a working, runnable Titanic-dataset analysis notebook seeded into Recents on first launch, using only stdlib (no pandas, no rich outputs) so it works within the app's current text-output-only capability.

**Architecture:** A CSV data file bundled next to `kernel_runner.py` (Chaquopy packages everything under `src/main/python`), a small path-resolution helper added to `kernel_runner.py`, a static nbformat-4 notebook bundled as an Android asset, and a one-time first-launch copy-into-Recents step in `MainActivity`.

**Tech Stack:** Kotlin, Android SDK (`AssetManager`, `SharedPreferences`), Python (stdlib `csv`/`collections` only), JUnit4 (JVM unit test for the notebook JSON), AndroidJUnit4 (instrumented test for the Python data path, reusing the existing `KernelManagerTest` fixture).

## Global Constraints

- No pandas, no matplotlib, no rich outputs — all notebook code must produce only text output via `print(...)`, matching the app's current text-stdout-only rendering.
- The CSV is bundled, not downloaded — no network access required to use the sample notebook.
- Seeding happens exactly once, via a dedicated `SharedPreferences` boolean flag (`sample_notebook_seeded`) — not tied to "Recents is empty" — so it never reappears after the user deletes the seeded notebook.
- If the one-time seed copy fails, it fails silently: no toast, no retry, does not block app startup.
- The already-present, real Titanic dataset at `app/src/main/python/titanic.csv` (891 data rows + 1 header row, columns `PassengerId,Survived,Pclass,Name,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked`) is the exact file to use — do not regenerate or alter it.

---

### Task 1: `kernel_runner.data_path()` + bundled CSV + instrumented verification

**Files:**
- Modify: `app/src/main/python/kernel_runner.py`
- Verify (already present in the working tree, untracked): `app/src/main/python/titanic.csv`
- Modify: `app/src/androidTest/kotlin/com/jupyterdroid/KernelManagerTest.kt`

**Interfaces:**
- Produces: `kernel_runner.data_path(filename: str) -> str` — returns the absolute path to a file bundled alongside `kernel_runner.py`.

`titanic.csv` already exists on disk at `app/src/main/python/titanic.csv` (891 data rows, verified: `wc -l` reports 892 total lines including the header). It is currently untracked in git — this task's commit is what adds it to version control.

No JVM unit test is possible here (this exercises the real Chaquopy/Python runtime) — verification is an instrumented test, following the existing pattern in `KernelManagerTest.kt`. No Android device is connected in this environment; this task's instrumented test is written and compiled, not run, per the note in Step 2 below — actual execution happens in Task 4 (manual verification on device).

- [ ] **Step 1: Add `data_path()` to `kernel_runner.py`**

In `app/src/main/python/kernel_runner.py`, change the import block at the top from:

```python
import sys
import io
import traceback
import subprocess
```

to:

```python
import sys
import io
import os
import traceback
import subprocess
```

Then add this function immediately after the `_globals = {}` line (before `def execute(source):`):

```python
def data_path(filename):
    return os.path.join(os.path.dirname(__file__), filename)
```

- [ ] **Step 2: Add the instrumented verification test**

In `app/src/androidTest/kotlin/com/jupyterdroid/KernelManagerTest.kt`, add a new test method inside the existing `KernelManagerTest` class (after `statePersistedAcrossCells`, before the closing `}` of the class):

```kotlin
    @Test
    fun dataPathResolvesToBundledTitanicCsv() {
        val result = km.execute(
            """
            import kernel_runner, csv
            with open(kernel_runner.data_path("titanic.csv")) as f:
                rows = list(csv.DictReader(f))
            print(len(rows))
            """.trimIndent()
        )
        assertEquals("891\n", result.output)
        assertEquals("", result.error)
    }
```

This test cannot be run in this environment (no device/emulator connected). Instead:

- [ ] **Step 3: Confirm the androidTest source set still compiles**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/python/kernel_runner.py app/src/main/python/titanic.csv app/src/androidTest/kotlin/com/jupyterdroid/KernelManagerTest.kt
git commit -m "Bundle Titanic dataset and add kernel_runner.data_path() helper"
```

---

### Task 2: `sample_titanic.ipynb` asset + JSON structure test

**Files:**
- Create: `app/src/main/assets/sample_titanic.ipynb`
- Test: `app/src/test/kotlin/com/jupyterdroid/SampleNotebookTest.kt` (new file)

**Interfaces:**
- Consumes: `NotebookFile.read(file: File): Pair<NotebookJson, List<Cell>>` (existing, in `app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt`).
- Consumes: `kernel_runner.data_path("titanic.csv")` from Task 1 (referenced inside the notebook's code cells as plain text — Task 1 need not be complete for this task's own test to pass, since this task only validates JSON structure, not execution).

A JVM unit test's working directory for this module is `app/` (verified: `File("src/main/assets/sample_titanic.ipynb").exists()` resolves correctly from a unit test), so the asset can be read directly as a `File` without any Android framework dependency — same pattern as `NotebookFileTest`'s `fixture.ipynb`, except this one lives under `src/main/assets` (a real asset, not a test fixture) so it can also be read by the app itself via `AssetManager` in Task 3.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/jupyterdroid/SampleNotebookTest.kt`:

```kotlin
package com.jupyterdroid

import com.jupyterdroid.model.Cell
import com.jupyterdroid.util.NotebookFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SampleNotebookTest {

    @Test
    fun `sample notebook has ten cells alternating markdown and code`() {
        val (_, cells) = NotebookFile.read(File("src/main/assets/sample_titanic.ipynb"))

        assertEquals(10, cells.size)
        cells.forEachIndexed { i, cell ->
            if (i % 2 == 0) {
                assertTrue("cell $i should be Markdown", cell is Cell.Markdown)
            } else {
                assertTrue("cell $i should be Code", cell is Cell.Code)
            }
        }
    }

    @Test
    fun `sample notebook code cells reference the bundled dataset and use only stdlib`() {
        val (_, cells) = NotebookFile.read(File("src/main/assets/sample_titanic.ipynb"))
        val codeCells = cells.filterIsInstance<Cell.Code>()

        assertTrue(codeCells.first().source.contains("kernel_runner.data_path(\"titanic.csv\")"))
        codeCells.forEach { cell ->
            assertTrue(
                "cell should not import pandas: ${cell.source}",
                !cell.source.contains("import pandas")
            )
            assertTrue(
                "cell should not import matplotlib: ${cell.source}",
                !cell.source.contains("import matplotlib")
            )
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew testDebugUnitTest --tests "com.jupyterdroid.SampleNotebookTest"`
Expected: FAIL — `src/main/assets/sample_titanic.ipynb` does not exist yet.

- [ ] **Step 3: Create the notebook asset**

Create the directory and file `app/src/main/assets/sample_titanic.ipynb` with exactly this content:

```json
{
  "nbformat": 4,
  "nbformat_minor": 5,
  "metadata": {
    "kernelspec": {
      "display_name": "Python 3",
      "language": "python",
      "name": "python3"
    },
    "language_info": {
      "name": "python"
    }
  },
  "cells": [
    {
      "cell_type": "markdown",
      "source": [
        "# Titanic passenger survival\n",
        "\n",
        "A quick text-based look at the classic Titanic passenger dataset (891 passengers) using only Python's standard library — no external packages required."
      ],
      "metadata": {},
      "outputs": []
    },
    {
      "cell_type": "code",
      "source": [
        "import csv\n",
        "import kernel_runner\n",
        "\n",
        "with open(kernel_runner.data_path(\"titanic.csv\")) as f:\n",
        "    rows = list(csv.DictReader(f))\n",
        "\n",
        "print(f\"Loaded {len(rows)} passengers\")"
      ],
      "metadata": {},
      "outputs": [],
      "execution_count": null
    },
    {
      "cell_type": "markdown",
      "source": [
        "## Overall survival rate"
      ],
      "metadata": {},
      "outputs": []
    },
    {
      "cell_type": "code",
      "source": [
        "survived = sum(1 for r in rows if r[\"Survived\"] == \"1\")\n",
        "rate = survived / len(rows) * 100\n",
        "print(f\"Overall survival rate: {rate:.1f}% ({survived}/{len(rows)})\")"
      ],
      "metadata": {},
      "outputs": [],
      "execution_count": null
    },
    {
      "cell_type": "markdown",
      "source": [
        "## By sex"
      ],
      "metadata": {},
      "outputs": []
    },
    {
      "cell_type": "code",
      "source": [
        "from collections import defaultdict\n",
        "\n",
        "def survival_rate_by(key):\n",
        "    groups = defaultdict(lambda: [0, 0])\n",
        "    for r in rows:\n",
        "        g = groups[r[key]]\n",
        "        g[1] += 1\n",
        "        if r[\"Survived\"] == \"1\":\n",
        "            g[0] += 1\n",
        "    for value, (s, t) in sorted(groups.items()):\n",
        "        print(f\"{value}: {s}/{t} ({s / t * 100:.1f}%)\")\n",
        "\n",
        "survival_rate_by(\"Sex\")"
      ],
      "metadata": {},
      "outputs": [],
      "execution_count": null
    },
    {
      "cell_type": "markdown",
      "source": [
        "## By passenger class"
      ],
      "metadata": {},
      "outputs": []
    },
    {
      "cell_type": "code",
      "source": [
        "survival_rate_by(\"Pclass\")"
      ],
      "metadata": {},
      "outputs": [],
      "execution_count": null
    },
    {
      "cell_type": "markdown",
      "source": [
        "## Age"
      ],
      "metadata": {},
      "outputs": []
    },
    {
      "cell_type": "code",
      "source": [
        "ages_survived = [float(r[\"Age\"]) for r in rows if r[\"Survived\"] == \"1\" and r[\"Age\"]]\n",
        "ages_died = [float(r[\"Age\"]) for r in rows if r[\"Survived\"] == \"0\" and r[\"Age\"]]\n",
        "\n",
        "print(f\"Average age (survived): {sum(ages_survived) / len(ages_survived):.1f}\")\n",
        "print(f\"Average age (did not survive): {sum(ages_died) / len(ages_died):.1f}\")"
      ],
      "metadata": {},
      "outputs": [],
      "execution_count": null
    }
  ]
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew testDebugUnitTest --tests "com.jupyterdroid.SampleNotebookTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/sample_titanic.ipynb app/src/test/kotlin/com/jupyterdroid/SampleNotebookTest.kt
git commit -m "Add bundled Titanic sample notebook asset"
```

---

### Task 3: First-launch seeding into Recents

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/MainActivity.kt`

**Interfaces:**
- Consumes: the asset at `app/src/main/assets/sample_titanic.ipynb` (Task 2), read via `assets.open("sample_titanic.ipynb")`.
- Consumes: existing `saveRecent(path: String)` (private, same file) to register the seeded notebook in Recents.

No dedicated automated test — this uses `AssetManager` and `getExternalFilesDir`, both Android-framework, and `MainActivity.onCreate` has no existing test harness (no Espresso/Robolectric in this project, consistent with prior features in this codebase). Verified by compiling and by manual check in Task 4.

- [ ] **Step 1: Add the seeding call in `onCreate`**

In `app/src/main/kotlin/com/jupyterdroid/MainActivity.kt`, add a call to a new `seedSampleNotebookIfNeeded()` function right after `setContentView(R.layout.activity_main)` in `onCreate` (before `recentAdapter = RecentAdapter(...)`, so the sample notebook is present in Recents by the time it's first loaded):

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        seedSampleNotebookIfNeeded()

        recentAdapter = RecentAdapter(loadRecent()) { path -> openNotebook(path) }
```

- [ ] **Step 2: Implement `seedSampleNotebookIfNeeded()`**

Add this private function to `MainActivity` (e.g. directly above `private fun openFromUri`):

```kotlin
    private fun seedSampleNotebookIfNeeded() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (prefs.getBoolean("sample_notebook_seeded", false)) return

        try {
            val dir = getExternalFilesDir(null) ?: filesDir
            val file = File(dir, "sample_titanic_analysis.ipynb")
            assets.open("sample_titanic.ipynb").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            saveRecent(file.absolutePath)
        } catch (e: Exception) {
            // Onboarding aid only — a failure here must not block startup or surface to the user.
        } finally {
            prefs.edit().putBoolean("sample_notebook_seeded", true).apply()
        }
    }
```

Add the import at the top of the file:

```kotlin
import java.io.File
```

Note: the `finally` block sets the seeded flag even if the copy fails — this matches the spec's "fails silently, never retries" requirement (a transient failure shouldn't cause repeated silent attempts on every future launch either).

- [ ] **Step 3: Build and confirm no compile errors**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/MainActivity.kt
git commit -m "MainActivity: seed bundled Titanic sample notebook into Recents on first launch"
```

---

### Task 4: Manual verification on device

**Files:** none (verification only)

- [ ] **Step 1: Uninstall any existing copy of the app, then build and install fresh**

Run: `adb uninstall com.jupyterdroid` (ignore error if not installed), then `./gradlew installDebug`
Expected: BUILD SUCCESSFUL, app installed. (Uninstalling first is required — the seeding flag persists in SharedPreferences, so reinstalling over an existing install with `sample_notebook_seeded=true` already set would skip seeding and this test would not actually exercise it.)

- [ ] **Step 2: Confirm the sample notebook is seeded on first launch**

Open the app. Expected: "sample_titanic_analysis" (or similar, based on the file name) appears in Recents immediately, with no user action taken.

- [ ] **Step 3: Confirm it runs correctly**

Tap the seeded notebook to open it. Run all cells (bottom app bar → run all). Expected outputs, in order (computed directly from `app/src/main/python/titanic.csv` — exact, not approximate):
- `Loaded 891 passengers`
- `Overall survival rate: 38.4% (342/891)`
- Survival rate by sex:
  - `female: 233/314 (74.2%)`
  - `male: 109/577 (18.9%)`
- Survival rate by passenger class:
  - `1: 136/216 (63.0%)`
  - `2: 87/184 (47.3%)`
  - `3: 119/491 (24.2%)`
- Average age lines:
  - `Average age (survived): 28.3`
  - `Average age (did not survive): 30.6`

No red error text should appear under any cell.

- [ ] **Step 4: Confirm it doesn't reappear/duplicate on next launch**

Force-close and reopen the app (without uninstalling). Expected: Recents shows the same single sample-notebook entry, not a duplicate — confirms the one-time flag works.

- [ ] **Step 5: Confirm the seeded notebook behaves as an ordinary local notebook**

Edit a cell in the seeded notebook, save, reopen from Recents. Expected: the edit persisted, exactly like any notebook created via "New Notebook" (same File-based save path, no special behavior).

- [ ] **Step 6: Run the instrumented test from Task 1**

Run: `JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home ./gradlew connectedDebugAndroidTest --tests "com.jupyterdroid.KernelManagerTest"`
Expected: BUILD SUCCESSFUL, including `dataPathResolvesToBundledTitanicCsv` passing.

- [ ] **Step 7: Report results**

If all checks pass, this feature is complete. If any check fails, note which step and return to the relevant task above to fix before proceeding.

---

## Self-Review

**Spec coverage:**
- CSV bundled at `app/src/main/python/titanic.csv`, `kernel_runner.data_path()` helper → Task 1. ✓
- Notebook content (10 cells, stdlib-only, text-output-only, referencing `data_path`) → Task 2. ✓
- Bundled as Android asset → Task 2 (`app/src/main/assets/sample_titanic.ipynb`). ✓
- One-time `SharedPreferences` boolean flag, distinct from "Recents empty" → Task 3 (`sample_notebook_seeded`). ✓
- Copies into `getExternalFilesDir(null)`, registers via existing `saveRecent` → Task 3. ✓
- Behaves as an ordinary local notebook thereafter (File-based, no new plumbing) → Task 3 (writes a plain `File`, same mechanism "New Notebook" uses) and verified in Task 4 Step 5. ✓
- Silent failure on copy error, no toast, no retry, flag still set to prevent repeated attempts → Task 3 Step 2 (`catch` swallows, `finally` sets the flag unconditionally). ✓
- No network fetch — confirmed nowhere in this plan does any task add network code. ✓
- No pandas/matplotlib — Task 2's second test explicitly asserts their absence from every code cell. ✓

**Placeholder scan:** No TBD/TODO; every step has literal code, exact JSON, or an exact command with expected output (including the specific expected numeric survival-rate outputs in Task 4).

**Type consistency:** `kernel_runner.data_path(filename)` (Task 1) is referenced with the identical call `kernel_runner.data_path("titanic.csv")` in both the Task 1 instrumented test and the Task 2 notebook asset's first code cell. `NotebookFile.read(File)` (existing, used in Task 2's test) matches its actual signature in `NotebookFile.kt`. `saveRecent(path: String)` (existing, used in Task 3) matches its actual private signature in `MainActivity.kt`.
