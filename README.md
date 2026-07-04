# JupyterDroid

**Jupyter notebooks, running natively on your phone.** Real `.ipynb` files, a real in-process Python kernel, matplotlib plots, offline — no server, no WebView, no cloud.

![platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![python](https://img.shields.io/badge/python-3.11-3776AB?logo=python&logoColor=white)
![kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![release](https://img.shields.io/github/v/release/bandeiracaio/JupyterDroid?label=release)
![license](https://img.shields.io/badge/license-MIT-blue)

Open a `.ipynb`, run Python, see the output — on a train, on a plane, anywhere. It's Jupyter Notebook, mobile-native.

---

## Contents

- [Why](#why)
- [Features](#features)
- [Not yet](#not-yet)
- [Install](#install)
- [First run](#first-run)
- [Development](#development)
- [Architecture](#architecture)
- [Error handling](#error-handling)
- [Tech stack](#tech-stack)
- [Roadmap](#roadmap)
- [Backlog](#backlog--ideas)
- [License](#license)

---

## Why

**JupyterDroid is Jupyter Notebook, mobile-native — no more, no less.**

Not a companion app, not a read-only viewer, not an IDE with notebook support bolted on. The goal is parity with what a `.ipynb` file and a Python kernel can do, running natively on a phone, online or off.

- **Real notebooks** — reads and writes standard `.ipynb` (nbformat 4), byte-compatible with JupyterLab and VS Code. Never a proprietary format.
- **Fast** — lean UI, in-process CPython, no server round-trips. Opening a notebook is instant.
- **Works anywhere** — no remote kernel, no WebView, no network dependency.
- **Native Android** — Kotlin + [Chaquopy](https://chaquo.com/chaquopy/). Not a wrapped web app.
- **Scoped like Jupyter** — if Jupyter Notebook doesn't do it, it's out of scope. No lock-in, no extensions.

---

## Features

**Notebooks & files**
- Create new `.ipynb` notebooks, or open existing ones from device storage via the system file picker
- Recent-files list on the home screen
- Edits save **back to the original file** through the Storage Access Framework — no cache copy, no export step
- Explicit **Save**, plus auto-save when the app is backgrounded

**Editing**
- **Code cells** with live Python **syntax highlighting** — keywords, strings, comments, numbers (incl. `1_000` / `0xFF` / `0o17`), and `@decorators`, themed for light and dark
- **Markdown cells** — tap to edit, tap away to render (headings, bold, lists)
- **Reorder** cells (drag handle or ↑/↓) and **delete** them (swipe or 🗑, with **Undo**)
- Survives screen **rotation** without losing edits or run state

**Execution**
- Run a cell, or **Run All** in order, with one tap
- **Stop** a running cell — the toolbar Run button becomes Stop and raises `KeyboardInterrupt`; the kernel and your variables survive
- Persistent state across cells — define a variable in cell 1, use it in cell 2
- **pip install** packages on-device, with failures that explain *why* (e.g. native packages can't compile on-device)

**Outputs**
- stdout / stderr rendered inline below each cell (stderr in red, with a one-tap **Copy error**)
- **Matplotlib plots** render as images in the cell — matplotlib is bundled, so no install needed — and are saved into the `.ipynb` as standard `image/png` outputs (they open with plots intact in desktop Jupyter)
- **Expression echo** — a bare expression on a cell's last line prints its value, like Jupyter (so `df` shows the DataFrame)
- Plot images are decoded off the main thread and cached, so scrolling a chart-heavy notebook stays smooth

**Reliability**
- **Kernel crash recovery** — if the kernel dies, Python is reset and a copyable error is surfaced so you can keep working

---

## Not yet

Gaps toward full notebook parity — not permanent limitations. See the [backlog](#backlog--ideas) for the full list.

- **Rich HTML outputs** — pandas DataFrames render as monospace text (via expression echo), not styled HTML tables
- **Export** — no PDF/HTML export yet
- **Multiple kernels** — one shared Python session per app run

---

## Install

### Option A — Download the APK (easiest)

1. **Allow sideloading:** Settings → Security (or Apps) → enable **Install unknown apps** for your browser or Files app.
2. **Download** `app-debug.apk` from the [latest release](https://github.com/bandeiracaio/JupyterDroid/releases/latest) and tap it to install.

Or over ADB from a computer:

```bash
curl -L https://github.com/bandeiracaio/JupyterDroid/releases/latest/download/app-debug.apk -o app-debug.apk
adb install -r app-debug.apk
```

> If `adb` isn't on your PATH, use the full path (e.g. `~/android-sdk/platform-tools/adb`). Run `adb devices` first — your phone should show as `device`; if it says `unauthorized`, unlock it and tap **Allow** on the USB-debugging prompt. This is a debug build, so Android will warn about the unknown source — expected for a sideloaded APK.

### Option B — Build from source

Requires [Android Studio](https://developer.android.com/studio) and a device or emulator on Android 7.0+ (API 24).

```bash
git clone https://github.com/bandeiracaio/JupyterDroid.git
```

Open the folder in Android Studio, **Sync Gradle** when prompted (downloads Chaquopy + Python 3.11 and bundles matplotlib), then hit **Run** (▶). The first build takes a few minutes while Chaquopy assembles Python for ARM; later builds are fast. See [Development](#development) for command-line builds.

---

## First run

The app ships with a bundled sample — **`sample_titanic_analysis.ipynb`** — seeded into your recent files on first launch. It walks the classic Titanic dataset using only the standard library, then adds matplotlib survival-rate bar charts, an age-distribution histogram, and an expression-echo demo. Tap **Run All** to see text output, plots, and echo together.

> Upgrading and don't see the updated sample? It seeds only once. Clear the app's data (or reinstall) to re-seed.

---

## Development

All Gradle commands need a JDK 17. The project uses Chaquopy 15.0.1 with build-Python 3.11.

```bash
# Build the debug APK
./gradlew assembleDebug            # -> app/build/outputs/apk/debug/app-debug.apk

# Install to a connected device / running emulator
./gradlew installDebug

# JVM unit tests (highlighter, nbformat round-trip, pip messages, sample notebook)
./gradlew testDebugUnitTest

# The Python kernel has its own host test — runs under plain python3, no device:
python3 app/src/test/python/test_kernel_runner.py
```

Instrumented tests (`app/src/androidTest/`, e.g. the adapter reorder/delete behavior) require a connected device or emulator: `./gradlew connectedDebugAndroidTest`.

---

## Architecture

Three layers, no magic:

```
UI  ──  RecyclerView of cells (Kotlin, Views)
        │
KernelManager  ──  Kotlin coroutines, Dispatchers.IO
        │
kernel_runner.py  ──  CPython in-process via Chaquopy
```

### Execution flow

1. User taps **Run** (or **Run All**) on a code cell.
2. `NotebookActivity` calls `KernelManager.execute(source)` on `Dispatchers.IO`.
3. Chaquopy invokes `kernel_runner.execute()`, which runs the source, captures stdout/stderr, echoes the last expression, and sweeps any open matplotlib figures to base64 PNGs.
4. An `ExecutionResult(output, error, executionCount, images)` returns to the main thread.
5. The cell's `RecyclerView` item refreshes — text inline, plots decoded off-thread into image views.

### Layout

```
app/src/main/
├── kotlin/com/jupyterdroid/
│   ├── JupyterDroidApp.kt          # Application entry
│   ├── MainActivity.kt             # home: recent files, create/open, seeds the sample
│   ├── NotebookActivity.kt         # notebook editor: run/stop, run-all, save, menu
│   ├── model/
│   │   ├── Cell.kt                 # sealed Cell: Cell.Code / Cell.Markdown
│   │   └── NotebookJson.kt         # nbformat data classes (kotlinx.serialization)
│   ├── kernel/
│   │   └── KernelManager.kt        # Chaquopy bridge: execute / interrupt / pipInstall / reset
│   ├── ui/
│   │   ├── NotebookAdapter.kt      # cell list, reorder/delete, drag
│   │   ├── CodeCellViewHolder.kt   # editor + highlighting + async image render
│   │   ├── MarkdownCellViewHolder.kt
│   │   ├── PythonHighlighter.kt    # regex tokenizer + span highlighter
│   │   └── PipInstallBottomSheet.kt
│   └── util/
│       ├── NotebookFile.kt         # read/write .ipynb (SAF + File)
│       ├── PipMessages.kt          # human-readable pip failure classification
│       └── ErrorReporter.kt        # copyable error text ("paste to Claude")
└── python/
    ├── kernel_runner.py            # exec-based kernel: echo, figure sweep, interrupt
    └── titanic.csv                 # data for the bundled sample
```

---

## Error handling

| Scenario | Behaviour |
|---|---|
| Python exception in a cell | Traceback shown inline below the cell in red, with a **Copy error** button |
| Cell interrupted (Stop) | `KeyboardInterrupt` traceback in the cell; kernel and globals survive |
| Kernel / bridge crash | Python is reset and a copyable **Kernel crash** snackbar is shown; Run All stops rather than running on against a dead kernel |
| pip install failure | Bottom sheet explains the cause — native-build limitation, package not found, or the raw stderr |
| File read/write error | Copyable error surfaced; a failed save never loses the in-memory notebook |

---

## Tech stack

| | |
|---|---|
| Language | Kotlin |
| Min / target SDK | API 24 (Android 7.0) / API 34 |
| Python runtime | [Chaquopy](https://chaquo.com/chaquopy/) 15.0.1, CPython 3.11 |
| Bundled packages | matplotlib (+ numpy) |
| Serialization | `kotlinx.serialization` |
| Markdown | [Markwon](https://github.com/noties/Markwon) |
| Concurrency | Kotlin Coroutines |
| UI | Views + RecyclerView + Material Components (no Compose, no WebView) |

---

## Roadmap

**V1 — core notebook** ✅
Project scaffold (Gradle + Chaquopy), `.ipynb` read/write, exec-based kernel with persistent globals, code + markdown cells, pip install, save / auto-save.

**V2 — notebook parity** ✅
- Matplotlib plots + expression echo — shipped 2026-07-03
- Kernel interrupt controls — shipped 2026-07-03
- Syntax highlighting — shipped 2026-07-02

Next up is drawn from the backlog below — likeliest headliners: HTML/PDF export, per-cell run + execution badges, and richer pandas output.

---

## Backlog / Ideas

Candidate work, grounded in the current codebase — a menu, not commitments. Struck-through items are done.

### Add

1. **Per-cell Run button + `In [n]` execution badges** — cells only run from the toolbar today; `executionCount` is tracked but never shown.
2. **Restart-kernel UI action** — `KernelManager.reset()` exists but is only called on crash; no user-facing way to clear state.
3. **Find & replace across cell sources** — no search of any kind yet.
4. **Export to HTML/PDF** — share a notebook as a self-contained document with plots inline.
5. **Python keyboard accessory row** (`() [] {} : " ' =` + Tab) — mobile typing aid.
6. **Clear-all-outputs action** — expected before sharing/committing a notebook.
7. **Variable/namespace inspector** — list current kernel globals.
8. **Duplicate-cell + copy-cell-to-clipboard** — we have delete/reorder but not duplicate.
9. **Per-cell "running" spinner** — no per-cell feedback while executing, only the toolbar Run→Stop swap.
10. **Auto-scroll to the executing cell during Run All** — no sense of progress on a long run.

### Improve

1. ~~**Syntax highlighter regex gaps** — `1_000`/`0o17` literals, decorators.~~ Done — `_`/octal numbers and `@decorators` handled; f-string interiors still want a real tokenizer.
2. ~~**pip UI fails silently for native packages.**~~ Done — `PipMessages` classifies native-build failures, missing packages, and surfaces stderr otherwise.
3. **Markwon is core-only** — markdown cells don't render tables, strikethrough, or task lists; add the GFM extensions.
4. **No unsaved-changes indicator** — add a dirty marker and debounced autosave-on-edit.
5. ~~**`execute_result` text/plain dropped on read.**~~ Done — read captures `execute_result`/`display_data` `text/plain` in document order (image preferred over its repr).
6. **Long outputs render as one giant TextView** — truncate with "show more" or make them scroll.
7. **Errors are raw red text** — add traceback collapsing.
8. **Whole-cell re-highlight on every keystroke** — O(n) rescan per character; incremental highlighting is the upgrade.
9. **Image cache is memory-only** — add a disk cache and downscale oversized plots.
10. **Stale-interrupt figure-sweep micro-window** — remaining hardening from the kernel-interrupt review.

### Remove

1. ~~**`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` permissions** — unused (app is SAF-based).~~ Done.
2. ~~**Leftover `titanic-sample-notebook` git worktree.**~~ Done.
3. **`matplotlib==3.6.0` + `fonttools==4.51.0` pins** — exist only to satisfy buildPython 3.9; bump buildPython and drop them.
4. ~~**`outputs: []` / `execution_count` on saved markdown cells** — non-standard nbformat.~~ Done.
5. **`execute_result`→`display_data` rewrite on save** — a special-case that changes output types; preserve the original.
6. ~~**Duplicate survival-grouping logic in the sample notebook.**~~ Done — shared `survival_counts` helper.
7. ~~**Per-bind re-allocation in `CodeCellViewHolder`.**~~ Done — listeners wired once, colors hoisted.
8. **Pre-scoped-storage assumptions** — purge remaining legacy external-storage code paths.
9. **The `plt.show()` workaround in the sample** — only there to suppress the Legend echo; removable if echo skips matplotlib artist objects.
10. **Resolved `ponytail:` debt comments** — audit and delete stale ones.

---

## License

[MIT](LICENSE) © 2026 bandeiracaio

Full design document: [`docs/superpowers/specs/2026-06-29-jupyterdroid-design.md`](docs/superpowers/specs/2026-06-29-jupyterdroid-design.md)
