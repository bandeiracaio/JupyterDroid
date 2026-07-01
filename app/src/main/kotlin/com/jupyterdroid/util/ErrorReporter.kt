package com.jupyterdroid.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.jupyterdroid.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorReporter {

    fun format(
        action: String,
        message: String,
        stackTrace: String?,
        extra: String?,
        appVersion: String
    ): String = buildString {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("[$timestamp] $action")
        appendLine("App: JupyterDroid v$appVersion")
        appendLine("Message: $message")
        if (!stackTrace.isNullOrEmpty()) {
            appendLine("Stack trace:")
            appendLine(stackTrace)
        }
        if (!extra.isNullOrEmpty()) {
            appendLine("Cell source:")
            appendLine(extra)
        }
    }.trimEnd()

    fun copyFromThrowable(context: Context, action: String, throwable: Throwable, extra: String? = null) {
        copyToClipboard(
            context,
            format(
                action = action,
                message = throwable.message ?: throwable.toString(),
                stackTrace = Log.getStackTraceString(throwable),
                extra = extra,
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            )
        )
    }

    fun copyFromText(context: Context, action: String, errorText: String, extra: String? = null) {
        copyToClipboard(
            context,
            format(
                action = action,
                message = errorText,
                stackTrace = null,
                extra = extra,
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            )
        )
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("JupyterDroid error", text))
    }
}
