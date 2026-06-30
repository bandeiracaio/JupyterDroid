package com.jupyterdroid.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jupyterdroid.R
import com.jupyterdroid.kernel.KernelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PipInstallBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_pip, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val packageEdit = view.findViewById<EditText>(R.id.packageNameEdit)
        val outputText = view.findViewById<TextView>(R.id.pipOutputText)
        val installButton = view.findViewById<Button>(R.id.installButton)
        val km = KernelManager.getInstance()

        installButton.setOnClickListener {
            val pkg = packageEdit.text.toString().trim()
            if (pkg.isEmpty()) return@setOnClickListener

            installButton.isEnabled = false
            outputText.text = "Installing $pkg…"

            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { km.pipInstall(pkg) }
                outputText.text = when {
                    result.success -> result.stdout.ifEmpty { "Installed $pkg" }
                    else -> result.stderr.ifEmpty { "Failed to install $pkg" }
                }
                installButton.isEnabled = true
            }
        }
    }

    companion object {
        const val TAG = "PipInstallBottomSheet"
    }
}
