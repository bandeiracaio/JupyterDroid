import os
import sys
import threading
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "main", "python"))
import kernel_runner

# 1. Interrupt while idle is a no-op returning False.
assert kernel_runner.interrupt() is False

# 2. Interrupt breaks an infinite loop; error is a KeyboardInterrupt traceback.
result = {}
t = threading.Thread(target=lambda: result.update(kernel_runner.execute("while True:\n    pass")))
t.start()
time.sleep(0.3)  # let exec() enter the loop
assert kernel_runner.interrupt() is True
t.join(5)
assert not t.is_alive(), "cell thread did not stop within 5s"
assert "KeyboardInterrupt" in result["error"], result["error"]
assert result["output"] == ""

# 3. Kernel survives: globals persist across the interrupt, next execute works.
kernel_runner.execute("x = 41")
r = kernel_runner.execute("print(x + 1)")
assert r["error"] == "", r["error"]
assert r["output"].strip() == "42"

# 4. Thread id cleared after execute: a second idle interrupt is False again.
assert kernel_runner.interrupt() is False

# 5. Stale-tid race: hammer interrupt() concurrently with fast execute() calls
# on a reused worker thread. No result may error, and no exception may escape
# execute() itself; a final clean execute afterwards must be unaffected.
results = []


def racer():
    for _ in range(200):
        results.append(kernel_runner.execute("pass"))


t2 = threading.Thread(target=racer)
t2.start()
for _ in range(200):
    kernel_runner.interrupt()
t2.join(5)
assert not t2.is_alive(), "racer thread did not finish within 5s"
assert len(results) == 200
for r in results:
    assert isinstance(r, dict), "execute() must always return a dict, never raise"
    assert "KeyboardInterrupt" not in r["error"], r["error"]

clean = kernel_runner.execute("y = 2")
assert clean["error"] == "", clean["error"]
final = kernel_runner.execute("print(y)")
assert final["error"] == "", final["error"]
assert final["output"].strip() == "2"

# 5. Expression echo: last bare expression is repr()'d like Jupyter.
r = kernel_runner.execute("1 + 1")
assert r["output"] == "2\n", r["output"]
assert r["error"] == ""
assert r["images"] == []

r = kernel_runner.execute("z = 5")          # assignment: no echo
assert r["output"] == "", r["output"]

r = kernel_runner.execute("None")           # None: no echo
assert r["output"] == "", r["output"]

r = kernel_runner.execute("print('a')\n3")  # stdout first, then echo
assert r["output"] == "a\n3\n", r["output"]

r = kernel_runner.execute("'café'")    # unicode repr survives
assert r["output"] == "'café'\n", r["output"]

r = kernel_runner.execute("def f(:")        # syntax error unchanged
assert "SyntaxError" in r["error"], r["error"]
assert r["images"] == []

r = kernel_runner.execute("z + 1")          # echo uses kernel globals
assert r["output"] == "6\n", r["output"]

# 6. Figure sweep fast path: host has no matplotlib.
import sys as _sys
assert "matplotlib" not in _sys.modules
r = kernel_runner.execute("40 + 2")
assert r["images"] == []

# 7. Agg backend forced before any user code can import matplotlib.
import os as _os
assert _os.environ.get("MPLBACKEND") == "agg"

print("ALL PASS")
