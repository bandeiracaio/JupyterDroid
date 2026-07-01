# Bundled Titanic sample notebook

## Problem

New users have nothing to open on first launch — no notebook exists until they create or pick one. A bundled, runnable sample notebook gives them something to explore immediately, and doubles as a working example of what a notebook can do within the app's current capabilities.

## Constraints from current app state

- No rich outputs yet — only text stdout/stderr is rendered under a cell (per README's "What it cannot do (yet)"). The sample notebook's code must only produce text output.
- `pip install` works but installing pandas would require a working Chaquopy wheel for this device's ABI plus network access on first run — too fragile for a first-launch experience. The sample uses stdlib only (`csv`, `collections`).

## Data

The standard 891-row Titanic training dataset (columns: `PassengerId, Survived, Pclass, Name, Sex, Age, SibSp, Parch, Ticket, Fare, Cabin, Embarked`) — the same dataset used in countless tutorials, ~60KB as CSV.

Bundled as `app/src/main/python/titanic.csv`, alongside `kernel_runner.py`. Chaquopy packages everything under `src/main/python` into the app and extracts it to disk at runtime, so it's reachable via a plain file path from Python — no Android assets API needed from the Python side.

`kernel_runner.py` gets one new helper:

```python
def data_path(filename):
    return os.path.join(os.path.dirname(__file__), filename)
```

(`import os` added to the existing import block.)

## Notebook content

`sample_titanic.ipynb`, a real nbformat-4 file, bundled as an Android asset (`app/src/main/assets/sample_titanic.ipynb`). Alternating markdown (context) and code (stdlib-only, text-output-only) cells:

1. Markdown: title + one-paragraph intro to the dataset.
2. Code: load the CSV via `kernel_runner.data_path("titanic.csv")` and `csv.DictReader`, print row count.
3. Markdown: "Overall survival rate" header.
4. Code: compute and print overall survival rate.
5. Markdown: "By sex" header.
6. Code: survival rate grouped by `Sex`.
7. Markdown: "By passenger class" header.
8. Code: survival rate grouped by `Pclass`.
9. Markdown: "Age" header.
10. Code: average age of survivors vs. non-survivors (skipping rows with a blank `Age`).

## Seeding into the app

On `MainActivity`'s first-ever launch — a dedicated one-time `SharedPreferences` boolean flag (`sample_notebook_seeded`), distinct from "Recents is currently empty" so it never re-appears after the user deletes it — copy the bundled asset into `getExternalFilesDir(null)/sample_titanic_analysis.ipynb` and add that path to Recents via the existing `saveRecent` mechanism.

From that point on it behaves as an ordinary locally-created notebook: same File-based read/write path as "New notebook," fully editable, deletable, and re-runnable like any other local file. No new Uri/File-duality plumbing.

## Error handling

If the one-time asset copy fails (e.g. storage full), it fails silently and simply doesn't seed a Recents entry — this is a nice-to-have onboarding aid, not a required feature, so a failure here must not block or disrupt normal app startup. No toast, no retry.

## Explicitly out of scope

- No pandas, no matplotlib, no rich outputs — deferred until the app supports them (tracked separately in the README roadmap).
- No UI entry point (button, menu item) to re-seed or re-download the sample after first launch — if the user deletes it, it's gone, same as any other user-deleted notebook.
- No network fetch at runtime — the CSV is bundled, not downloaded.
