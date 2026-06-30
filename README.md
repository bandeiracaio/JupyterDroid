# JupyterDroid

A native Android app for running Jupyter notebooks locally on-device — fast app, fast execution.

No remote server. No WebView. Python runs in-process, notebooks open instantly, and everything works offline.

---

## Philosophy

- **Fast above all** — lean UI, in-process kernel, no unnecessary layers
- **Real notebooks** — reads and writes standard `.ipynb` files compatible with JupyterLab and VS Code
- **Native Android** — Kotlin + Chaquopy, not a web wrapper

---

## What it can do

- Create new `.ipynb` notebooks (standard Jupyter nbformat 4 — opens in JupyterLab/VS Code)
- Open existing `.ipynb` files from device storage via file picker
- Keep a recent files list
- **Code cells** — write Python, run it, see stdout/stderr inline below the cell
- State persists across cells — define a variable in cell 1, use it in cell 2
- **Markdown cells** — tap to edit, tap away to render (headings, bold, lists)
- Run all cells in order with one tap
- **pip install** packages on-device without leaving the app
- Explicit save button + auto-save when the app backgrounds
- Kernel crash recovery — auto-restarts Python and shows a notification

## What it cannot do (yet)

- **Rich outputs** — matplotlib plots, pandas DataFrames, images — only text stdout/stderr for now
- **Kernel interrupt** — no way to stop a running cell mid-execution
- **Reorder or delete cells** — cells can only be added at the bottom
- **Syntax highlighting** in code cells
- **Write back to original file** — files opened via picker are copied to cache; saves go to the cache copy, not back to the original location
- **Export** — no PDF, HTML, or other formats
- **Multiple kernels** — one shared Python state per app session

---

## Install on Android

You need Android Studio to build and install the app. No APK is distributed yet.

### Requirements

- [Android Studio](https://developer.android.com/studio) (latest stable)
- An Android device running Android 7.0+ (API 24), or an emulator
- USB cable (for physical device) or AVD configured (for emulator)

### Steps

**1. Clone the repo**

```bash
git clone https://github.com/bandeiracaio/JupyterDroid.git
cd JupyterDroid
```

**2. Open in Android Studio**

File → Open → select the `JupyterDroid` folder → Open.

**3. Sync Gradle**

Android Studio will prompt "Gradle files have changed" — click **Sync Now**. This downloads Chaquopy and Python 3.11 (~30 MB). Wait until the sync finishes.

**4. Connect your device**

- **Physical device:** enable Developer Options on your phone (Settings → About → tap Build Number 7 times), then enable USB Debugging. Connect via USB. Your device should appear in the device dropdown at the top of Android Studio.
- **Emulator:** in Android Studio open Device Manager (right sidebar) → Create Device → choose a Pixel profile → select an API 24+ system image → Finish.

**5. Run**

Click the green **Run** button (▶) or press `Shift+F10`. Android Studio builds the APK, installs it, and launches JupyterDroid on your device.

> First build takes a few minutes — Chaquopy compiles Python for ARM. Subsequent builds are fast.

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
    └── kernel_runner.py             # exec-based kernel, stdout/stderr capture
```

---

## Tech Stack

| | |
|---|---|
| Language | Kotlin |
| Min SDK | API 24 (Android 7.0) |
| Python runtime | [Chaquopy](https://chaquo.com/chaquopy/) 15.0.1 |
| Python version | 3.11 |
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

### V1 (current — `feat/v1-implementation`)
- [x] Project scaffold (Gradle + Chaquopy)
- [x] `.ipynb` read/write (standard nbformat 4)
- [x] Kernel integration (exec-based, persistent globals)
- [x] Code cell UI + execution
- [x] Markdown cell UI + rendering
- [x] pip install UI
- [x] Save / auto-save

### V2 (planned)
- Rich outputs: matplotlib plots, pandas tables, images
- Kernel interrupt controls
- Reorder / delete cells
- Syntax highlighting in code cells
- Write back to original file location

---

## Design Spec

Full design document: [`docs/superpowers/specs/2026-06-29-jupyterdroid-design.md`](docs/superpowers/specs/2026-06-29-jupyterdroid-design.md)
