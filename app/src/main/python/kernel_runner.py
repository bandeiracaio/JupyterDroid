import sys
import io
import os
import traceback
import subprocess
import ctypes
import threading

_execution_count = 0
_globals = {}
_exec_thread_id = None


def data_path(filename):
    return os.path.join(os.path.dirname(__file__), filename)


def execute(source):
    global _execution_count, _globals, _exec_thread_id
    _execution_count += 1
    _exec_thread_id = threading.get_ident()

    stdout_buf = io.StringIO()
    stderr_buf = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = stdout_buf, stderr_buf

    error = ""
    try:
        exec(compile(source, "<cell>", "exec"), _globals)
    except BaseException:  # BaseException so KeyboardInterrupt lands here, not in the bridge
        error = traceback.format_exc()
    finally:
        _exec_thread_id = None
        sys.stdout, sys.stderr = old_out, old_err

    return {
        "output": stdout_buf.getvalue(),
        "error": error,
        "execution_count": _execution_count,
    }


def interrupt():
    # ponytail: PyThreadState_SetAsyncExc delivers between Python bytecodes.
    # A blocking C call (time.sleep, native pandas op) only sees it on return.
    # Kernel reset remains the hard stop.
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
