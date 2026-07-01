package com.jupyterdroid.kernel

import com.chaquo.python.Python

data class ExecutionResult(val output: String, val error: String, val executionCount: Int)
data class PipResult(val stdout: String, val stderr: String, val success: Boolean)

class KernelManager private constructor() {
    private val runner = Python.getInstance().getModule("kernel_runner")

    fun execute(source: String): ExecutionResult {
        val result = runner.callAttr("execute", source)
        return ExecutionResult(
            output = result.callAttr("__getitem__", "output").toString(),
            error = result.callAttr("__getitem__", "error").toString(),
            executionCount = result.callAttr("__getitem__", "execution_count").toInt()
        )
    }

    fun pipInstall(packageName: String): PipResult {
        val result = runner.callAttr("pip_install", packageName)
        return PipResult(
            stdout = result.callAttr("__getitem__", "stdout").toString(),
            stderr = result.callAttr("__getitem__", "stderr").toString(),
            success = result.callAttr("__getitem__", "returncode").toInt() == 0
        )
    }

    fun reset() {
        runner.callAttr("reset")
    }

    companion object {
        @Volatile private var instance: KernelManager? = null

        fun getInstance(): KernelManager =
            instance ?: synchronized(this) {
                instance ?: KernelManager().also { instance = it }
            }
    }
}
