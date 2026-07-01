# Write back to original file location

## Problem

Notebooks opened via the Android file picker (`ACTION_OPEN_DOCUMENT`) are copied into the app's cache directory on open. Saves write to that cache copy, never back to the file the user actually picked — so edits silently diverge from the original `.ipynb` on disk.

## Scope

Only the file-picker flow changes. Notebooks created inside the app ("New notebook") keep saving into the app's private external-files folder exactly as today — no Storage Access Framework (SAF) save picker is added for that path.

## Design

Read and write directly through the picked `content://` Uri via `ContentResolver`. This removes the cache-copy step entirely rather than patching around it — a simplification, not just a fix. The Python kernel has no dependency on the notebook's on-disk path (it only ever sees cell source strings), so nothing downstream needs a real `File`.

### `NotebookFile.kt`

- Extract a pure `serialize(notebookJson: NotebookJson, cells: List<Cell>): String`. The existing `write(notebookJson, cells, file: File)` becomes a one-line wrapper: `file.writeText(serialize(...))`.
- Add `read(text: String): Pair<NotebookJson, List<Cell>>` alongside the existing `read(file: File)`, which becomes a wrapper: `read(file.readText())`.

### `MainActivity.kt`

- `openFromUri(uri)`: call `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)` so access survives app restarts. No cache copy — launch `NotebookActivity` with the Uri directly.
- Recent files: store the Uri string (`content://...`) for picker-opened notebooks instead of a cache file path. When reopening from Recents, detect a `content://` prefix to route to the Uri-based open path vs. the existing plain-path (File-based) open path.
- Recent files display name: for Uri entries, use `DocumentFile.fromSingleUri(this, uri)?.name` instead of `path.substringAfterLast("/")`, since raw content Uris have opaque encoded path segments, not filenames.

### `NotebookActivity.kt`

- Add `EXTRA_URI` alongside the existing `EXTRA_FILE_PATH`, and a `currentUri: Uri?` field alongside `currentFile: File?` — mutually exclusive, mirroring the existing pattern (no new sealed class/interface).
- Load: if launched with a Uri, read via `contentResolver.openInputStream(uri)` and `NotebookFile.read(text)`.
- Save: if `currentUri` is set, write via `contentResolver.openOutputStream(uri, "wt")`. The `"wt"` mode is used deliberately — plain `"w"` truncation behavior is not guaranteed across all SAF document providers; `"wt"` is Android's documented way to request truncate-on-write.
- Title bar: for Uri-backed notebooks, use the DocumentFile display name instead of `File.name`.

## Error handling

Unchanged pattern from the existing table in README.md: I/O failures (including a Uri that's become invalid — file deleted or moved externally) show a Toast with the OS error message. No fallback to a cache copy.

## Explicitly out of scope

- SAF save picker for new/internally-created notebooks.
- Any persistent local caching of externally-opened notebook content.
- New abstraction types for the File/Uri duality — a second nullable field, matching the existing `currentFile: File?` pattern.
