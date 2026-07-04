package com.jupyterdroid.util

/** Turns a failed pip result into a message that explains *why*, instead of a bare
 *  "Failed to install X". Pure (no Android deps) so it can be unit-tested. */
object PipMessages {

    private const val MAX_STDERR = 600

    // On-device pip can't compile native extensions; these strings appear when a
    // package tries to build one.
    private val NATIVE_BUILD_SIGNS = listOf(
        "failed building wheel",
        "failed to build",
        "could not build wheels",
        "error: command",
        "clang",
        "gcc",
    )

    fun failure(pkg: String, stdout: String, stderr: String): String {
        val haystack = (stdout + "\n" + stderr).lowercase()
        return when {
            NATIVE_BUILD_SIGNS.any { it in haystack } ->
                "Couldn't install '$pkg': it needs native code compiled on-device, " +
                    "which isn't supported here. Only pure-Python packages install."
            "no matching distribution found" in haystack ->
                "No installable '$pkg' found — check the name, or it may have no " +
                    "pure-Python build for this platform."
            stderr.isNotBlank() -> stderr.trim().takeLast(MAX_STDERR)
            else -> "Failed to install '$pkg'."
        }
    }
}
