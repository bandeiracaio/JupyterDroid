import sys
import io
import os
import traceback
import subprocess
import ctypes
import threading
import ast
import base64

# Must be set before user code first imports matplotlib.
os.environ.setdefault("MPLBACKEND", "agg")

_execution_count = 0
_globals = {}
_exec_thread_id = None


def data_path(filename):
    return os.path.join(os.path.dirname(__file__), filename)


def _exec_with_echo(source):
    """Run source in _globals; if the last top-level statement is a bare
    expression, evaluate it and return its repr (Jupyter echo). Returns ""
    when there is nothing to echo."""
    tree = ast.parse(source, "<cell>")
    if tree.body and isinstance(tree.body[-1], ast.Expr):
        body = ast.Module(body=tree.body[:-1], type_ignores=[])
        exec(compile(body, "<cell>", "exec"), _globals)
        value = eval(compile(ast.Expression(tree.body[-1].value), "<cell>", "eval"), _globals)
        if value is not None:
            return repr(value) + "\n"
        return ""
    exec(compile(source, "<cell>", "exec"), _globals)
    return ""


def _capture_figures():
    """Sweep all open matplotlib figures into base64 PNGs and close them.
    No-op (and no import) when matplotlib was never loaded."""
    if "matplotlib" not in sys.modules:
        return []
    import matplotlib.pyplot as plt
    images = []
    for num in plt.get_fignums():
        buf = io.BytesIO()
        plt.figure(num).savefig(buf, format="png")
        images.append(base64.b64encode(buf.getvalue()).decode("ascii"))
    plt.close("all")
    return images


def execute(source):
    global _execution_count, _globals, _exec_thread_id
    _execution_count += 1
    _exec_thread_id = threading.get_ident()

    stdout_buf = io.StringIO()
    stderr_buf = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr

    try:
        sys.stdout, sys.stderr = stdout_buf, stderr_buf
        error = ""
        echo = ""
        images = []
        try:
            echo = _exec_with_echo(source)
        except BaseException:  # BaseException so KeyboardInterrupt lands here, not in the bridge
            error = traceback.format_exc()
        # Sweep even after an error so leftover figures never leak into the
        # next cell's result. A sweep failure becomes the cell's error.
        try:
            images = _capture_figures()
        except BaseException:
            images = []
            if not error:
                error = traceback.format_exc()
        return {
            "output": stdout_buf.getvalue() + echo,
            "error": error,
            "execution_count": _execution_count,
            "images": images,
        }
    except BaseException:
        # A stale interrupt delivered here (after the inner try, before we return)
        # still needs to come back as a normal result, not escape to the bridge.
        return {
            "output": stdout_buf.getvalue(),
            "error": traceback.format_exc(),
            "execution_count": _execution_count,
            "images": [],
        }
    finally:
        sys.stdout, sys.stderr = old_out, old_err
        _exec_thread_id = None
        # ponytail: NULL cancels any async exc still queued on this (reused) pool
        # thread so it can't fire during the next execute() on this same thread.
        ctypes.pythonapi.PyThreadState_SetAsyncExc(
            ctypes.c_ulong(threading.get_ident()), None)


def interrupt():
    # ponytail: PyThreadState_SetAsyncExc delivers between Python bytecodes.
    # A blocking C call (time.sleep, native pandas op) only sees it on return.
    # Kernel reset remains the hard stop.
    # Residual race: tid is read, then the cell can finish and the pool thread
    # go idle, before the injection call below runs. execute()'s finally drains
    # any such stale async exc so it can't leak into the NEXT execute() on that
    # thread — but a few bytecodes between the drain and returning to Java are
    # still exposed if a new execute() starts in that exact window.
    tid = _exec_thread_id
    if tid is None:
        return False
    ctypes.pythonapi.PyThreadState_SetAsyncExc(
        ctypes.c_ulong(tid), ctypes.py_object(KeyboardInterrupt))
    return True


def pip_install(package):
    result = subprocess.run(
        [sys.executable, "-m", "pip", "install", package],
        capture_output=True,
        text=True,
    )
    return {
        "stdout": result.stdout,
        "stderr": result.stderr,
        "returncode": result.returncode,
    }


def reset():
    global _execution_count, _globals
    _execution_count = 0
    _globals = {}
