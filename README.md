# JupyterDroid

A native Android app for running Jupyter notebooks locally on-device — fast app, fast execution.

No remote server. No WebView. Python runs in-process, notebooks open instantly, and everything works offline.

---

## Philosophy

**JupyterDroid is Jupyter Notebook, mobile-native — no more, no less.**

Not a companion app, not a lightweight viewer, not an IDE with notebook support bolted on: the goal is full parity with what a `.ipynb` file and a Python kernel can do, running natively on a phone, online or off.

- **Real notebooks** — reads and writes standard `.ipynb` (nbformat 4), fully compatible with JupyterLab and VS Code. Never a proprietary format.
- **Fast** — lean UI, in-process kernel, no unnecessary layers. Opening a notebook should feel instant, not like waiting for a server.
- **Works anywhere** — no remote server, no WebView, no network dependency. A notebook you can open on a train.
- **Native Android** — Kotlin + Chaquopy. Not a wrapped web app.
- **No more than Jupyter** — if it's not something Jupyter Notebook does, it's out of scope. No proprietary extensions, no lock-in.

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

Gaps toward full notebook parity — not permanent limitations.

- **Rich outputs** — matplotlib plots, pandas DataFrames, images — only text stdout/stderr for now
- **Kernel interrupt** — no way to stop a running cell mid-execution
- **Reorder or delete cells** — cells can only be added at the bottom
- **Syntax highlighting** in code cells
- **Write back to original file** — files opened via picker are copied to cache; saves go to the cache copy, not back to the original location
- **Export** — no PDF, HTML, or other formats
- **Multiple kernels** — one shared Python state per app session

---

## Install on Android

### Option A — Direct APK install (easiest)

**1. Enable Unknown Sources on your phone**

Settings → Security (or Apps) → enable **Install unknown apps** for your browser or Files app.

**2. Enable USB Debugging**

Settings → About phone → tap **Build Number** 7 times → Developer Options → enable **USB Debugging**.

**3. Download the APK**

Go to the [latest release](https://github.com/bandeiracaio/JupyterDroid/releases/latest) and download `app-debug.apk`.

Or install via ADB from your Mac:

```bash
# Download
curl -L https://github.com/bandeiracaio/JupyterDroid/releases/latest/download/app-debug.apk -o app-debug.apk

# Connect phone via USB, then:
adb install app-debug.apk
```

If `adb` is not on your PATH, use the full path: `~/android-sdk/platform-tools/adb`.

**4. Verify ADB sees your phone**

```bash
adb devices
```

You should see your device listed as `device`. If it says `unauthorized`, unlock your phone and tap **Allow** on the USB debugging prompt.

**5. Install**

```bash
adb install app-debug.apk
```

`Success` means the app is installed. Open **JupyterDroid** from your launcher.

> **Note:** This is a debug build. Android may warn you it's from an unknown source — that's expected for a sideloaded APK.

---

### Option B — Build from source (Android Studio)

Requires [Android Studio](https://developer.android.com/studio) (latest stable) and an Android device or emulator running Android 7.0+ (API 24).

**1. Clone and open**

```bash
git clone https://github.com/bandeiracaio/JupyterDroid.git
```

File → Open → select the `JupyterDroid` folder → Open.

**2. Sync Gradle**

Android Studio will prompt "Gradle files have changed" — click **Sync Now**. This downloads Chaquopy and Python 3.11 (~30 MB).

**3. Connect your device**

- **Physical device:** enable Developer Options (Settings → About → tap Build Number 7 times), then enable USB Debugging. Connect via USB.
- **Emulator:** Device Manager → Create Device → Pixel profile → API 24+ system image → Finish.

**4. Run**

Click the green **Run** button (▶) or press `Shift+F10`.

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

### V2 (planned) — closing the gap toward full notebook parity
- Rich outputs: matplotlib plots, pandas tables, images
- Kernel interrupt controls
- Reorder / delete cells
- Syntax highlighting in code cells
- Write back to original file location

---

## Design Spec

Full design document: [`docs/superpowers/specs/2026-06-29-jupyterdroid-design.md`](docs/superpowers/specs/2026-06-29-jupyterdroid-design.md)
