package com.jupyterdroid

import android.app.Application
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.Python

class JupyterDroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
