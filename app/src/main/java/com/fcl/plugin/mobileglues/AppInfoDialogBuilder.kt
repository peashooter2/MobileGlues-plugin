package com.fcl.plugin.mobileglues

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.net.toUri
import com.fcl.plugin.mobileglues.settings.MGConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@SuppressLint("InflateParams")
class AppInfoDialogBuilder(private val context: Context, private val config: MGConfig?) : MaterialAlertDialogBuilder(context) {

    fun showDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_info, null, false)
        view.findViewById<TextView>(R.id.info_version).text = BuildConfig.VERSION_NAME

        setTitle(R.string.dialog_info)
        setView(view)

        setNeutralButton(R.string.dialog_positive, null)
        setNegativeButton(R.string.dialog_sponsor) { _, _ ->
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://www.buymeacoffee.com/Swung0x48".toUri()
                )
            )
        }
        setPositiveButton(R.string.dialog_github) { _, _ ->
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/MobileGL-Dev/MobileGlues-release".toUri()
                )
            )
        }

        val dialog = this.create()

        dialog.setOnShowListener {
            val glInfoButton = dialog.findViewById<MaterialButton>(R.id.button_gl_info)
            glInfoButton?.setOnClickListener {
                val mgGLInfoDialog = MGGLInfoDialogBuilder(context, config)
                mgGLInfoDialog.show()
                mgGLInfoDialog.fillMobileGluesInfo()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
