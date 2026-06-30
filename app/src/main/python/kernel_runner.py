import sys
import io
import traceback
import subprocess

_execution_count = 0
_globals = {}


def execute(source):
    global _execution_count, _globals
    _execution_count += 1

    stdout_buf = io.StringIO()
    stderr_buf = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = stdout_buf, stderr_buf

    error = ""
    try:
        exec(compile(source, "<cell>", "exec"), _globals)
    except Exception:
        error = traceback.format_exc()
    finally:
        sys.stdout, sys.stderr = old_out, old_err

    return {
        "output": stdout_buf.getvalue(),
        "error": error,
        "execution_count": _execution_count,
    }


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
