# Kernel Interrupt — Design

**Date:** 2026-07-02
**Status:** Approved

## Goal

Stop a running cell mid-execution. The toolbar "Run" action becomes "Stop" while anything executes; tapping it raises `KeyboardInterrupt` in the executing cell, which shows as a normal cell error. The kernel survives — globals intact, next run works.

## Approach

Inject an async `KeyboardInterrupt` into the executing Python thread via `ctypes.pythonapi.PyThreadState_SetAsyncExc`, targeting the thread id recorded at `execute()` start.

Rejected alternatives:
- **`_thread.interrupt_main()`** — targets Python's main thread; cells execute on a background (Dispatchers.IO) thread, so it would never hit.
- **Subprocess-per-cell kernel** — real preemptive kill, but destroys the shared-globals model and is a rewrite, not a feature.

## Components

### `kernel_runner.py` (modified)

- Module global `_exec_thread_id`: set to `threading.get_ident()` at `execute()` entry, cleared to `None` in the existing `finally`.
- `execute()`'s `except Exception` becomes `except BaseException` so `KeyboardInterrupt` (and `SystemExit`) are captured as the cell's `error` traceback instead of propagating through the Chaquopy bridge.
- New function:

```python
def interrupt():
    tid = _exec_thread_id
    if tid is None:
        return False
    ctypes.pythonapi.PyThreadState_SetAsyncExc(
        ctypes.c_ulong(tid), ctypes.py_object(KeyboardInterrupt))
    return True
```

Known ceiling (`# ponytail:` comment in code): `PyThreadState_SetAsyncExc` delivers between Python bytecodes. A blocking C call (`time.sleep`, a native pandas op) only sees the interrupt when it returns. The existing kernel-crash/reset path remains the hard-stop fallback.

### `KernelManager.kt` (modified)

```kotlin
fun interrupt(): Boolean = runner.callAttr("interrupt").toBoolean()
```

Called from the main thread while `execute()` blocks an IO thread — safe; Chaquopy manages the GIL per call.

### `NotebookActivity.kt` (modified)

- `private var isRunning = false`, flipped around every `km.execute` call (single-cell and run-all), with `invalidateOptionsMenu()` on each flip.
- `private var stopRequested = false`, set when Stop is tapped, checked by the run-all loop between cells (an interrupt kills only the current cell; the flag stops the loop from starting the next one). Cleared at the start of each run action.
- `onPrepareOptionsMenu`: `action_run_cell` title = "Stop" when `isRunning`, else "Run".
- `onOptionsItemSelected` for `action_run_cell`: if `isRunning` → `stopRequested = true; km.interrupt()`; else run the focused cell as today.
- `action_run_all` while running: ignored (single toolbar Stop is the only control while executing).

## Behavior summary

| State | Toolbar "Run" item | Effect of tap |
|---|---|---|
| Idle | "Run" | Runs focused cell |
| Cell running (single or run-all) | "Stop" | `KeyboardInterrupt` into cell; run-all loop also stops |

Interrupted cell shows the `KeyboardInterrupt` traceback in its error area (red), exactly like any other exception. Execution count still increments. Globals defined before the interrupt survive.

## Scope

- No per-cell run/stop buttons (cells run from the toolbar today; unchanged).
- No timeout/auto-interrupt.
- No hard kill for blocked C calls — documented ceiling, reset path already exists.

## Testing

Python/device behavior, so emulator verification:
1. Run `while True: pass` → tap Stop → cell shows `KeyboardInterrupt`, app responsive.
2. After interrupt, run `print("alive")` → works; a variable defined pre-interrupt is still present.
3. `import time; time.sleep(15)` → Stop → returns with `KeyboardInterrupt` only when the sleep ends (documents the ceiling).
4. Run All with a `while True: pass` in the middle → Stop → later cells do NOT execute.
5. Stop while idle → no-op, no crash.

## Risks

- Async exception delivery is at bytecode granularity — covered above.
- `PyThreadState_SetAsyncExc` with a stale/finished thread id: guarded by clearing `_exec_thread_id` in `finally`; worst case the call targets no live thread and affects nothing.
