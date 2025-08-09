package com.fcl.plugin.mobileglues.settings

import android.content.ContentResolver
import android.content.Context
import android.content.UriPermission
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.fcl.plugin.mobileglues.utils.Constants
import java.io.File

class FolderPermissionManager(private val context: Context) {

    /**
     * @return Obtain the list of Uris that have been granted the read/write permission
     */
    fun getGrantedFolderUris(): List<Uri> {
        val uriList = mutableListOf<Uri>()
        val contentResolver: ContentResolver = context.contentResolver

        for (permission: UriPermission in contentResolver.persistedUriPermissions) {
            if (permission.isReadPermission && permission.isWritePermission) {
                uriList.add(permission.uri)
            }
        }
        return uriList
    }

    fun getFileByUri(uri: Uri): File? {
        if (uri.authority != "com.android.externalstorage.documents") return null

        val docId = if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.getTreeDocumentId(uri)
        } else {
            DocumentsContract.getDocumentId(uri)
        }

        val split = docId.split(":")
        if (split.size < 2) return null

        val type = split[0]
        val relativePath = split[1]

        val baseDir = if (type.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            return null
        }

        return File(baseDir, relativePath)
    }

    fun isUriMatchingFilePath(uri: Uri, file: File): Boolean {
        val expectedFile = getFileByUri(uri) ?: return false
        return expectedFile.absolutePath == file.absolutePath
    }

    fun getMGFolderUri(): Uri? {
        val grantedFolderUris = getGrantedFolderUris()
        val mgFolder = File(Constants.MG_DIRECTORY)

        for (uri in grantedFolderUris) {
            if (isUriMatchingFilePath(uri, mgFolder)) {
                return uri
            }
        }

        return null
    }
}
