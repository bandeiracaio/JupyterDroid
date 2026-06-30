# JupyterDroid V1 Design

## Overview

Native Android app (Kotlin, API 24+) that runs Jupyter notebooks locally on-device. Core philosophy: fast app, fast execution. No remote server, no WebView — everything runs in-process.

## V1 Scope

- Create and open `.ipynb` files (standard Jupyter format, compatible with JupyterLab/VS Code)
- Code cells — write and run Python, output displayed inline below the cell
- Markdown cells — rendered text
- pip package installation on-device
- Save/load from local device storage

Out of scope for V1: rich outputs (plots, images, tables), file browser beyond recent list, kernel selection, export.

## Architecture

Three layers:

### 1. Kernel Layer
Chaquopy embeds CPython. On notebook open, a `KernelManager` singleton starts an `ipykernel` instance in a background coroutine. Code execution calls into `kernel_runner.py` via Chaquopy, captures stdout/stderr, and returns an `ExecutionResult`. pip installs run via `subprocess` on a separate thread.

### 2. Data Layer
`Notebook` is a Kotlin data class mirroring the `.ipynb` JSON schema via `kotlinx.serialization`. Cells are a sealed class: `CodeCell(source, outputs, executionCount)` and `MarkdownCell(source)`. `NotebookFile` handles read/write to local storage.

### 3. UI Layer
`MainActivity` — file list (recent notebooks + create new button).  
`NotebookActivity` — single `RecyclerView` of cells, floating toolbar (add cell / run cell / run all / pip install / save).  
`CodeCellViewHolder` — `EditText` for source + `TextView` for output below.  
`MarkdownCellViewHolder` — `EditText` in edit mode, Markwon-rendered `TextView` in view mode.

## File Structure

```
app/src/main/
├── kotlin/com/jupyterdroid/
│   ├── MainActivity.kt
│   ├── NotebookActivity.kt
│   ├── model/
│   │   ├── Notebook.kt
│   │   └── Cell.kt
│   ├── kernel/
│   │   └── KernelManager.kt
│   ├── ui/
│   │   ├── NotebookAdapter.kt
│   │   ├── CodeCellViewHolder.kt
│   │   └── MarkdownCellViewHolder.kt
│   └── util/
│       └── NotebookFile.kt
└── python/
    └── kernel_runner.py
```

## Data Flow

1. User taps Run → `NotebookActivity` calls `KernelManager.execute(source)`
2. Dispatched to `Dispatchers.IO`, calls `kernel_runner.py` via Chaquopy
3. Python returns `ExecutionResult(output, error, executionCount)`
4. Main thread updates cell output, `notifyItemChanged`

## Error Handling

| Scenario | Behaviour |
|---|---|
| Python exception | stderr displayed inline below cell in red |
| Kernel crash | auto-restart, snackbar "Kernel restarted" |
| pip install failure | stderr shown in bottom sheet dialog |
| File I/O error | toast with OS error message |

## Save Strategy

- Explicit save button
- Auto-save on `onPause` (app backgrounded)
- No keystroke auto-save (performance)
- pip installs block UI via `ProgressDialog` (prevents partial-import crashes)

## Dependencies

| Dependency | Purpose |
|---|---|
| Chaquopy | Embed CPython, pip API |
| ipykernel | Jupyter kernel (installed via Chaquopy pip) |
| kotlinx.serialization | .ipynb JSON read/write |
| Markwon | Markdown rendering in TextViews |

## Testing

1. **Serialization unit test** — read a real `.ipynb` fixture, assert round-trip write produces identical JSON
2. **Kernel integration test** — `execute("print('hello')")` returns `"hello\n"`; bad expression returns non-empty error
3. **Manual smoke test** — create → add code cell → run → add markdown → render → pip install → run with package → save → reopen → verify

## Target

- Min SDK: API 24 (Android 7.0)
- Language: Kotlin
- Build: Gradle + Chaquopy plugin
