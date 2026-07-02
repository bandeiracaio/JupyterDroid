# Cell Delete & Reorder Design

First of four queued parity features (then: syntax highlighting, kernel interrupt, rich outputs).

## Goal

Cells in the notebook editor can be deleted and reordered — currently they can only be added at the bottom.

## Interaction

- Each cell shows a slim, right-aligned action row: **↑ ↓ ⠿ (drag handle) 🗑** — one shared layout include used by both `item_code_cell` and `item_markdown_cell`.
- One `ItemTouchHelper` attached to the RecyclerView in `NotebookActivity`:
  - Swipe left/right on a cell → delete.
  - Drag to reorder, started **from the drag handle only** (`itemTouchHelper.startDrag(holder)` on handle `ACTION_DOWN`). Long-press drag is disabled (`isLongPressDragEnabled = false`) because long-press inside a cell's EditText means text selection.
- ↑ / ↓ buttons move the cell one position; 🗑 deletes. Buttons duplicate the gestures for discoverability (explicit user choice: both).

## Adapter changes (`NotebookAdapter`)

- `moveCell(from: Int, to: Int)` — reorder `cells`, `notifyItemMoved(from, to)`.
- `deleteCell(pos: Int): Cell` — remove from `cells`, `notifyItemRemoved(pos)`, return the removed cell.
- `restoreCell(pos: Int, cell: Cell)` — reinsert, `notifyItemInserted(pos)` (Undo path).

## Stale-position fixes

The existing code captures `position` at bind time and after async execution; both go stale once cells can move or disappear:

- ViewHolder callbacks (`onRunCell`, `updateSource`, new action buttons) switch from bind-time `position` to `bindingAdapterPosition`, ignoring `RecyclerView.NO_POSITION`.
- `updateCellOutput` changes from position-based to identity-based: the activity passes the `Cell.Code` object it executed; the adapter locates it with `cells.indexOf(cell)` and updates that index (no-op if the cell was deleted mid-run).

## Delete flow

Swipe or 🗑 → `deleteCell` → Snackbar "Cell deleted" with **Undo** action that calls `restoreCell` at the old position. No confirmation dialog.

## Persistence

None needed. `adapter.cells` is the single source of truth already serialized by `save()` / auto-save on pause; reorders and deletes persist through the existing path.

## Testing

- JVM unit tests for `moveCell` / `deleteCell` / `restoreCell` list semantics (order, bounds, returned cell).
- Gestures, buttons, and Undo verified manually on the `JupyterDroid_test` emulator.

## Out of scope

- Multi-select / bulk delete.
- Insert-at-position (cells still add at the bottom).
- Cut/copy/paste cells.
