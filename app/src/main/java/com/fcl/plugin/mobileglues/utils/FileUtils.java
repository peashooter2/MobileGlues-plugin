package com.fcl.plugin.mobileglues.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import java.util.Objects;
import androidx.documentfile.provider.DocumentFile;
import android.provider.DocumentsContract;

import com.fcl.plugin.mobileglues.MainActivity;

public class FileUtils {
    public static String readText(Context context, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString(UTF_8.name());
        }
    }

    public static void writeText(Context context, Uri directoryUri, String fileName, String text, String mimeType) throws IOException {
        final ContentResolver resolver = context.getContentResolver();
        final String baseDocId = DocumentsContract.getTreeDocumentId(directoryUri);
        final Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, baseDocId + "/" + fileName);

        try (OutputStream out = resolver.openOutputStream(fileUri, "wt");
            BufferedOutputStream bufferedOut = new BufferedOutputStream(out)) {
            bufferedOut.write(text.getBytes(StandardCharsets.UTF_8));
            return;
        } catch (IOException | RuntimeException e) {
//            e.printStackTrace();
        }

        Uri parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                directoryUri, DocumentsContract.getTreeDocumentId(directoryUri)
        );

        Uri newFileUri = DocumentsContract.createDocument(
                resolver,
                parentDocumentUri,
                mimeType,
                fileName);

        if (newFileUri == null) {
            throw new IOException("Failed to create document: " + fileName);
        }

        try (OutputStream out = resolver.openOutputStream(newFileUri, "wt");
             BufferedOutputStream bufferedOut = new BufferedOutputStream(out)) {
            bufferedOut.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void writeText(File file, String text) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            throw new UnsupportedOperationException("Use SAF method for Android 10+");
        }
        writeText(file, text, UTF_8);
    }

    public static String readText(File file) throws IOException {
        return readText(file, UTF_8);
    }

    public static String readText(File file, Charset charset) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), charset);
    }

    public static void writeText(File file, String text, Charset charset) throws IOException {
        writeBytes(file, text.getBytes(charset));
    }

    public static void writeBytes(File file, byte[] data) throws IOException {
        writeBytes(file.toPath(), data);
    }

    public static void writeBytes(Path file, byte[] data) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, data);
    }

    public static void deleteFile(File file) throws IOException {
        if (file.isDirectory()) {
            for (File subFile : Objects.requireNonNull(file.listFiles())) {
                deleteFile(subFile);
            }
        }
        Files.delete(file.toPath());
    }
	
	private void deleteAppFiles() {
		Uri mgDirUri = MainActivity.MGDirectoryUri;
		File mgDir = new File(Environment.getExternalStorageDirectory(), "MG");
		File config = new File(mgDir, "config.json");
		File cache = new File(mgDir, "glsl_cache.tmp");
		File log   = new File(mgDir, "latest.log");
		try {
			if (config.exists()) config.delete();
			if (cache .exists()) cache .delete();
			if (log   .exists()) log   .delete();
			if (mgDir.isDirectory() && mgDir.list().length == 0) {
				mgDir.delete();
			}
		} catch (Exception e) {
			
		}
	}
	

	public static void deleteFileViaSAF(Context context, Uri directoryUri, String fileName) {
		try {
			DocumentFile dir = DocumentFile.fromTreeUri(context, directoryUri);
			if (dir != null) {
				DocumentFile file = dir.findFile(fileName);
				if (file != null && file.exists()) {
					DocumentsContract.deleteDocument(context.getContentResolver(), file.getUri());
				}
			}
		} catch (Exception e) {
			
		}
	}
}
