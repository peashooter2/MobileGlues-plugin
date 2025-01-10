package com.fcl.plugin.mobileglues.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object FileUtils {

    @Throws(IOException::class)
    fun readText(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri).use { input ->
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var length: Int
            while (input?.read(buffer).also { length = it ?: -1 } != -1) {
                result.write(buffer, 0, length)
            }
            return result.toString(StandardCharsets.UTF_8.name())
        }
    }

    @Throws(IOException::class)
    fun writeText(
        context: Context,
        directoryUri: Uri,
        fileName: String,
        text: String,
        mimeType: String
    ) {
        val resolver = context.contentResolver
        val baseDocId = DocumentsContract.getTreeDocumentId(directoryUri)
        val fileUri =
            DocumentsContract.buildDocumentUriUsingTree(directoryUri, "$baseDocId/$fileName")

        try {
            resolver.openOutputStream(fileUri, "wt")?.use { out ->
                BufferedOutputStream(out).use { bufferedOut ->
                    bufferedOut.write(text.toByteArray(StandardCharsets.UTF_8))
                    return
                }
            }
        } catch (_: IOException) {
            // handle below
        } catch (_: RuntimeException) {
            // handle below
        }

        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            directoryUri,
            DocumentsContract.getTreeDocumentId(directoryUri)
        )

        val newFileUri =
            DocumentsContract.createDocument(resolver, parentDocumentUri, mimeType, fileName)
                ?: throw IOException("Failed to create document: $fileName")

        resolver.openOutputStream(newFileUri, "wt")?.use { out ->
            BufferedOutputStream(out).use { bufferedOut ->
                bufferedOut.write(text.toByteArray(StandardCharsets.UTF_8))
            }
        }
    }

    @Throws(IOException::class)
    fun writeText(file: File, text: String) {
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        //     throw UnsupportedOperationException("Use SAF method for Android 10+")
        // }
        writeText(file, text, StandardCharsets.UTF_8)
    }

    @Throws(IOException::class)
    fun readText(file: File): String = readText(file, StandardCharsets.UTF_8)

    @Throws(IOException::class)
    fun readText(file: File, charset: Charset): String {
        return String(Files.readAllBytes(file.toPath()), charset)
    }

    @Throws(IOException::class)
    fun writeText(file: File, text: String, charset: Charset) {
        writeBytes(file, text.toByteArray(charset))
    }

    @Throws(IOException::class)
    fun writeBytes(file: File, data: ByteArray) {
        writeBytes(file.toPath(), data)
    }

    @Throws(IOException::class)
    fun writeBytes(path: Path, data: ByteArray) {
        Files.createDirectories(path.parent)
        Files.write(path, data)
    }

    @Throws(IOException::class)
    fun deleteFile(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                deleteFile(it)
            }
        }
        Files.delete(file.toPath())
    }

    fun deleteAppFiles() {
        val mgDir = File(Environment.getExternalStorageDirectory(), "MG")
        val config = File(mgDir, "config.json")
        val cache = File(mgDir, "glsl_cache.tmp")
        val log = File(mgDir, "latest.log")

        try {
            if (config.exists()) config.delete()
            if (cache.exists()) cache.delete()
            if (log.exists()) log.delete()
            if (mgDir.isDirectory && mgDir.list()?.isEmpty() == true) {
                mgDir.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun deleteFileViaSAF(context: Context, directoryUri: Uri, fileName: String) {
        try {
            val dir = DocumentFile.fromTreeUri(context, directoryUri)
            val file = dir?.findFile(fileName)
            if (file != null && file.exists()) {
                DocumentsContract.deleteDocument(context.contentResolver, file.uri)
            }
        } catch (_: Exception) {
        }
    }
}
