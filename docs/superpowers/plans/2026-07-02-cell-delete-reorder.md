# Cell Delete & Reorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Notebook cells can be deleted (swipe or 🗑 button, with Undo snackbar) and reordered (drag handle or ↑/↓ buttons).

**Architecture:** A shared `cell_actions.xml` layout include adds a right-aligned action row (↑ ↓ ⠿ 🗑) to both cell layouts. `NotebookAdapter` gains `moveCell`/`deleteCell`/`restoreCell` and wires the buttons; all position callbacks switch from bind-time positions to `bindingAdapterPosition`, and `updateCellOutput` switches from position to object identity (`===`) so async execution results land on the right cell after moves/deletes. `NotebookActivity` attaches one `ItemTouchHelper` (swipe-to-delete, handle-initiated drag) and owns the Undo snackbar.

**Tech Stack:** Kotlin, `androidx.recyclerview` `ItemTouchHelper` (already a dependency), Material `Snackbar` (already used), AndroidJUnit4 instrumented tests (runner already configured).

## Global Constraints

- Long-press drag is disabled (`isLongPressDragEnabled = false`) — long-press inside a cell's EditText means text selection. Drag starts only from the ⠿ handle.
- Delete shows a Snackbar "Cell deleted" with an **Undo** action; no confirmation dialog.
- No new dependencies. Action buttons are unicode-glyph borderless buttons, not icon assets.
- Cells are `data class`es — identity lookups MUST use `===` (`indexOfFirst { it === cell }`), never `indexOf`, because two cells with equal content compare equal.
- Out of scope: multi-select, insert-at-position, cut/copy/paste.

---

### Task 1: Action-row layout include

**Files:**
- Create: `app/src/main/res/layout/cell_actions.xml`
- Modify: `app/src/main/res/layout/item_cell_code.xml`
- Modify: `app/src/main/res/layout/item_cell_markdown.xml`

**Interfaces:**
- Produces: view IDs `R.id.moveUpButton`, `R.id.moveDownButton`, `R.id.dragHandle`, `R.id.deleteCellButton` present in every cell item view (Tasks 2–3 look them up via `holder.itemView.findViewById`).

No automated test — layout XML only; compile check verifies resource references.

- [x] **Step 1: Create `app/src/main/res/layout/cell_actions.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="end"
    android:orientation="horizontal">

    <Button
        android:id="@+id/moveUpButton"
        style="?android:attr/borderlessButtonStyle"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:contentDescription="Move cell up"
        android:minWidth="0dp"
        android:text="↑"
        android:textSize="16sp" />

    <Button
        android:id="@+id/moveDownButton"
        style="?android:attr/borderlessButtonStyle"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:contentDescription="Move cell down"
        android:minWidth="0dp"
        android:text="↓"
        android:textSize="16sp" />

    <Button
        android:id="@+id/dragHandle"
        style="?android:attr/borderlessButtonStyle"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:contentDescription="Drag to reorder"
        android:minWidth="0dp"
        android:text="⠿"
        android:textSize="16sp" />

    <Button
        android:id="@+id/deleteCellButton"
        style="?android:attr/borderlessButtonStyle"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:contentDescription="Delete cell"
        android:minWidth="0dp"
        android:text="🗑"
        android:textSize="16sp" />
</LinearLayout>
```

- [x] **Step 2: Add the include to `item_cell_code.xml`**

Insert as the FIRST child of the root `LinearLayout` (before `sourceEdit`), so the actions sit above the code editor:

```xml
    <include layout="@layout/cell_actions" />
```

- [x] **Step 3: Restructure `item_cell_markdown.xml` to fit the include**

The markdown item's root is a `FrameLayout` (overlapping edit/rendered views); wrap it in a vertical `LinearLayout` with the actions row on top. Replace the entire file with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="8dp">

    <include layout="@layout/cell_actions" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

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
</LinearLayout>
```

- [x] **Step 4: Compile check**

Run: `JAVA_HOME=~/jdk17/zulu-17.jdk/Contents/Home ./gradlew :app:compileDebugKotlin :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Commit**

```bash
git add app/src/main/res/layout/cell_actions.xml app/src/main/res/layout/item_cell_code.xml app/src/main/res/layout/item_cell_markdown.xml
git commit -m "Add per-cell action row layout (move up/down, drag handle, delete)"
```

---

### Task 2: Adapter — move/delete/restore, identity-based output, live positions

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/ui/NotebookAdapter.kt`
- Modify: `app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt`
- Modify: `app/src/main/kotlin/com/jupyterdroid/ui/MarkdownCellViewHolder.kt`
- Test: `app/src/androidTest/kotlin/com/jupyterdroid/NotebookAdapterTest.kt` (new)

**Interfaces:**
- Consumes: view IDs from Task 1 (`R.id.moveUpButton`, `R.id.moveDownButton`, `R.id.dragHandle`, `R.id.deleteCellButton`).
- Produces (Task 3 relies on these exact signatures):
  - `NotebookAdapter(cells: MutableList<Cell>, onRunCell: (Int) -> Unit, markwon: Markwon, onDeleteRequested: (Int) -> Unit, onStartDrag: (RecyclerView.ViewHolder) -> Unit)`
  - `fun moveCell(from: Int, to: Int)`
  - `fun deleteCell(position: Int): Cell`
  - `fun restoreCell(position: Int, cell: Cell)`
  - `fun updateCellOutput(cell: Cell.Code, result: ExecutionResult)` (replaces the position-based overload)
  - `CodeCellViewHolder.bind(cell: Cell.Code, onSourceChanged: (Int, String) -> Unit)` and `MarkdownCellViewHolder.bind(cell: Cell.Markdown, onSourceChanged: (Int, String) -> Unit)` (position parameter removed)

The test is instrumented (adapter constructor needs a real `Markwon`, which needs a `Context`). `notifyItem*` calls are safe with no attached RecyclerView — the observable simply has no observers.

- [x] **Step 1: Write the failing test**

Create `app/src/androidTest/kotlin/com/jupyterdroid/NotebookAdapterTest.kt`:

```kotlin
package com.jupyterdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupyterdroid.kernel.ExecutionResult
import com.jupyterdroid.model.Cell
import com.jupyterdroid.ui.NotebookAdapter
import io.noties.markwon.Markwon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookAdapterTest {

    private fun adapterFor(cells: MutableList<Cell>) = NotebookAdapter(
        cells,
        onRunCell = {},
        markwon = Markwon.create(InstrumentationRegistry.getInstrumentation().targetContext),
        onDeleteRequested = {},
        onStartDrag = {}
    )

    @Test
    fun moveCellReordersList() {
        val cells = mutableListOf<Cell>(Cell.Code(source = "a"), Cell.Code(source = "b"), Cell.Code(source = "c"))
        adapterFor(cells).moveCell(0, 2)
        assertEquals(listOf("b", "c", "a"), cells.map { (it as Cell.Code).source })
    }

    @Test
    fun moveCellOutOfBoundsIsNoOp() {
        val cells = mutableListOf<Cell>(Cell.Code(source = "a"))
        val adapter = adapterFor(cells)
        adapter.moveCell(0, 1)
        adapter.moveCell(-1, 0)
        adapter.moveCell(0, 0)
        assertEquals(1, cells.size)
        assertEquals("a", (cells[0] as Cell.Code).source)
    }

    @Test
    fun deleteReturnsCellAndRestoreReinsertsIt() {
        val cells = mutableListOf<Cell>(Cell.Code(source = "a"), Cell.Markdown(source = "b"))
        val adapter = adapterFor(cells)
        val removed = adapter.deleteCell(0)
        assertEquals(1, cells.size)
        assertEquals("a", (removed as Cell.Code).source)
        adapter.restoreCell(0, removed)
        assertEquals(2, cells.size)
        assertSame(removed, cells[0])
    }

    @Test
    fun updateCellOutputTargetsCellByIdentityNotEquality() {
        // Two cells with EQUAL content — identity must disambiguate.
        val first = Cell.Code(source = "x")
        val second = Cell.Code(source = "x")
        val cells = mutableListOf<Cell>(first, second)
        val adapter = adapterFor(cells)

        adapter.updateCellOutput(second, ExecutionResult("out", "", 1))

        assertEquals("", (cells[0] as Cell.Code).output)   // first untouched
        assertEquals("out", (cells[1] as Cell.Code).output)
    }

    @Test
    fun updateCellOutputForDeletedCellIsNoOp() {
        val cell = Cell.Code(source = "x")
        val cells = mutableListOf<Cell>(cell)
        val adapter = adapterFor(cells)
        adapter.deleteCell(0)
        adapter.updateCellOutput(cell, ExecutionResult("out", "", 1)) // must not throw
        assertEquals(0, cells.size)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Start the emulator if not running: `~/android-sdk/emulator/emulator -avd JupyterDroid_test -no-snapshot-save -no-audio -no-boot-anim &` and wait for boot.

Run: `JAVA_HOME=~/jdk17/zulu-17.jdk/Contents/Home ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jupyterdroid.NotebookAdapterTest`
Expected: FAIL — compile error (constructor has no `onDeleteRequested`/`onStartDrag` parameters; `moveCell` etc. unresolved).

- [x] **Step 3: Rewrite `NotebookAdapter.kt`**

Replace the full file with:

```kotlin
package com.jupyterdroid.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.R
import com.jupyterdroid.kernel.ExecutionResult
import com.jupyterdroid.model.Cell
import io.noties.markwon.Markwon

class NotebookAdapter(
    val cells: MutableList<Cell>,
    private val onRunCell: (Int) -> Unit,
    private val markwon: Markwon,
    private val onDeleteRequested: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
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
            else -> MarkdownCellViewHolder.create(parent, markwon)
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CodeCellViewHolder -> holder.bind(cells[position] as Cell.Code, ::updateSource)
            is MarkdownCellViewHolder -> holder.bind(cells[position] as Cell.Markdown, ::updateSource)
        }
        wireActions(holder)
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

    fun moveCell(from: Int, to: Int) {
        if (from == to || from !in cells.indices || to !in cells.indices) return
        cells.add(to, cells.removeAt(from))
        notifyItemMoved(from, to)
    }

    fun deleteCell(position: Int): Cell {
        val cell = cells.removeAt(position)
        notifyItemRemoved(position)
        return cell
    }

    fun restoreCell(position: Int, cell: Cell) {
        cells.add(position, cell)
        notifyItemInserted(position)
    }

    // Identity (===), not equals: cells are data classes, so two cells with the
    // same content compare equal and indexOf would hit the wrong one.
    fun updateCellOutput(cell: Cell.Code, result: ExecutionResult) {
        val position = cells.indexOfFirst { it === cell }
        if (position == -1) return // cell deleted while executing
        cells[position] = cell.copy(
            output = result.output,
            error = result.error,
            executionCount = result.executionCount
        )
        notifyItemChanged(position)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireActions(holder: RecyclerView.ViewHolder) {
        val item = holder.itemView
        item.findViewById<View>(R.id.moveUpButton).setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos > 0) moveCell(pos, pos - 1) // pos > 0 also excludes NO_POSITION
        }
        item.findViewById<View>(R.id.moveDownButton).setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < cells.size - 1) moveCell(pos, pos + 1)
        }
        item.findViewById<View>(R.id.deleteCellButton).setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDeleteRequested(pos)
        }
        item.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(holder)
            false
        }
    }

    private fun updateSource(position: Int, source: String) {
        when (val cell = cells[position]) {
            is Cell.Code -> cells[position] = cell.copy(source = source)
            is Cell.Markdown -> cells[position] = cell.copy(source = source)
        }
    }
}
```

- [x] **Step 4: Switch `CodeCellViewHolder` to live positions**

In `CodeCellViewHolder.kt`, replace the `bind` signature and watcher (currently lines 24–34):

```kotlin
    fun bind(cell: Cell.Code, onSourceChanged: (Int, String) -> Unit) {
        // Remove old watcher before setText to avoid feedback loop on rebind
        watcher?.let { sourceEdit.removeTextChangedListener(it) }
        sourceEdit.setText(cell.source)

        watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onSourceChanged(pos, s.toString())
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        }
        sourceEdit.addTextChangedListener(watcher)
```

(The rest of `bind` — the output/error `when` block — is unchanged.)

- [x] **Step 5: Switch `MarkdownCellViewHolder` to live positions**

In `MarkdownCellViewHolder.kt`, replace the `bind` signature (line 20) and watcher (lines 35–39):

```kotlin
    fun bind(cell: Cell.Markdown, onSourceChanged: (Int, String) -> Unit) {
```

```kotlin
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onSourceChanged(pos, s.toString())
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        }
```

- [x] **Step 6: Fix `NotebookActivity` compile errors minimally**

Task 3 does the real wiring; this step only keeps the project compiling so the adapter test can run. In `NotebookActivity.kt` `onCreate`, replace the adapter construction:

```kotlin
        adapter = NotebookAdapter(
            cells,
            onRunCell = { position -> runCell(position) },
            markwon = Markwon.create(this),
            onDeleteRequested = { position -> deleteCellWithUndo(position) },
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) }
        )
```

Add a field and stub before `runCell` (Task 3 fills them in — the stub keeps this task self-contained):

```kotlin
    private lateinit var itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper

    private fun deleteCellWithUndo(position: Int) {
        // Wired fully in the ItemTouchHelper task
    }
```

And in `runCell`/`runAllCells`, replace position-based output updates with the identity-based call:

In `runCell`, replace `result?.let { adapter.updateCellOutput(position, it) }` with:

```kotlin
            result?.let { adapter.updateCellOutput(cell, it) }
```

In `runAllCells`, replace the loop body:

```kotlin
        lifecycleScope.launch {
            adapter.cells.toList().forEach { c ->
                (c as? Cell.Code)?.let { cell ->
                    val result = withContext(Dispatchers.IO) {
                        try { km.execute(cell.source) } catch (e: Exception) { null }
                    }
                    result?.let { adapter.updateCellOutput(cell, it) }
                }
            }
        }
```

(`toList()` snapshots the run order; deletions mid-run become no-ops via the identity lookup.)

- [x] **Step 7: Run test to verify it passes**

Run: `JAVA_HOME=~/jdk17/zulu-17.jdk/Contents/Home ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jupyterdroid.NotebookAdapterTest`
Expected: PASS (5 tests).

- [x] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/ui/NotebookAdapter.kt app/src/main/kotlin/com/jupyterdroid/ui/CodeCellViewHolder.kt app/src/main/kotlin/com/jupyterdroid/ui/MarkdownCellViewHolder.kt app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt app/src/androidTest/kotlin/com/jupyterdroid/NotebookAdapterTest.kt
git commit -m "NotebookAdapter: move/delete/restore cells, identity-based output updates"
```

---

### Task 3: `NotebookActivity` — ItemTouchHelper and Undo snackbar

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt`

**Interfaces:**
- Consumes from Task 2: `adapter.moveCell(from, to)`, `adapter.deleteCell(position): Cell`, `adapter.restoreCell(position, cell)`; the `itemTouchHelper` field and `deleteCellWithUndo` stub created in Task 2 Step 6.

No dedicated automated test — gesture wiring can't be driven without a UI-interaction test framework (not in this project); the underlying operations were tested in Task 2. Verified manually in Task 4.

- [x] **Step 1: Add imports**

```kotlin
import androidx.recyclerview.widget.ItemTouchHelper
```

Change the `itemTouchHelper` field declaration from Task 2's fully-qualified stub to:

```kotlin
    private lateinit var itemTouchHelper: ItemTouchHelper
```

- [x] **Step 2: Attach the ItemTouchHelper**

In `onCreate`, after `recycler.adapter = adapter`:

```kotlin
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveCell(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) deleteCellWithUndo(pos)
            }

            // Drag starts from the handle only — long-press inside a cell's
            // EditText means text selection, not reorder.
            override fun isLongPressDragEnabled() = false
        })
        itemTouchHelper.attachToRecyclerView(recycler)
```

- [x] **Step 3: Fill in `deleteCellWithUndo`**

Replace the Task 2 stub:

```kotlin
    private fun deleteCellWithUndo(position: Int) {
        val cell = adapter.deleteCell(position)
        Snackbar.make(findViewById(R.id.cellsRecyclerView), "Cell deleted", Snackbar.LENGTH_LONG)
            .setAction("Undo") { adapter.restoreCell(position, cell) }
            .show()
    }
```

- [x] **Step 4: Compile check**

Run: `JAVA_HOME=~/jdk17/zulu-17.jdk/Contents/Home ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt
git commit -m "NotebookActivity: swipe-to-delete with Undo, handle-initiated drag reorder"
```

---

### Task 4: Manual verification on emulator

**Files:** none (verification only)

- [x] **Step 1: Install**

Emulator running (`~/android-sdk/emulator/emulator -avd JupyterDroid_test ...` if needed), then:
Run: `JAVA_HOME=~/jdk17/zulu-17.jdk/Contents/Home ./gradlew installDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 2: Buttons**

Open the sample Titanic notebook. Every cell (code and markdown) shows ↑ ↓ ⠿ 🗑. Tap ↓ on the first cell — it swaps with the second. Tap ↑ — it swaps back. ↑ on the first cell and ↓ on the last cell do nothing (no crash).

- [x] **Step 3: Delete + Undo**

Tap 🗑 on a cell — it disappears, "Cell deleted" snackbar appears. Tap **Undo** — the cell returns in its old position with its content. Delete again and let the snackbar expire — cell stays gone.

- [x] **Step 4: Swipe**

Swipe a cell sideways (start from the action row or cell padding, not inside text) — same delete + Undo flow.

- [x] **Step 5: Drag**

Touch and drag the ⠿ handle — the cell lifts and reorders as you drag. Verify a plain long-press inside a cell's text does NOT start a drag (text selection appears instead).

- [x] **Step 6: State integrity**

Run a code cell (e.g. `print("hi")` in the sample), then move it up — output stays with the cell. Move a cell, edit its text, save, reopen — order and edits persisted.

- [x] **Step 7: Report results**

All pass → feature complete. Any failure → note the step and return to the relevant task.

---

## Self-Review

**Spec coverage:**
- Action row ↑ ↓ ⠿ 🗑 on both cell types → Task 1. ✓
- `moveCell`/`deleteCell`/`restoreCell` → Task 2 Step 3. ✓
- `bindingAdapterPosition` in ViewHolder callbacks → Task 2 Steps 4–5. ✓
- Identity-based `updateCellOutput` → Task 2 Steps 3 & 6, tested with equal-content cells. ✓
- ItemTouchHelper: swipe delete, handle-only drag, `isLongPressDragEnabled = false` → Task 3 Step 2. ✓
- Undo snackbar, no dialog → Task 3 Step 3. ✓
- Persistence via existing save path — no code needed; verified in Task 4 Step 6. ✓
- Out of scope items: none appear in any task. ✓

**Placeholder scan:** No TBD/TODO; every code step shows complete code; commands include expected output. Task 2 Step 6 creates a stub that Task 3 explicitly fills — intentional compile-bridge, labeled as such in both tasks.

**Type consistency:** Constructor signature in Task 2 Step 3 matches Task 2 Step 6's call site and the test's `adapterFor`. `updateCellOutput(cell: Cell.Code, result: ExecutionResult)` matches `ExecutionResult(output, error, executionCount)` (`kernel/KernelManager.kt:5`). `deleteCellWithUndo(position: Int)` referenced in Task 2 Step 6 and defined in Task 3 Step 3.
