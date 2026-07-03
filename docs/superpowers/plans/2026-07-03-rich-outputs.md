# Rich Outputs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Matplotlib figures render as images in the cell and persist as nbformat `display_data`; cells echo their last expression Jupyter-style.

**Architecture:** `kernel_runner.py` gains an ast-based last-expression echo and a post-exec figure sweep (Agg backend, PNG → base64), returning `"images"` in the result dict. Kotlin carries `images: List<String>` through `ExecutionResult` → `Cell.Code` → an `ImageView` container in the cell layout. nbformat outputs become raw `JsonObject`s so heterogeneous output types (stream + display_data) read/write cleanly.

**Tech Stack:** Python stdlib (ast, base64), matplotlib (device-only, user-installed), Kotlin, kotlinx.serialization JsonObject.

**Spec:** `docs/superpowers/specs/2026-07-03-rich-outputs-design.md`

## Global Constraints

- No new build dependencies (matplotlib is user-installed at runtime via the existing pip UI, never bundled).
- All gradle commands need `export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home` first (no system Java).
- Branch: `rich-outputs` (already created and checked out).
- `kernel_runner.py` stays importable and testable with host `python3` (3.9.6, **no matplotlib installed on host** — the sweep's no-matplotlib fast path is what host tests cover).
- The interrupt guards in `execute()` (outer `except BaseException`, finally drain) must keep covering the new echo/sweep code paths — do not restructure them.

---

### Task 1: Kernel echo + figure sweep

**Files:**
- Modify: `app/src/main/python/kernel_runner.py`
- Test: `app/src/test/python/test_kernel_runner.py` (extend — run with host `python3`, not gradle)

**Interfaces:**
- Consumes: existing `execute()`/`_globals` and its interrupt guards.
- Produces: `execute()` result dict gains `"images": [<base64 png str>, ...]` (always present, `[]` when no figures). Echo text is appended to `"output"` after stdout. Task 2 reads `"images"` through Chaquopy.

- [x] **Step 1: Write the failing tests**

Append to `app/src/test/python/test_kernel_runner.py` (before the final `print("ALL PASS")` line; keep everything already there):

```python
# 5. Expression echo: last bare expression is repr()'d like Jupyter.
r = kernel_runner.execute("1 + 1")
assert r["output"] == "2\n", r["output"]
assert r["error"] == ""
assert r["images"] == []

r = kernel_runner.execute("z = 5")          # assignment: no echo
assert r["output"] == "", r["output"]

r = kernel_runner.execute("None")           # None: no echo
assert r["output"] == "", r["output"]

r = kernel_runner.execute("print('a')\n3")  # stdout first, then echo
assert r["output"] == "a\n3\n", r["output"]

r = kernel_runner.execute("'café'")    # unicode repr survives
assert r["output"] == "'café'\n", r["output"]

r = kernel_runner.execute("def f(:")        # syntax error unchanged
assert "SyntaxError" in r["error"], r["error"]
assert r["images"] == []

r = kernel_runner.execute("z + 1")          # echo uses kernel globals
assert r["output"] == "6\n", r["output"]

# 6. Figure sweep fast path: host has no matplotlib.
import sys as _sys
assert "matplotlib" not in _sys.modules
r = kernel_runner.execute("40 + 2")
assert r["images"] == []

# 7. Agg backend forced before any user code can import matplotlib.
import os as _os
assert _os.environ.get("MPLBACKEND") == "agg"

print("ALL PASS")
```

Delete the old final `print("ALL PASS")` so it appears exactly once, at the end.

- [x] **Step 2: Run tests to verify they fail**

Run: `python3 app/src/test/python/test_kernel_runner.py`
Expected: FAIL — `KeyError: 'images'` (or the `"2\n"` echo assertion, whichever hits first).

- [x] **Step 3: Implement echo + sweep**

In `app/src/main/python/kernel_runner.py`:

Add to the imports at the top:

```python
import ast
import base64
```

Immediately after the imports (before the module globals), force the headless backend:

```python
# Must be set before user code first imports matplotlib.
os.environ.setdefault("MPLBACKEND", "agg")
```

Add these two helpers above `execute`:

```python
def _exec_with_echo(source):
    """Run source in _globals; if the last top-level statement is a bare
    expression, evaluate it and return its repr (Jupyter echo). Returns ""
    when there is nothing to echo."""
    tree = ast.parse(source, "<cell>")
    if tree.body and isinstance(tree.body[-1], ast.Expr):
        body = ast.Module(body=tree.body[:-1], type_ignores=[])
        exec(compile(body, "<cell>", "exec"), _globals)
        value = eval(compile(ast.Expression(tree.body[-1].value), "<cell>", "eval"), _globals)
        if value is not None:
            return repr(value) + "\n"
        return ""
    exec(compile(source, "<cell>", "exec"), _globals)
    return ""


def _capture_figures():
    """Sweep all open matplotlib figures into base64 PNGs and close them.
    No-op (and no import) when matplotlib was never loaded."""
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

Replace the body of `execute` between `sys.stdout, sys.stderr = stdout_buf, stderr_buf` and the first `return` with (the outer `except BaseException` and the `finally` stay exactly as they are):

```python
        error = ""
        echo = ""
        images = []
        try:
            echo = _exec_with_echo(source)
        except BaseException:  # BaseException so KeyboardInterrupt lands here, not in the bridge
            error = traceback.format_exc()
        # Sweep even after an error so leftover figures never leak into the
        # next cell's result. A sweep failure becomes the cell's error.
        try:
            images = _capture_figures()
        except BaseException:
            images = []
            if not error:
                error = traceback.format_exc()
        return {
            "output": stdout_buf.getvalue() + echo,
            "error": error,
            "execution_count": _execution_count,
            "images": images,
        }
```

In the outer `except BaseException:` return dict (the stale-interrupt path), add `"images": [],` so the key is always present:

```python
        return {
            "output": stdout_buf.getvalue(),
            "error": traceback.format_exc(),
            "execution_count": _execution_count,
            "images": [],
        }
```

- [x] **Step 4: Run tests to verify they pass**

Run: `python3 app/src/test/python/test_kernel_runner.py`
Expected: `ALL PASS`

- [x] **Step 5: Commit**

```bash
git add app/src/main/python/kernel_runner.py app/src/test/python/test_kernel_runner.py
git commit -m "kernel_runner: last-expression echo and matplotlib figure sweep"
```

---

### Task 2: Kotlin model, bridge, and image display

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/model/Cell.kt`
- Modify: `app/src/main/kotlin/com/jupyterdroid/kernel/KernelManager.kt`
- Modify: `app/src/main/kotlin/com/jupyterdroid/ui/NotebookAdapter.kt` (updateCellOutput)
- Modify: `app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt`
- Modify: `app/src/main/res/layout/item_cell_code.xml`

**Interfaces:**
- Consumes: `execute()` result dict key `"images"` (list of base64 PNG strings) from Task 1.
- Produces: `Cell.Code.images: List<String>` (default `emptyList()`) and `ExecutionResult.images: List<String>` — Task 3's persistence reads/writes `Cell.Code.images`.

- [x] **Step 1: Extend the model**

In `Cell.kt`, add `images` to `Cell.Code`:

```kotlin
    data class Code(
        val source: String = "",
        val output: String = "",
        val error: String = "",
        val executionCount: Int? = null,
        val images: List<String> = emptyList()
    ) : Cell()
```

In `KernelManager.kt`, extend `ExecutionResult` and `execute()`:

```kotlin
data class ExecutionResult(
    val output: String,
    val error: String,
    val executionCount: Int,
    val images: List<String> = emptyList()
)
```

```kotlin
    fun execute(source: String): ExecutionResult {
        val result = runner.callAttr("execute", source)
        return ExecutionResult(
            output = result.callAttr("__getitem__", "output").toString(),
            error = result.callAttr("__getitem__", "error").toString(),
            executionCount = result.callAttr("__getitem__", "execution_count").toInt(),
            images = result.callAttr("__getitem__", "images").asList().map { it.toString() }
        )
    }
```

In `NotebookAdapter.kt`, `updateCellOutput`'s copy gains the images:

```kotlin
        cells[position] = cell.copy(
            output = result.output,
            error = result.error,
            executionCount = result.executionCount,
            images = result.images
        )
```

- [x] **Step 2: Add the image container to the layout**

In `item_cell_code.xml`, insert between the `outputText` TextView and the `copyErrorButton` Button:

```xml
    <LinearLayout
        android:id="@+id/imagesContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:visibility="gone" />
```

- [x] **Step 3: Bind images in CodeCellViewHolder**

In `CodeCellViewHolder.kt`, add imports:

```kotlin
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
```

Add the view reference next to the others:

```kotlin
    val imagesContainer: LinearLayout = view.findViewById(R.id.imagesContainer)
```

In `bind`, after the existing output/error `when` block, add:

```kotlin
        imagesContainer.removeAllViews()
        imagesContainer.visibility = if (cell.images.isEmpty()) View.GONE else View.VISIBLE
        for (b64 in cell.images) {
            val bitmap = try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: IllegalArgumentException) {
                null
            }
            if (bitmap == null) {
                Log.w("CodeCellViewHolder", "Undecodable image output skipped")
                continue
            }
            imagesContainer.addView(
                ImageView(itemView.context).apply {
                    adjustViewBounds = true
                    setImageBitmap(bitmap)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
```

- [x] **Step 4: Build and run unit tests**

```bash
export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home
./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/model/Cell.kt app/src/main/kotlin/com/jupyterdroid/kernel/KernelManager.kt app/src/main/kotlin/com/jupyterdroid/ui/NotebookAdapter.kt app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt app/src/main/res/layout/item_cell_code.xml
git commit -m "Carry image outputs through model and render them in code cells"
```

---

### Task 3: nbformat persistence of display_data

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/model/NotebookJson.kt` (outputs become `List<JsonObject>`; delete `CellOutputJson`)
- Modify: `app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt`
- Test: `app/src/test/kotlin/com/jupyterdroid/NotebookFileTest.kt` (extend)

**Interfaces:**
- Consumes: `Cell.Code.images: List<String>` from Task 2.
- Produces: `.ipynb` round-trip — write `{"output_type":"display_data","data":{"image/png":"<b64>\n"},"metadata":{}}` per image; read `image/png` from `display_data` AND `execute_result` outputs (string or list-of-lines form) into `Cell.Code.images`. `NotebookCellJson.outputs: List<JsonObject>`.

- [x] **Step 1: Write the failing tests**

Add to `NotebookFileTest.kt` (note: `NotebookFile.read(text: String)` and `NotebookFile.serialize(...)` already exist):

```kotlin
    @Test
    fun `image outputs round-trip as display_data`() {
        val cell = Cell.Code(source = "plot", images = listOf("QUJD"))
        val serialized = NotebookFile.serialize(NotebookJson(), listOf(cell))
        assertTrue(serialized.contains("display_data"))
        assertTrue(serialized.contains("image/png"))

        val (_, cells) = NotebookFile.read(serialized)
        assertEquals(listOf("QUJD"), (cells[0] as Cell.Code).images)
    }

    @Test
    fun `desktop jupyter list-form image png parses`() {
        val nb = """
            {"nbformat":4,"nbformat_minor":5,"metadata":{},"cells":[
              {"cell_type":"code","source":["x"],"metadata":{},"execution_count":1,
               "outputs":[{"output_type":"execute_result","execution_count":1,
                           "data":{"image/png":["QUJD\n","REVG"]},"metadata":{}}]}
            ]}
        """.trimIndent()
        val (_, cells) = NotebookFile.read(nb)
        assertEquals(listOf("QUJDREVG"), (cells[0] as Cell.Code).images)
    }

    @Test
    fun `stream and image outputs coexist`() {
        val cell = Cell.Code(source = "s", output = "hi\n", images = listOf("QUJD"))
        val (_, cells) = NotebookFile.read(NotebookFile.serialize(NotebookJson(), listOf(cell)))
        val roundTripped = cells[0] as Cell.Code
        assertEquals("hi\n", roundTripped.output)
        assertEquals(listOf("QUJD"), roundTripped.images)
    }
```

- [x] **Step 2: Run tests to verify they fail**

```bash
export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home
./gradlew testDebugUnitTest 2>&1 | tail -15
```

Expected: FAIL to compile (`images` unresolved in test context is already resolved by Task 2, so the failures are the new assertions / missing serializer behavior — e.g. `serialized.contains("display_data")` assertion fails).

- [x] **Step 3: Switch outputs to JsonObject and implement read/write**

In `NotebookJson.kt`: change the `outputs` type and delete the now-unused `CellOutputJson` class entirely.

```kotlin
@Serializable
data class NotebookCellJson(
    @SerialName("cell_type") val cellType: String,
    val source: List<String> = emptyList(),
    val metadata: JsonObject = JsonObject(emptyMap()),
    val outputs: List<JsonObject> = emptyList(),
    @SerialName("execution_count") val executionCount: Int? = null
)
```

In `NotebookFile.kt`: replace the `CellOutputJson` import with:

```kotlin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
```

Replace `toCell()`:

```kotlin
    private fun NotebookCellJson.toCell(): Cell = when (cellType) {
        "code" -> Cell.Code(
            source = source.joinToString(""),
            output = outputs.filter { it.outputType == "stream" && it.streamName == "stdout" }
                .flatMap { it["text"].asLines() }.joinToString(""),
            error = outputs.filter { it.outputType == "stream" && it.streamName == "stderr" }
                .flatMap { it["text"].asLines() }.joinToString(""),
            executionCount = executionCount,
            images = outputs
                .filter { it.outputType == "display_data" || it.outputType == "execute_result" }
                .mapNotNull { out ->
                    out["data"]?.jsonObject?.get("image/png")?.asLines()
                        ?.joinToString("")?.replace("\n", "")?.takeIf { it.isNotEmpty() }
                }
        )
        else -> Cell.Markdown(source = source.joinToString(""))
    }

    private val JsonObject.outputType: String?
        get() = this["output_type"]?.jsonPrimitive?.content
    private val JsonObject.streamName: String?
        get() = this["name"]?.jsonPrimitive?.content

    // nbformat allows "text"/"image/png" as either a string or a list of lines.
    private fun JsonElement?.asLines(): List<String> = when (this) {
        is JsonArray -> map { it.jsonPrimitive.content }
        is JsonPrimitive -> listOf(content)
        else -> emptyList()
    }
```

Replace the `Cell.Code` branch of `toCellJson()` and add the two builders:

```kotlin
        is Cell.Code -> NotebookCellJson(
            cellType = "code",
            source = source.toNotebookLines(),
            outputs = buildList {
                if (output.isNotEmpty()) add(streamOutput("stdout", output))
                if (error.isNotEmpty()) add(streamOutput("stderr", error))
                images.forEach { add(imageOutput(it)) }
            },
            executionCount = executionCount
        )
```

```kotlin
    private fun streamOutput(name: String, text: String) = JsonObject(
        mapOf(
            "output_type" to JsonPrimitive("stream"),
            "name" to JsonPrimitive(name),
            "text" to JsonArray(text.toNotebookLines().map { JsonPrimitive(it) })
        )
    )

    private fun imageOutput(b64: String) = JsonObject(
        mapOf(
            "output_type" to JsonPrimitive("display_data"),
            "data" to JsonObject(mapOf("image/png" to JsonPrimitive(b64 + "\n"))),
            "metadata" to JsonObject(emptyMap())
        )
    )
```

Then search for any remaining `CellOutputJson` references and remove them:

```bash
grep -rn "CellOutputJson" app/src
```

Expected after the change: no hits. (If a test file references it, rewrite that assertion against the JSON string instead.)

- [x] **Step 4: Run all unit tests**

```bash
export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home
./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL` — new tests pass AND the pre-existing fixture round-trip tests still pass (stream read/write behavior must be unchanged).

- [x] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/model/NotebookJson.kt app/src/main/kotlin/com/jupyterdroid/util/NotebookFile.kt app/src/test/kotlin/com/jupyterdroid/NotebookFileTest.kt
git commit -m "Persist image outputs as nbformat display_data"
```

---

### Task 4: Manual verification (emulator)

**Files:** none — `./gradlew installDebug` (emulator `JupyterDroid_test`; adb at `/Users/bandeiracaio/android-sdk/platform-tools/adb`).

- [x] **Step 1: Echo**

New notebook. Cell `1 + 1` → Run shows `2`. Cell `x = 5` → Run shows nothing. Cell `print("a")` then bare `3` on next line → shows `a` then `3`.

- [x] **Step 2: Install matplotlib**

pip menu → install `matplotlib` (large download; wait for success toast/output).

- [x] **Step 3: Plot renders**

Cell:
```python
import matplotlib.pyplot as plt
plt.plot([1, 2, 3], [1, 4, 9])
plt.title("hi")
```
Run → a line plot image appears under the cell. No `plt.show()` needed.

- [x] **Step 4: Two figures**

Cell:
```python
plt.figure(); plt.plot([1, 2])
plt.figure(); plt.plot([2, 1])
```
Run → two images, first ascending then descending.

- [x] **Step 5: Persistence**

Save. Reopen the notebook → plot images still display. (If a desktop is handy: the file opens in JupyterLab/VS Code with the images visible — optional check.)

- [x] **Step 6: Error path**

Cell `plt.plot([1,2]); 1/0` → shows ZeroDivisionError traceback; the next cell run does NOT show the leftover figure (sweep-on-error closed it).

All pass → feature complete. Any failure → note the step and return to the relevant task.

---

## Self-Review

**Spec coverage:**
- `MPLBACKEND=agg` before user imports → Task 1 Step 3 + test 7. ✓
- Echo via ast split, Jupyter semantics incl. `None`/assignment/stdout-ordering/globals → Task 1 (tests 5). ✓
- Sweep: no-matplotlib fast path, savefig→base64, `close("all")`, sweep-even-on-error so figures never leak → Task 1 Step 3 + Task 4 Step 6. ✓
- Interrupt guards preserved: echo/sweep run inside the existing inner try; outer except + finally untouched, `"images"` added to stale-path dict → Task 1 Step 3. ✓
- `Cell.Code.images` / `ExecutionResult.images` / bridge `asList()` → Task 2 Step 1. ✓
- ImageView container, adjustViewBounds, decode-failure logged and skipped → Task 2 Steps 2–3. ✓
- display_data write with trailing `\n`, read from display_data + execute_result, string-or-list form → Task 3. ✓
- Unknown output types ignored on read / dropped on write → outputs-as-JsonObject filters by type; unchanged behavior. ✓
- Host has no matplotlib → host tests only cover fast path; device covers real plots (spec Testing section). ✓

**Placeholder scan:** no TBD/TODO anywhere; all code steps complete; commands include expected output.

**Type consistency:** `images: List<String>` everywhere; `ExecutionResult(output, error, executionCount, images)` matches the Task 2 call site and Task 3 tests construct `Cell.Code(images = ...)` per the Task 2 model. `asLines()`/`outputType`/`streamName` defined and used only in Task 3.
