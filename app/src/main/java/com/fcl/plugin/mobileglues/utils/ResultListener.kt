package com.fcl.plugin.mobileglues.utils

import android.app.Activity
import android.content.Intent

object ResultListener {

    private var listener: Listener? = null

    @JvmStatic
    fun registerListener(listener: Listener) {
        this.listener = listener
    }

    @JvmStatic
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        listener?.onActivityResult(requestCode, resultCode, data)
        listener = null
    }

    @JvmStatic
    fun startActivityForResult(activity: Activity, intent: Intent, requestCode: Int, listener: Listener) {
        registerListener(listener)
        activity.startActivityForResult(intent, requestCode)
    }

    fun interface Listener {
        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    }
}
