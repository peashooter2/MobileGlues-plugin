package com.fcl.plugin.mobileglues.utils

import android.os.Environment

object Constants {
    @JvmField
    val MG_DIRECTORY: String = "${Environment.getExternalStorageDirectory().absolutePath}/MG"

    @JvmField
    val CONFIG_FILE_PATH: String = "$MG_DIRECTORY/config.json"

    @JvmField
    val GLSL_CACHE_FILE_PATH: String = "$MG_DIRECTORY/glsl_cache.tmp"
}

