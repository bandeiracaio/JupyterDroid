# JupyterDroid V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app that creates and runs Jupyter notebooks locally on-device, with code cells, markdown cells, and pip install support.

**Architecture:** Chaquopy embeds CPython in-process; `kernel_runner.py` executes code via `exec()` and captures stdout/stderr; `NotebookFile` serializes to/from standard `.ipynb` JSON (nbformat 4); `RecyclerView` drives the notebook UI with per-cell ViewHolders.

**Tech Stack:** Kotlin, Chaquopy 15.0.1, Python 3.11, kotlinx.serialization 1.6.3, Markwon 4.6.2, Material Components 1.11.0, Kotlin Coroutines 1.7.3

## Global Constraints

- Min SDK: API 24 (Android 7.0) — Target SDK: API 34
- Package name: `com.jupyterdroid`
- Language: Kotlin, JVM target 1.8
- Python version: 3.11 (via Chaquopy)
- `.ipynb` files must be standard Jupyter nbformat 4 — compatible with JupyterLab/VS Code
- No internet required at runtime (offline-first)
- ABI filters: `arm64-v8a`, `x86_64`

---

### Task 1: Project Scaffold

**Files:**
- Create: `build.gradle.kts` (project level)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/jupyterdroid/JupyterDroidApp.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`

**Interfaces:**
- Produces: compilable Android project with Chaquopy, kotlinx.serialization, Markwon, Material3, Coroutines on classpath

- [ ] **Step 1: Create the Android project in Android Studio**

Open Android Studio → New Project → Empty Views Activity.
- Name: `JupyterDroid`
- Package: `com.jupyterdroid`
- Language: Kotlin
- Min SDK: API 24

After creation, delete the auto-generated `MainActivity.kt` — we'll write ours from scratch.

- [ ] **Step 2: Replace project-level build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    id("com.chaquo.python") version "15.0.1" apply false
}
```

- [ ] **Step 3: Replace app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.chaquo.python")
}

android {
    namespace = "com.jupyterdroid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jupyterdroid"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { jvmTarget = "1.8" }
}

chaquopy {
    defaultConfig {
        version = "3.11"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
```

- [ ] **Step 4: Create JupyterDroidApp.kt**

Create `app/src/main/kotlin/com/jupyterdroid/JupyterDroidApp.kt`:

```kotlin
package com.jupyterdroid

import android.app.Application
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.Python

class JupyterDroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
```

- [ ] **Step 5: Write AndroidManifest.xml**

Replace `app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />

    <application
        android:name=".JupyterDroidApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.JupyterDroid">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity android:name=".NotebookActivity" android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 6: Create resource files**

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">JupyterDroid</string>
</resources>
```

`app/src/main/res/values/colors.xml`:
```xml
<resources>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="white">#FFFFFFFF</color>
    <color name="code_background">#F5F5F5</color>
    <color name="error_red">#D32F2F</color>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.JupyterDroid" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
        <item name="colorPrimary">@color/purple_500</item>
        <item name="colorPrimaryVariant">@color/purple_700</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSecondary">@color/teal_200</item>
    </style>
</resources>
```

- [ ] **Step 7: Sync and build**

In Android Studio: File → Sync Project with Gradle Files, then Build → Make Project.

Expected: `BUILD SUCCESSFUL`. Chaquopy downloads Python 3.11 (~30 MB). This takes a few minutes on first sync.

- [ ] **Step 8: Commit**

```bash
git add .
git commit -m "feat: project scaffold with Chaquopy 15.0.1 and dependencies"
```

---

### Task 2: Data Model

**Files:**
- Create: `app/src/main/kotlin/com/jupyterdroid/model/Cell.kt`
- Create: `app/src/main/kotlin/com/jupyterdroid/model/NotebookJson.kt`
- Create: `app/src/test/resources/fixture.ipynb`

**Interfaces:**
- Produces:
  - `Cell` — sealed class with `Cell.Code(source, output, error, executionCount)` and `Cell.Markdown(source)`
  - `NotebookJson(nbformat, nbformatMinor, metadata, cells)` — serializable .ipynb root
  - `NotebookCellJson(cellType, source, metadata, outputs, executionCount)` — serializable cell
  - `CellOutputJson(outputType, name, text)` — serializable cell output

- [ ] **Step 1: Create Cell.kt**

Create `app/src/main/kotlin/com/jupyterdroid/model/Cell.kt`:

```kotlin
package com.jupyterdroid.model

sealed class Cell {
    data class Code(
        val source: String = "",
        val output: String = "",
        val error: String = "",
        val executionCount: Int? = null
    ) : Cell()

    data class Markdown(
        val source: String = ""
    ) : Cell()
}
```

- [ ] **Step 2: Create NotebookJson.kt**

Create `app/src/main/kotlin/com/jupyterdroid/model/NotebookJson.kt`:

```kotlin
package com.jupyterdroid.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class NotebookJson(
    val nbformat: Int = 4,
    @SerialName("nbformat_minor") val nbformatMinor: Int = 5,
    val metadata: JsonObject = defaultMetadata(),
    val cells: List<NotebookCellJson> = emptyList()
)

@Serializable
data class NotebookCellJson(
    @SerialName("cell_type") val cellType: String,
    val source: List<String> = emptyList(),
    val metadata: JsonObject = JsonObject(emptyMap()),
    val outputs: List<CellOutputJson> = emptyList(),
    @SerialName("execution_count") val executionCount: Int? = null
)

@Serializable
data class CellOutputJson(
    @SerialName("output_type") val outputType: String = "stream",
    val name: String = "stdout",
    val text: List<String> = emptyList()
)

private fun defaultMetadata(): JsonObject = JsonObject(
    mapOf(
        "kernelspec" to JsonObject(
            mapOf(
                "display_name" to JsonPrimitive("Python 3"),
                "language" to JsonPrimitive("python"),
                "name" to JsonPrimitive("python3")
            )
        ),
        "language_info" to JsonObject(
            mapOf("name" to JsonPrimitive("python"))
        )
    )
)
```

- [ ] **Step 3: Create test fixture**

Create directory `app/src/test/resources/` and write `fixture.ipynb`:

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
      "cell_type": "code",
      "execution_count": 1,
      "metadata": {},
      "outputs": [
        {
          "output_type": "stream",
          "name": "stdout",
          "text": ["hello\n"]
        }
      ],
      "source": ["print('hello')"]
    },
    {
      "cell_type": "markdown",
      "execution_count": null,
      "metadata": {},
      "outputs": [],
      "source": ["# Hello\n", "World"]
    }
  ]
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/model/ app/src/test/resources/
git commit -m "feat: data model — Cell sealed class and NotebookJson schema"
```

---

### Task 3: NotebookFile + Serialization Test

**Files:**
- Create: `app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt`
- Create: `app/src/test/kotlin/com/jupyterdroid/NotebookFileTest.kt`

**Interfaces:**
- Consumes: `NotebookJson`, `NotebookCellJson`, `CellOutputJson`, `Cell` (Task 2)
- Produces:
  - `NotebookFile.read(file: File): Pair<NotebookJson, List<Cell>>`
  - `NotebookFile.write(notebookJson: NotebookJson, cells: List<Cell>, file: File)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/jupyterdroid/NotebookFileTest.kt`:

```kotlin
package com.jupyterdroid

import com.jupyterdroid.model.Cell
import com.jupyterdroid.util.NotebookFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NotebookFileTest {

    @Test
    fun `read parses code and markdown cells`() {
        val fixture = File(javaClass.classLoader!!.getResource("fixture.ipynb")!!.file)
        val (_, cells) = NotebookFile.read(fixture)

        assertEquals(2, cells.size)
        assertTrue(cells[0] is Cell.Code)
        assertTrue(cells[1] is Cell.Markdown)

        val code = cells[0] as Cell.Code
        assertEquals("print('hello')", code.source)
        assertEquals("hello\n", code.output)
        assertEquals(1, code.executionCount)

        val md = cells[1] as Cell.Markdown
        assertEquals("# Hello\nWorld", md.source)
    }

    @Test
    fun `round trip preserves JSON structure`() {
        val fixture = File(javaClass.classLoader!!.getResource("fixture.ipynb")!!.file)
        val (notebookJson, cells) = NotebookFile.read(fixture)

        val tmp = createTempFile("test", ".ipynb")
        NotebookFile.write(notebookJson, cells, tmp)

        val original = Json.parseToJsonElement(fixture.readText())
        val written = Json.parseToJsonElement(tmp.readText())
        assertEquals(original, written)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "com.jupyterdroid.NotebookFileTest"
```

Expected: FAIL — `NotebookFile` not found.

- [ ] **Step 3: Create NotebookFile.kt**

Create `app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt`:

```kotlin
package com.jupyterdroid.util

import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.CellOutputJson
import com.jupyterdroid.model.NotebookCellJson
import com.jupyterdroid.model.NotebookJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object NotebookFile {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun read(file: File): Pair<NotebookJson, List<Cell>> {
        val nb = json.decodeFromString<NotebookJson>(file.readText())
        return Pair(nb, nb.cells.map { it.toCell() })
    }

    fun write(notebookJson: NotebookJson, cells: List<Cell>, file: File) {
        val updated = notebookJson.copy(cells = cells.map { it.toCellJson() })
        file.writeText(json.encodeToString(updated))
    }

    private fun NotebookCellJson.toCell(): Cell = when (cellType) {
        "code" -> Cell.Code(
            source = source.joinToString(""),
            output = outputs.filter { it.outputType == "stream" && it.name == "stdout" }
                .flatMap { it.text }.joinToString(""),
            error = outputs.filter { it.outputType == "stream" && it.name == "stderr" }
                .flatMap { it.text }.joinToString(""),
            executionCount = executionCount
        )
        else -> Cell.Markdown(source = source.joinToString(""))
    }

    private fun Cell.toCellJson(): NotebookCellJson = when (this) {
        is Cell.Code -> NotebookCellJson(
            cellType = "code",
            source = source.toNotebookLines(),
            outputs = buildList {
                if (output.isNotEmpty())
                    add(CellOutputJson(name = "stdout", text = output.toNotebookLines()))
                if (error.isNotEmpty())
                    add(CellOutputJson(name = "stderr", text = error.toNotebookLines()))
            },
            executionCount = executionCount
        )
        is Cell.Markdown -> NotebookCellJson(
            cellType = "markdown",
            source = source.toNotebookLines(),
            outputs = emptyList(),
            executionCount = null
        )
    }

    // .ipynb format: each line ends with \n except the last
    private fun String.toNotebookLines(): List<String> {
        if (isEmpty()) return emptyList()
        val parts = split("\n")
        return parts.mapIndexed { i, part -> if (i < parts.size - 1) "$part\n" else part }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.jupyterdroid.NotebookFileTest"
```

Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/util/ app/src/test/
git commit -m "feat: NotebookFile read/write with round-trip test"
```

---

### Task 4: Kernel (kernel_runner.py + KernelManager)

**Files:**
- Create: `app/src/main/python/kernel_runner.py`
- Create: `app/src/main/kotlin/com/jupyterdroid/kernel/KernelManager.kt`
- Create: `app/src/androidTest/kotlin/com/jupyterdroid/KernelManagerTest.kt`

**Interfaces:**
- Consumes: Chaquopy `Python` singleton (Task 1)
- Produces:
  - `ExecutionResult(output: String, error: String, executionCount: Int)`
  - `PipResult(stdout: String, stderr: String, success: Boolean)`
  - `KernelManager.getInstance(context: Context): KernelManager`
  - `KernelManager.execute(source: String): ExecutionResult`
  - `KernelManager.pipInstall(packageName: String): PipResult`
  - `KernelManager.reset()`

- [ ] **Step 1: Create kernel_runner.py**

Create `app/src/main/python/kernel_runner.py`:

```python
import sys
import io
import traceback
import subprocess

_execution_count = 0
_globals = {}


def execute(source):
    global _execution_count, _globals
    _execution_count += 1

    stdout_buf = io.StringIO()
    stderr_buf = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = stdout_buf, stderr_buf

    error = ""
    try:
        exec(compile(source, "<cell>", "exec"), _globals)
    except Exception:
        error = traceback.format_exc()
    finally:
        sys.stdout, sys.stderr = old_out, old_err

    return {
        "output": stdout_buf.getvalue(),
        "error": error,
        "execution_count": _execution_count,
    }


def pip_install(package):
    result = subprocess.run(
        [sys.executable, "-m", "pip", "install", package],
        capture_output=True,
        text=True,
    )
    return {
        "stdout": result.stdout,
        "stderr": result.stderr,
        "returncode": result.returncode,
    }


def reset():
    global _execution_count, _globals
    _execution_count = 0
    _globals = {}
```

- [ ] **Step 2: Write the failing instrumented test**

Create `app/src/androidTest/kotlin/com/jupyterdroid/KernelManagerTest.kt`:

```kotlin
package com.jupyterdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupyterdroid.kernel.KernelManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KernelManagerTest {

    private lateinit var km: KernelManager

    @Before
    fun setup() {
        km = KernelManager.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        km.reset()
    }

    @Test
    fun executeReturnsStdout() {
        val result = km.execute("print('hello')")
        assertEquals("hello\n", result.output)
        assertEquals("", result.error)
        assertEquals(1, result.executionCount)
    }

    @Test
    fun executeReturnsErrorOnException() {
        val result = km.execute("1/0")
        assertTrue(result.error.contains("ZeroDivisionError"))
        assertEquals("", result.output)
    }

    @Test
    fun statePersistedAcrossCells() {
        km.execute("x = 42")
        val result = km.execute("print(x)")
        assertEquals("42\n", result.output)
        assertEquals(2, result.executionCount)
    }
}
```

- [ ] **Step 3: Run instrumented test to verify it fails**

Connect an Android device or start an emulator, then:

```bash
./gradlew connectedAndroidTest --tests "com.jupyterdroid.KernelManagerTest"
```

Expected: FAIL — `KernelManager` not found.

- [ ] **Step 4: Create KernelManager.kt**

Create `app/src/main/kotlin/com/jupyterdroid/kernel/KernelManager.kt`:

```kotlin
package com.jupyterdroid.kernel

import android.content.Context
import com.chaquo.python.Python

data class ExecutionResult(val output: String, val error: String, val executionCount: Int)
data class PipResult(val stdout: String, val stderr: String, val success: Boolean)

class KernelManager private constructor() {
    private val runner = Python.getInstance().getModule("kernel_runner")

    fun execute(source: String): ExecutionResult {
        val result = runner.callAttr("execute", source)
        return ExecutionResult(
            output = result["output"].toString(),
            error = result["error"].toString(),
            executionCount = result["execution_count"].toInt()
        )
    }

    fun pipInstall(packageName: String): PipResult {
        val result = runner.callAttr("pip_install", packageName)
        return PipResult(
            stdout = result["stdout"].toString(),
            stderr = result["stderr"].toString(),
            success = result["returncode"].toInt() == 0
        )
    }

    fun reset() {
        runner.callAttr("reset")
    }

    companion object {
        @Volatile private var instance: KernelManager? = null

        fun getInstance(context: Context): KernelManager =
            instance ?: synchronized(this) {
                instance ?: KernelManager().also { instance = it }
            }
    }
}
```

- [ ] **Step 5: Run instrumented tests to verify they pass**

```bash
./gradlew connectedAndroidTest --tests "com.jupyterdroid.KernelManagerTest"
```

Expected: 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/python/ app/src/main/kotlin/com/jupyterdroid/kernel/ app/src/androidTest/
git commit -m "feat: kernel — exec-based Python runner with KernelManager"
```

---

### Task 5: Code Cell UI + NotebookActivity

**Files:**
- Create: `app/src/main/res/layout/item_cell_code.xml`
- Create: `app/src/main/res/layout/activity_notebook.xml`
- Create: `app/src/main/res/menu/menu_notebook.xml`
- Create: `app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt`
- Create: `app/src/main/kotlin/com/jupyterdroid/ui/NotebookAdapter.kt`
- Create: `app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt`

**Interfaces:**
- Consumes: `Cell`, `ExecutionResult`, `KernelManager.execute()`, `NotebookFile.read()`, `NotebookFile.write()` (Tasks 2–4)
- Produces:
  - `NotebookAdapter(cells: MutableList<Cell>, onRunCell: (Int) -> Unit)` with `.addCodeCell()`, `.addMarkdownCell()`, `.updateCellOutput(position, result)`
  - `NotebookActivity` launched with optional `Intent.putExtra(EXTRA_FILE_PATH, path)`

- [ ] **Step 1: Create item_cell_code.xml**

Create `app/src/main/res/layout/item_cell_code.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="4dp">

    <EditText
        android:id="@+id/sourceEdit"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/code_background"
        android:fontFamily="monospace"
        android:gravity="top|start"
        android:inputType="textMultiLine|textNoSuggestions"
        android:minLines="2"
        android:padding="8dp"
        android:textSize="13sp" />

    <TextView
        android:id="@+id/outputText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fontFamily="monospace"
        android:padding="8dp"
        android:textSize="12sp"
        android:visibility="gone" />
</LinearLayout>
```

- [ ] **Step 2: Create activity_notebook.xml**

Create `app/src/main/res/layout/activity_notebook.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/cellsRecyclerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingBottom="80dp" />

    <com.google.android.material.bottomappbar.BottomAppBar
        android:id="@+id/bottomAppBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        app:menu="@menu/menu_notebook" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 3: Create menu_notebook.xml**

Create `app/src/main/res/menu/menu_notebook.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/action_run_cell"  android:title="Run" />
    <item android:id="@+id/action_run_all"   android:title="Run All" />
    <item android:id="@+id/action_add_code"  android:title="+ Code" />
    <item android:id="@+id/action_add_md"    android:title="+ MD" />
    <item android:id="@+id/action_pip"       android:title="pip" />
    <item android:id="@+id/action_save"      android:title="Save" />
</menu>
```

- [ ] **Step 4: Create CodeCellViewHolder.kt**

Create `app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt`:

```kotlin
package com.jupyterdroid.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.R
import com.jupyterdroid.model.Cell

class CodeCellViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val sourceEdit: EditText = view.findViewById(R.id.sourceEdit)
    val outputText: TextView = view.findViewById(R.id.outputText)
    private var watcher: TextWatcher? = null

    fun bind(cell: Cell.Code, position: Int, onSourceChanged: (Int, String) -> Unit) {
        watcher?.let { sourceEdit.removeTextChangedListener(it) }
        sourceEdit.setText(cell.source)

        watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) = onSourceChanged(position, s.toString())
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        }
        sourceEdit.addTextChangedListener(watcher)

        when {
            cell.error.isNotEmpty() -> {
                outputText.visibility = View.VISIBLE
                outputText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.error_red)
                )
                outputText.text = cell.error
            }
            cell.output.isNotEmpty() -> {
                outputText.visibility = View.VISIBLE
                outputText.setTextColor(
                    ContextCompat.getColor(itemView.context, android.R.color.black)
                )
                outputText.text = cell.output
            }
            else -> outputText.visibility = View.GONE
        }
    }

    companion object {
        fun create(parent: ViewGroup): CodeCellViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cell_code, parent, false)
            return CodeCellViewHolder(view)
        }
    }
}
```

- [ ] **Step 5: Create NotebookAdapter.kt (code cells only — Task 6 adds markdown)**

Create `app/src/main/kotlin/com/jupyterdroid/ui/NotebookAdapter.kt`:

```kotlin
package com.jupyterdroid.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.kernel.ExecutionResult
import com.jupyterdroid.model.Cell

class NotebookAdapter(
    val cells: MutableList<Cell>,
    private val onRunCell: (Int) -> Unit
) : RecyclerView.Adapter<CodeCellViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CodeCellViewHolder.create(parent)

    override fun onBindViewHolder(holder: CodeCellViewHolder, position: Int) {
        holder.bind(cells[position] as Cell.Code, position, ::updateSource)
    }

    override fun getItemCount() = cells.size

    fun addCodeCell() {
        cells.add(Cell.Code())
        notifyItemInserted(cells.size - 1)
    }

    fun addMarkdownCell() {
        cells.add(Cell.Markdown())
        notifyItemInserted(cells.size - 1)
    }

    fun updateCellOutput(position: Int, result: ExecutionResult) {
        val cell = cells[position] as? Cell.Code ?: return
        cells[position] = cell.copy(
            output = result.output,
            error = result.error,
            executionCount = result.executionCount
        )
        notifyItemChanged(position)
    }

    private fun updateSource(position: Int, source: String) {
        when (val cell = cells[position]) {
            is Cell.Code -> cells[position] = cell.copy(source = source)
            is Cell.Markdown -> cells[position] = cell.copy(source = source)
        }
    }
}
```

- [ ] **Step 6: Create NotebookActivity.kt**

Create `app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt`:

```kotlin
package com.jupyterdroid

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.snackbar.Snackbar
import com.jupyterdroid.kernel.KernelManager
import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.NotebookJson
import com.jupyterdroid.ui.NotebookAdapter
import com.jupyterdroid.ui.PipInstallBottomSheet
import com.jupyterdroid.util.NotebookFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NotebookActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
    }

    private lateinit var adapter: NotebookAdapter
    private lateinit var km: KernelManager
    private var notebookJson: NotebookJson = NotebookJson()
    private var currentFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notebook)

        km = KernelManager.getInstance(this)

        val cells: MutableList<Cell> = intent.getStringExtra(EXTRA_FILE_PATH)?.let { path ->
            currentFile = File(path)
            try {
                val (json, loaded) = NotebookFile.read(currentFile!!)
                notebookJson = json
                loaded.toMutableList()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to open: ${e.message}", Toast.LENGTH_LONG).show()
                mutableListOf()
            }
        } ?: mutableListOf()

        adapter = NotebookAdapter(cells) { position -> runCell(position) }

        val recycler = findViewById<RecyclerView>(R.id.cellsRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val bar = findViewById<BottomAppBar>(R.id.bottomAppBar)
        bar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_run_cell -> {
                    val pos = (recycler.layoutManager as LinearLayoutManager)
                        .findLastVisibleItemPosition()
                    if (pos >= 0) runCell(pos)
                    true
                }
                R.id.action_run_all -> { runAllCells(); true }
                R.id.action_add_code -> { adapter.addCodeCell(); true }
                R.id.action_add_md -> { adapter.addMarkdownCell(); true }
                R.id.action_pip -> {
                    PipInstallBottomSheet().show(supportFragmentManager, PipInstallBottomSheet.TAG)
                    true
                }
                R.id.action_save -> { save(); true }
                else -> false
            }
        }

        title = currentFile?.name ?: "Untitled.ipynb"
    }

    private fun runCell(position: Int) {
        val cell = adapter.cells.getOrNull(position) as? Cell.Code ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    km.execute(cell.source)
                } catch (e: Exception) {
                    km.reset()
                    withContext(Dispatchers.Main) {
                        Snackbar.make(
                            findViewById(R.id.cellsRecyclerView),
                            "Kernel restarted",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    null
                }
            }
            result?.let { adapter.updateCellOutput(position, it) }
        }
    }

    private fun runAllCells() {
        lifecycleScope.launch {
            adapter.cells.indices.forEach { i ->
                if (adapter.cells[i] is Cell.Code) {
                    val cell = adapter.cells[i] as Cell.Code
                    val result = withContext(Dispatchers.IO) {
                        try { km.execute(cell.source) } catch (e: Exception) { null }
                    }
                    result?.let { adapter.updateCellOutput(i, it) }
                }
            }
        }
    }

    private fun save() {
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

    override fun onPause() {
        super.onPause()
        save()
    }
}
```

- [ ] **Step 7: Build and smoke test (code cells)**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

Temporarily set `NotebookActivity` as MAIN launcher in AndroidManifest, install on device, add a code cell, run `print("hello")` — verify output appears below the cell.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt \
        app/src/main/kotlin/com/jupyterdroid/ui/NotebookAdapter.kt \
        app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt \
        app/src/main/res/
git commit -m "feat: code cell UI with run, run-all, and save"
```

---

### Task 6: Markdown Cell UI

**Files:**
- Create: `app/src/main/res/layout/item_cell_markdown.xml`
- Create: `app/src/main/kotlin/com/jupyterdroid/ui/MarkdownCellViewHolder.kt`
- Modify: `app/src/main/kotlin/com/jupyterdroid/ui/NotebookAdapter.kt`

**Interfaces:**
- Consumes: `Cell.Markdown`, Markwon library (Task 1)
- Produces: `MarkdownCellViewHolder.bind(cell: Cell.Markdown, position: Int, onSourceChanged: (Int, String) -> Unit)` — tap rendered text to edit, focus-lost to render

- [ ] **Step 1: Create item_cell_markdown.xml**

Create `app/src/main/res/layout/item_cell_markdown.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="8dp">

    <EditText
        android:id="@+id/sourceEdit"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="top|start"
        android:inputType="textMultiLine"
        android:minLines="2"
        android:visibility="gone" />

    <TextView
        android:id="@+id/renderedText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="48dp"
        android:padding="4dp" />
</FrameLayout>
```

- [ ] **Step 2: Create MarkdownCellViewHolder.kt**

Create `app/src/main/kotlin/com/jupyterdroid/ui/MarkdownCellViewHolder.kt`:

```kotlin
package com.jupyterdroid.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.R
import com.jupyterdroid.model.Cell
import io.noties.markwon.Markwon

class MarkdownCellViewHolder(view: View, private val markwon: Markwon) : RecyclerView.ViewHolder(view) {
    private val sourceEdit: EditText = view.findViewById(R.id.sourceEdit)
    private val renderedText: TextView = view.findViewById(R.id.renderedText)

    fun bind(cell: Cell.Markdown, position: Int, onSourceChanged: (Int, String) -> Unit) {
        sourceEdit.setText(cell.source)
        render(cell.source)

        renderedText.setOnClickListener {
            renderedText.visibility = View.GONE
            sourceEdit.visibility = View.VISIBLE
            sourceEdit.requestFocus()
        }

        sourceEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val src = sourceEdit.text.toString()
                onSourceChanged(position, src)
                render(src)
            }
        }

        sourceEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) = onSourceChanged(position, s.toString())
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }

    private fun render(source: String) {
        sourceEdit.visibility = View.GONE
        renderedText.visibility = View.VISIBLE
        if (source.isEmpty()) {
            renderedText.text = "Tap to edit markdown"
        } else {
            markwon.setMarkdown(renderedText, source)
        }
    }

    companion object {
        fun create(parent: ViewGroup): MarkdownCellViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cell_markdown, parent, false)
            return MarkdownCellViewHolder(view, Markwon.create(parent.context))
        }
    }
}
```

- [ ] **Step 3: Update NotebookAdapter.kt to support both cell types**

Replace the entire `NotebookAdapter.kt` content:

```kotlin
package com.jupyterdroid.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.kernel.ExecutionResult
import com.jupyterdroid.model.Cell

class NotebookAdapter(
    val cells: MutableList<Cell>,
    private val onRunCell: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CODE = 0
        private const val TYPE_MARKDOWN = 1
    }

    override fun getItemViewType(position: Int) = when (cells[position]) {
        is Cell.Code -> TYPE_CODE
        is Cell.Markdown -> TYPE_MARKDOWN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            TYPE_CODE -> CodeCellViewHolder.create(parent)
            else -> MarkdownCellViewHolder.create(parent)
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CodeCellViewHolder -> holder.bind(
                cells[position] as Cell.Code, position, ::updateSource
            )
            is MarkdownCellViewHolder -> holder.bind(
                cells[position] as Cell.Markdown, position, ::updateSource
            )
        }
    }

    override fun getItemCount() = cells.size

    fun addCodeCell() {
        cells.add(Cell.Code())
        notifyItemInserted(cells.size - 1)
    }

    fun addMarkdownCell() {
        cells.add(Cell.Markdown())
        notifyItemInserted(cells.size - 1)
    }

    fun updateCellOutput(position: Int, result: ExecutionResult) {
        val cell = cells[position] as? Cell.Code ?: return
        cells[position] = cell.copy(
            output = result.output,
            error = result.error,
            executionCount = result.executionCount
        )
        notifyItemChanged(position)
    }

    private fun updateSource(position: Int, source: String) {
        when (val cell = cells[position]) {
            is Cell.Code -> cells[position] = cell.copy(source = source)
            is Cell.Markdown -> cells[position] = cell.copy(source = source)
        }
    }
}
```

- [ ] **Step 4: Build and verify**

```bash
./gradlew assembleDebug
```

Install on device, tap `+ MD`, type `# Hello **world**`, tap away — verify heading with bold renders. Tap rendered text — verify edit mode returns.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/ui/MarkdownCellViewHolder.kt \
        app/src/main/kotlin/com/jupyterdroid/ui/NotebookAdapter.kt \
        app/src/main/res/layout/item_cell_markdown.xml
git commit -m "feat: markdown cell UI with Markwon rendering"
```

---

### Task 7: pip Install UI

**Files:**
- Create: `app/src/main/res/layout/bottom_sheet_pip.xml`
- Create: `app/src/main/kotlin/com/jupyterdroid/ui/PipInstallBottomSheet.kt`

**Interfaces:**
- Consumes: `KernelManager.pipInstall(packageName: String): PipResult` (Task 4)
- Produces: `PipInstallBottomSheet` — `BottomSheetDialogFragment` shown via `PipInstallBottomSheet().show(supportFragmentManager, PipInstallBottomSheet.TAG)` (already wired in `NotebookActivity` from Task 5)

- [ ] **Step 1: Create bottom_sheet_pip.xml**

Create `app/src/main/res/layout/bottom_sheet_pip.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="pip install"
        android:textSize="18sp"
        android:textStyle="bold"
        android:layout_marginBottom="12dp" />

    <EditText
        android:id="@+id/packageNameEdit"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="package name (e.g. requests)"
        android:inputType="text"
        android:singleLine="true" />

    <Button
        android:id="@+id/installButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Install" />

    <TextView
        android:id="@+id/pipOutputText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fontFamily="monospace"
        android:layout_marginTop="8dp"
        android:textSize="12sp" />
</LinearLayout>
```

- [ ] **Step 2: Create PipInstallBottomSheet.kt**

Create `app/src/main/kotlin/com/jupyterdroid/ui/PipInstallBottomSheet.kt`:

```kotlin
package com.jupyterdroid.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jupyterdroid.R
import com.jupyterdroid.kernel.KernelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PipInstallBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_pip, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val packageEdit = view.findViewById<EditText>(R.id.packageNameEdit)
        val outputText = view.findViewById<TextView>(R.id.pipOutputText)
        val installButton = view.findViewById<Button>(R.id.installButton)
        val km = KernelManager.getInstance(requireContext())

        installButton.setOnClickListener {
            val pkg = packageEdit.text.toString().trim()
            if (pkg.isEmpty()) return@setOnClickListener

            installButton.isEnabled = false
            outputText.text = "Installing $pkg…"

            CoroutineScope(Dispatchers.Main).launch {
                val result = withContext(Dispatchers.IO) { km.pipInstall(pkg) }
                outputText.text = when {
                    result.success -> result.stdout.ifEmpty { "Installed $pkg" }
                    else -> result.stderr.ifEmpty { "Failed to install $pkg" }
                }
                installButton.isEnabled = true
            }
        }
    }

    companion object {
        const val TAG = "PipInstallBottomSheet"
    }
}
```

- [ ] **Step 3: Build and verify**

```bash
./gradlew assembleDebug
```

Install on device, tap `pip`, type `requests`, tap Install — verify success message. Then in a code cell run `import requests; print(requests.__version__)` — verify version prints.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/ui/PipInstallBottomSheet.kt \
        app/src/main/res/layout/bottom_sheet_pip.xml
git commit -m "feat: pip install bottom sheet"
```

---

### Task 8: MainActivity

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/layout/item_notebook.xml`
- Create: `app/src/main/kotlin/com/jupyterdroid/MainActivity.kt`

**Interfaces:**
- Consumes: `NotebookActivity.EXTRA_FILE_PATH` (Task 5)
- Produces: launch screen with "New Notebook", "Open file" picker, and recent notebooks list (SharedPreferences-backed)

- [ ] **Step 1: Create activity_main.xml**

Create `app/src/main/res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <Button
        android:id="@+id/newNotebookButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:text="New Notebook" />

    <Button
        android:id="@+id/openNotebookButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="16dp"
        android:text="Open .ipynb file" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:text="Recent"
        android:textStyle="bold" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recentFilesRecyclerView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
```

- [ ] **Step 2: Create item_notebook.xml**

Create `app/src/main/res/layout/item_notebook.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/fileNameText"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="16dp"
    android:textSize="16sp" />
```

- [ ] **Step 3: Create MainActivity.kt**

Create `app/src/main/kotlin/com/jupyterdroid/MainActivity.kt`:

```kotlin
package com.jupyterdroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private val prefsName = "jupyterdroid"
    private val recentKey = "recent_notebooks"
    private lateinit var recentAdapter: RecentAdapter

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { openFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recentAdapter = RecentAdapter(loadRecent()) { path -> openNotebook(path) }

        val recycler = findViewById<RecyclerView>(R.id.recentFilesRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = recentAdapter

        findViewById<View>(R.id.newNotebookButton).setOnClickListener {
            startActivity(Intent(this, NotebookActivity::class.java))
        }

        findViewById<View>(R.id.openNotebookButton).setOnClickListener {
            openFileLauncher.launch(arrayOf("*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        recentAdapter.update(loadRecent())
    }

    private fun openFromUri(uri: Uri) {
        try {
            val file = File(cacheDir, "opened_${System.currentTimeMillis()}.ipynb")
            contentResolver.openInputStream(uri)!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            openNotebook(file.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openNotebook(path: String) {
        saveRecent(path)
        startActivity(
            Intent(this, NotebookActivity::class.java)
                .putExtra(NotebookActivity.EXTRA_FILE_PATH, path)
        )
    }

    private fun loadRecent(): List<String> =
        getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getStringSet(recentKey, emptySet())
            ?.sortedByDescending { File(it).lastModified() }
            ?: emptyList()

    private fun saveRecent(path: String) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(recentKey, mutableSetOf())!!.toMutableSet()
        set.add(path)
        prefs.edit().putStringSet(recentKey, set).apply()
    }

    inner class RecentAdapter(
        private var paths: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecentAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.fileNameText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notebook, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val path = paths[position]
            holder.text.text = path.substringAfterLast("/")
            holder.itemView.setOnClickListener { onClick(path) }
        }

        override fun getItemCount() = paths.size

        fun update(newPaths: List<String>) {
            paths = newPaths
            notifyDataSetChanged()
        }
    }
}
```

- [ ] **Step 4: Full smoke test**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the complete manual smoke test:
1. App opens to MainActivity
2. Tap "New Notebook" → empty notebook opens
3. Tap `+ Code` → code cell appears → type `print("hello from JupyterDroid")` → tap Run → `hello from JupyterDroid` appears below cell
4. Tap `+ MD` → markdown cell → type `# Hello **world**` → tap away → renders as styled heading
5. Tap `pip` → bottom sheet → install `requests` → success message
6. Add code cell → type `import requests; print(requests.__version__)` → Run → version string appears
7. Tap `Save` → toast "Saved"
8. Press back → MainActivity shows notebook in recent list
9. Tap it → notebook reopens with all cells and outputs intact

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/MainActivity.kt \
        app/src/main/res/layout/activity_main.xml \
        app/src/main/res/layout/item_notebook.xml
git commit -m "feat: MainActivity with new notebook, file open, and recent list"
```

---

## Spec Coverage

| Requirement | Task |
|---|---|
| Create `.ipynb` files | Task 3 (write), Task 5 (save on first run), Task 8 (New Notebook) |
| Open `.ipynb` files | Task 3 (read), Task 8 (file picker + recent list) |
| Standard nbformat 4 | Task 2 (NotebookJson schema), Task 3 (round-trip test) |
| Code cells with inline output | Task 5 (CodeCellViewHolder, runCell) |
| Markdown cells with rendering | Task 6 (MarkdownCellViewHolder + Markwon) |
| pip install on-device | Task 4 (kernel_runner.pip_install), Task 7 (PipInstallBottomSheet) |
| Auto-save on background | Task 5 (onPause → save()) |
| Error shown inline in red | Task 5 (CodeCellViewHolder error branch) |
| Kernel crash → snackbar + restart | Task 5 (runCell catch block → km.reset()) |
| pip failure → stderr shown | Task 7 (PipInstallBottomSheet error branch) |
| File I/O error → toast | Task 5 (NotebookActivity catch blocks) |
