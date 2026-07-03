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

print("ALL PASS")
