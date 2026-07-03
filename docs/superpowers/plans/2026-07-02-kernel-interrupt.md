# Kernel Interrupt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop a running cell mid-execution via a toolbar Run↔Stop swap; the cell shows a `KeyboardInterrupt` error and the kernel survives.

**Architecture:** `kernel_runner.py` records the executing thread id and a new `interrupt()` injects `KeyboardInterrupt` via `ctypes.pythonapi.PyThreadState_SetAsyncExc`. `KernelManager.interrupt()` bridges it. `NotebookActivity` tracks `isRunning`/`stopRequested`, retitles the BottomAppBar "Run" item to "Stop" while executing, and breaks the run-all loop between cells on stop.

**Tech Stack:** Python stdlib (ctypes, threading), Kotlin, Chaquopy bridge.

**Spec:** `docs/superpowers/specs/2026-07-02-kernel-interrupt-design.md`

## Global Constraints

- No new dependencies.
- All gradle commands need `export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home` first (no system Java).
- Branch: `kernel-interrupt` (already created and checked out).
- `kernel_runner.py` must stay pure stdlib so it is testable with host `python3` (available: Python 3.9.6 at `python3`).
- The Run control is a `BottomAppBar` menu (`app/src/main/res/menu/menu_notebook.xml`), NOT the options menu — there is no `onPrepareOptionsMenu`; the title is set directly on the retained `MenuItem`.

---

### Task 1: kernel_runner interrupt + host-python test

**Files:**
- Modify: `app/src/main/python/kernel_runner.py`
- Test: `app/src/test/python/test_kernel_runner.py` (new dir; plain assert script run with host `python3`, not gradle)

**Interfaces:**
- Consumes: existing `execute(source)` / module globals in `kernel_runner.py`.
- Produces: `interrupt() -> bool` (True if something was running and an interrupt was sent, False if idle). `execute()` now catches `BaseException`, so an interrupted cell returns normally with `"KeyboardInterrupt"` in `result["error"]`. Task 2 calls `interrupt()` through Chaquopy.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/python/test_kernel_runner.py`:

```python
import os
import sys
import threading
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "main", "python"))
import kernel_runner

# 1. Interrupt while idle is a no-op returning False.
assert kernel_runner.interrupt() is False

# 2. Interrupt breaks an infinite loop; error is a KeyboardInterrupt traceback.
result = {}
t = threading.Thread(target=lambda: result.update(kernel_runner.execute("while True:\n    pass")))
t.start()
time.sleep(0.3)  # let exec() enter the loop
assert kernel_runner.interrupt() is True
t.join(5)
assert not t.is_alive(), "cell thread did not stop within 5s"
assert "KeyboardInterrupt" in result["error"], result["error"]
assert result["output"] == ""

# 3. Kernel survives: globals persist across the interrupt, next execute works.
kernel_runner.execute("x = 41")
r = kernel_runner.execute("print(x + 1)")
assert r["error"] == "", r["error"]
assert r["output"].strip() == "42"

# 4. Thread id cleared after execute: a second idle interrupt is False again.
assert kernel_runner.interrupt() is False

print("ALL PASS")
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 app/src/test/python/test_kernel_runner.py`
Expected: FAIL with `AttributeError: module 'kernel_runner' has no attribute 'interrupt'`

- [ ] **Step 3: Implement interrupt in kernel_runner.py**

In `app/src/main/python/kernel_runner.py`, add to the imports at the top:

```python
import ctypes
import threading
```

Add a module global next to the existing ones (`_execution_count`, `_globals`):

```python
_exec_thread_id = None
```

Replace the existing `execute` function with:

```python
def execute(source):
    global _execution_count, _globals, _exec_thread_id
    _execution_count += 1
    _exec_thread_id = threading.get_ident()

    stdout_buf = io.StringIO()
    stderr_buf = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = stdout_buf, stderr_buf

    error = ""
    try:
        exec(compile(source, "<cell>", "exec"), _globals)
    except BaseException:  # BaseException so KeyboardInterrupt lands here, not in the bridge
        error = traceback.format_exc()
    finally:
        _exec_thread_id = None
        sys.stdout, sys.stderr = old_out, old_err

    return {
        "output": stdout_buf.getvalue(),
        "error": error,
        "execution_count": _execution_count,
    }
```

Add below `execute`:

```python
def interrupt():
    # ponytail: PyThreadState_SetAsyncExc delivers between Python bytecodes.
    # A blocking C call (time.sleep, native pandas op) only sees it on return.
    # Kernel reset remains the hard stop.
    tid = _exec_thread_id
    if tid is None:
        return False
    ctypes.pythonapi.PyThreadState_SetAsyncExc(
        ctypes.c_ulong(tid), ctypes.py_object(KeyboardInterrupt))
    return True
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python3 app/src/test/python/test_kernel_runner.py`
Expected: `ALL PASS`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/python/kernel_runner.py app/src/test/python/test_kernel_runner.py
git commit -m "kernel_runner: interruptible execute via PyThreadState_SetAsyncExc"
```

---

### Task 2: KernelManager bridge + toolbar Run↔Stop

**Files:**
- Modify: `app/src/main/kotlin/com/jupyterdroid/kernel/KernelManager.kt`
- Modify: `app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt`

**Interfaces:**
- Consumes: `kernel_runner.interrupt() -> bool` from Task 1.
- Produces: `KernelManager.interrupt(): Boolean`; `NotebookActivity` state `isRunning`/`stopRequested` and helper `setRunning(running: Boolean)`.

- [ ] **Step 1: Add KernelManager.interrupt**

In `KernelManager.kt`, add below `execute`:

```kotlin
    fun interrupt(): Boolean = runner.callAttr("interrupt").toBoolean()
```

- [ ] **Step 2: Add running state to NotebookActivity**

In `NotebookActivity.kt`, add `import android.view.MenuItem` to the imports (if not present). Add fields near the other `private var`s:

```kotlin
    private var runMenuItem: MenuItem? = null
    private var isRunning = false
    private var stopRequested = false
```

Add this helper next to `runCell`:

```kotlin
    private fun setRunning(running: Boolean) {
        isRunning = running
        runMenuItem?.title = if (running) "Stop" else "Run"
    }
```

- [ ] **Step 3: Wire the BottomAppBar menu**

In `onCreate`, the menu listener currently reads (around `NotebookActivity.kt:110-129`):

```kotlin
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
```

Change those two branches to (and add the `runMenuItem` line after the listener block):

```kotlin
        val bar = findViewById<BottomAppBar>(R.id.bottomAppBar)
        bar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_run_cell -> {
                    if (isRunning) {
                        stopRequested = true
                        km.interrupt()
                    } else {
                        val pos = (recycler.layoutManager as LinearLayoutManager)
                            .findLastVisibleItemPosition()
                        if (pos >= 0) runCell(pos)
                    }
                    true
                }
                R.id.action_run_all -> { if (!isRunning) runAllCells(); true }
```

(The remaining branches — add_code, add_md, pip, save — are unchanged.) After the `bar.setOnMenuItemClickListener { ... }` block, add:

```kotlin
        runMenuItem = bar.menu.findItem(R.id.action_run_cell)
```

- [ ] **Step 4: Flip state in runCell and runAllCells**

Replace `runCell` with:

```kotlin
    private fun runCell(position: Int) {
        val cell = adapter.cells.getOrNull(position) as? Cell.Code ?: return
        stopRequested = false
        setRunning(true)
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
            setRunning(false)
            result?.let { adapter.updateCellOutput(cell, it) }
        }
    }
```

Replace `runAllCells` with:

```kotlin
    private fun runAllCells() {
        // Sequential: one coroutine, in order — parallel would cause race conditions in Python globals
        stopRequested = false
        setRunning(true)
        lifecycleScope.launch {
            for (c in adapter.cells.toList()) {
                if (stopRequested) break  // interrupt killed the current cell; don't start the next
                val cell = c as? Cell.Code ?: continue
                val result = withContext(Dispatchers.IO) {
                    try { km.execute(cell.source) } catch (e: Exception) { null }
                }
                result?.let { adapter.updateCellOutput(cell, it) }
            }
            setRunning(false)
        }
    }
```

- [ ] **Step 5: Build and run unit tests**

```bash
export JAVA_HOME=/Users/bandeiracaio/jdk17/zulu-17.jdk/Contents/Home
./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/jupyterdroid/kernel/KernelManager.kt app/src/main/kotlin/com/jupyterdroid/NotebookActivity.kt
git commit -m "Toolbar Run/Stop: interrupt running cell, stop run-all loop"
```

---

### Task 3: Manual verification (emulator)

**Files:** none — install with `./gradlew installDebug` (emulator `JupyterDroid_test`; adb at `/Users/bandeiracaio/android-sdk/platform-tools/adb`).

- [ ] **Step 1: Interrupt an infinite loop**

New notebook, code cell `while True: pass`, tap Run (toolbar shows "Stop" while running), tap Stop. Cell shows a red `KeyboardInterrupt` traceback; app stays responsive.

- [ ] **Step 2: Kernel survives**

Cell `x = 1` → Run; cell `while True: pass` → Run → Stop; cell `print(x)` → Run prints `1`. Globals intact, kernel alive.

- [ ] **Step 3: Blocking C call ceiling**

Cell `import time; time.sleep(15)` → Run → Stop immediately. The cell keeps running until the sleep ends, then shows `KeyboardInterrupt`. (Documents the ceiling; expected behavior.)

- [ ] **Step 4: Run All stops**

Cells: `print("a")`, `while True: pass`, `print("c")`. Run All → Stop during the loop. Cell 2 shows `KeyboardInterrupt`; cell 3 shows no new output (never ran). Toolbar returns to "Run".

- [ ] **Step 5: Stop while idle**

With nothing running the item reads "Run"; tapping it runs the focused cell as before. No crash paths.

All pass → feature complete. Any failure → note the step and return to the relevant task.

---

## Self-Review

**Spec coverage:**
- `_exec_thread_id` set/cleared in `execute`, `except BaseException` → Task 1 Step 3. ✓
- `interrupt()` with idle guard + `c_ulong` + ponytail ceiling comment → Task 1 Step 3. ✓
- `KernelManager.interrupt(): Boolean` → Task 2 Step 1. ✓
- `isRunning`/`stopRequested`, Run↔Stop title, run-all break between cells, run-all ignored while running → Task 2 Steps 2–4. ✓ (Spec's `onPrepareOptionsMenu` wording adapted: control is a BottomAppBar menu, title set directly on the retained MenuItem — noted in Global Constraints.)
- Spec test scenarios 1–5 → Task 3 Steps 1–5 (+ Task 1's host-python test covers 1, 2, 5 mechanically). ✓
- Stale-thread-id risk → cleared in `finally` (Task 1 Step 3), verified by test assertion 4. ✓

**Placeholder scan:** none; all code steps show complete code, commands show expected output.

**Type consistency:** `interrupt() -> bool` (Python) ↔ `toBoolean()` (Kotlin). `setRunning` defined Task 2 Step 2, used Steps 3–4. `stopRequested` set in menu branch, cleared/checked in run functions.
