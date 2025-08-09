package com.fcl.plugin.mobileglues

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppInfoDialogBuilder(context: Context) : MaterialAlertDialogBuilder(context) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_info, null)
        view.findViewById<TextView>(R.id.info_version).text = BuildConfig.VERSION_NAME

        setTitle(R.string.dialog_info)
        setView(view)
        setPositiveButton(R.string.dialog_positive, null)
        setNeutralButton(R.string.dialog_github) { _, _ ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MobileGL-Dev/MobileGlues-release"))
            )
        }
    }
}
