# JupyterDroid

> Native Jupyter notebooks on Android — no server, no browser, just Python.

[![Release](https://img.shields.io/github/v/release/bandeiracaio/JupyterDroid?label=release)](https://github.com/bandeiracaio/JupyterDroid/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen?logo=android)](https://developer.android.com/about/versions/nougat)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?logo=kotlin)](https://kotlinlang.org)
[![Python](https://img.shields.io/badge/Python-3.11-yellow?logo=python)](https://www.python.org)
[![License](https://img.shields.io/github/license/bandeiracaio/JupyterDroid)](LICENSE)

JupyterDroid runs Jupyter notebooks locally on your Android device. CPython 3.11 executes in-process via [Chaquopy](https://chaquo.com/chaquopy/) — no Wi-Fi required, no remote kernel, no WebView. Notebooks are saved as standard `.ipynb` files that open directly in JupyterLab and VS Code.

---

## Features

- **Real `.ipynb` files** — reads and writes standard nbformat 4; compatible with JupyterLab, VS Code, and Colab
- **In-process Python kernel** — CPython 3.11 runs inside the app, no network required
- **Code cells** — write and execute Python; stdout/stderr shown inline below each cell
- **Persistent state** — variables defined in one cell are available in all subsequent cells
- **Markdown cells** — tap to edit, tap away to render (headings, bold, italic, lists, code)
- **Run all** — execute every code cell in order with one tap
- **pip install** — install packages on-device from a bottom sheet, without leaving the app
- **Auto-save** — saves automatically when the app goes to background; explicit save button also available
- **Kernel recovery** — detects crashes, restarts Python automatically, and notifies you

### Not yet supported

| Feature | Notes |
|---|---|
| Rich outputs | Only text stdout/stderr — no plots, images, or DataFrames |
| Kernel interrupt | Running cells cannot be stopped mid-execution |
| Cell reorder / delete | Cells can only be appended |
| Syntax highlighting | Plain EditText for now |
| Write-back to original file | Files opened via picker are copied to cache; saves go to the cache copy |
| Export | No PDF, HTML, or other formats |

---

## Installation

### Download APK (easiest)

1. Go to the [latest release](https://github.com/bandeiracaio/JupyterDroid/releases/latest) and download `app-debug.apk`
2. On your phone: Settings → Security → enable **Install unknown apps** for your browser or Files app
3. Open the downloaded APK and tap **Install**

### Install via ADB

Requires [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`).

```bash
# Download the APK
curl -L https://github.com/bandeiracaio/JupyterDroid/releases/latest/download/app-debug.apk -o app-debug.apk

# Enable USB Debugging on your phone:
# Settings → About phone → tap Build Number 7× → Developer Options → USB Debugging

# Connect via USB, verify the device is visible
adb devices

# Install
adb install app-debug.apk
```

If `adb devices` shows `unauthorized`, unlock your phone and tap **Allow** on the USB debugging prompt.

> This is a debug build signed with the Android debug key. Your phone may warn about an unknown source — that is expected for sideloaded APKs.

### Build from source

Requires [Android Studio](https://developer.android.com/studio) (latest stable) and a device or emulator running Android 7.0+ (API 24).

```bash
git clone https://github.com/bandeiracaio/JupyterDroid.git
```

1. Open the `JupyterDroid` folder in Android Studio
2. Click **Sync Now** when prompted — this downloads Chaquopy and Python 3.11 (~30 MB)
3. Connect a device or start an emulator
4. Press **Run** (▶) or `Shift+F10`

> First build takes a few minutes while Chaquopy compiles CPython for ARM. Subsequent builds are fast.

---

## How it works

### Architecture

```
┌─────────────────────────────────┐
│  UI Layer                       │
│  RecyclerView of Cell items     │
│  CodeCellViewHolder             │
│  MarkdownCellViewHolder         │
└────────────────┬────────────────┘
                 │ suspend fun on Dispatchers.IO
┌────────────────▼────────────────┐
│  KernelManager (singleton)      │
│  Kotlin Coroutines              │
└────────────────┬────────────────┘
                 │ Chaquopy JNI bridge
┌────────────────▼────────────────┐
│  kernel_runner.py               │
│  CPython 3.11 in-process        │
│  exec() + stdout/stderr capture │
└─────────────────────────────────┘
```

### Execution flow

1. User taps **Run** on a code cell
2. `NotebookActivity` dispatches `KernelManager.execute(source)` to `Dispatchers.IO`
3. Chaquopy calls `kernel_runner.py::execute()`, which redirects `sys.stdout`/`sys.stderr` to `StringIO` buffers and runs the source with `exec(compile(source, "<cell>", "exec"), globals)`
4. Python returns `{output, error, execution_count}` to Kotlin as a `PyObject` dict
5. `ExecutionResult` is posted back to the main thread; the RecyclerView item refreshes

The Python kernel is a singleton — globals persist across cells for the lifetime of the app session. `KernelManager.reset()` clears them.

### File format

Notebooks are read and written as standard [nbformat 4](https://nbformat.readthedocs.io/en/latest/format_description.html) JSON. The internal `Cell` sealed class maps directly to `NotebookCellJson` via `NotebookFile.kt`. Line endings follow the `.ipynb` convention: each line in a `source` array ends with `\n` except the last.

### Project structure

```
app/src/main/
├── kotlin/com/jupyterdroid/
│   ├── JupyterDroidApp.kt           # Application — starts Chaquopy
│   ├── MainActivity.kt              # Recent files list + new/open buttons
│   ├── NotebookActivity.kt          # Notebook editor
│   ├── kernel/
│   │   └── KernelManager.kt         # Singleton; execute(), pipInstall(), reset()
│   ├── model/
│   │   ├── Cell.kt                  # Sealed class: Code | Markdown
│   │   └── NotebookJson.kt          # @Serializable nbformat 4 schema
│   ├── ui/
│   │   ├── NotebookAdapter.kt       # RecyclerView adapter
│   │   ├── CodeCellViewHolder.kt    # Source EditText + output TextView
│   │   ├── MarkdownCellViewHolder.kt# Edit/render toggle via Markwon
│   │   └── PipInstallBottomSheet.kt # pip install UI
│   └── util/
│       └── NotebookFile.kt          # .ipynb read/write
└── python/
    └── kernel_runner.py             # exec-based kernel, stdout/stderr capture, pip
```

---

## Tech stack

| | |
|---|---|
| Language | Kotlin |
| Min SDK | API 24 (Android 7.0 Nougat) |
| Target SDK | API 34 (Android 14) |
| Python runtime | [Chaquopy](https://chaquo.com/chaquopy/) 15.0.1 |
| Python version | 3.11 |
| Serialization | `kotlinx.serialization-json` 1.6.3 |
| Markdown rendering | [Markwon](https://github.com/noties/Markwon) 4.6.2 |
| UI components | Material Components 1.11.0 |
| Concurrency | Kotlin Coroutines 1.7.3 |
| Build | Gradle 8.2, AGP 8.2.0 |

---

## Error handling

| Scenario | Behaviour |
|---|---|
| Python exception in cell | Full traceback shown inline below cell in red |
| Kernel crash | `KernelManager.reset()` called automatically; snackbar notifies user |
| pip install failure | stderr shown in the bottom sheet; button re-enabled |
| File read/write error | Toast with the OS error message |

---

## Roadmap

### V1 — current
- [x] Gradle scaffold with Chaquopy 15.0.1
- [x] `.ipynb` read/write (nbformat 4)
- [x] exec-based Python kernel with persistent globals
- [x] Code cell UI + execution + output display
- [x] Markdown cell UI + Markwon rendering
- [x] pip install bottom sheet
- [x] Save / auto-save on pause

### V2 — planned
- [ ] Rich outputs: matplotlib figures, pandas DataFrames, inline images
- [ ] Kernel interrupt (stop a running cell)
- [ ] Reorder and delete cells
- [ ] Syntax highlighting in code cells
- [ ] Write back to original file location (not just cache copy)
- [ ] Export to HTML / PDF

---

## Contributing

Pull requests are welcome. For significant changes, open an issue first to discuss what you'd like to change.

```bash
git clone https://github.com/bandeiracaio/JupyterDroid.git
cd JupyterDroid
# Open in Android Studio and build
```

---

## License

[MIT](LICENSE)
