# JupyterDroid

A native Android app for running Jupyter notebooks locally on-device — fast app, fast execution.

No remote server. No WebView. Python runs in-process, notebooks open instantly, and everything works offline.

---

## Philosophy

- **Fast above all** — lean UI, in-process kernel, no unnecessary layers
- **Real notebooks** — reads and writes standard `.ipynb` files compatible with JupyterLab and VS Code
- **Native Android** — Kotlin + Chaquopy, not a web wrapper

---

## V1 Features

- Create and open `.ipynb` files from device storage
- **Code cells** — write Python, run it, see output inline below the cell
- **Markdown cells** — write Markdown, render it in place
- **pip install** — install packages on-device without leaving the app
- Auto-save on background, explicit save button

---

## Architecture

Three layers, no magic:

```
UI (RecyclerView of cells)
        ↓
KernelManager (Kotlin coroutines)
        ↓
kernel_runner.py via Chaquopy (CPython in-process)
```

### Data model

Notebooks are plain Kotlin data classes serialized to/from `.ipynb` JSON via `kotlinx.serialization`. Cells are a sealed class:

```kotlin
sealed class Cell
data class CodeCell(val source: String, val outputs: List<String>, val executionCount: Int?) : Cell()
data class MarkdownCell(val source: String) : Cell()
```

### Execution flow

1. User taps **Run** on a code cell
2. `NotebookActivity` calls `KernelManager.execute(source)` on `Dispatchers.IO`
3. Chaquopy calls into `kernel_runner.py`, captures stdout/stderr
4. `ExecutionResult(output, error, executionCount)` returned to main thread
5. Cell output updates, `RecyclerView` item refreshes

### File structure

```
app/src/main/
├── kotlin/com/jupyterdroid/
│   ├── MainActivity.kt              # file list + create new
│   ├── NotebookActivity.kt          # notebook editor
│   ├── model/
│   │   ├── Notebook.kt              # data classes + JSON serialization
│   │   └── Cell.kt                  # sealed class CodeCell / MarkdownCell
│   ├── kernel/
│   │   └── KernelManager.kt         # Chaquopy lifecycle, execute(), pip()
│   ├── ui/
│   │   ├── NotebookAdapter.kt
│   │   ├── CodeCellViewHolder.kt
│   │   └── MarkdownCellViewHolder.kt
│   └── util/
│       └── NotebookFile.kt          # read/write .ipynb to storage
└── python/
    └── kernel_runner.py             # thin shim: starts ipykernel, exposes execute()
```

---

## Tech Stack

| | |
|---|---|
| Language | Kotlin |
| Min SDK | API 24 (Android 7.0) |
| Python runtime | [Chaquopy](https://chaquo.com/chaquopy/) |
| Jupyter kernel | `ipykernel` (installed via Chaquopy pip) |
| JSON | `kotlinx.serialization` |
| Markdown | [Markwon](https://github.com/noties/Markwon) |
| Concurrency | Kotlin Coroutines |

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| Python exception | stderr shown inline below cell in red |
| Kernel crash | auto-restart + snackbar "Kernel restarted" |
| pip install failure | stderr shown in bottom sheet |
| File I/O error | toast with OS error message |

---

## Roadmap

### V1 (current)
- [x] Design spec
- [ ] Project scaffold (Gradle + Chaquopy)
- [ ] `.ipynb` read/write
- [ ] Kernel integration
- [ ] Code cell UI + execution
- [ ] Markdown cell UI + rendering
- [ ] pip install UI
- [ ] Save / auto-save

### V2 (planned)
- Rich outputs: matplotlib plots, pandas tables, images
- Kernel restart / interrupt controls
- File browser
- Syntax highlighting in code cells

---

## Design Spec

Full design document: [`docs/superpowers/specs/2026-06-29-jupyterdroid-design.md`](docs/superpowers/specs/2026-06-29-jupyterdroid-design.md)
