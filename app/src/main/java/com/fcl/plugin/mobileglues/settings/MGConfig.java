package com.fcl.plugin.mobileglues.settings;

import static com.fcl.plugin.mobileglues.MainActivity.MainActivityContext;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import com.fcl.plugin.mobileglues.MainActivity;
import com.fcl.plugin.mobileglues.utils.Constants;
import com.fcl.plugin.mobileglues.utils.FileUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import android.os.Environment;

public class MGConfig {
    private int enableANGLE;
    private int enableNoError;
    private int enableExtGL43;
    private int enableExtTimerQuery;
    private int enableExtComputeShader;
    private int enableExtDirectStateAccess;
    private int maxGlslCacheSize;
    private int multidrawMode;
    private int angleDepthClearFixMode;
    private int customGLVersion;

    public MGConfig(int enableANGLE, int enableNoError, int enableExtGL43, 
                    int enableExtTimerQuery, int enableExtComputeShader, int enableExtDirectStateAccess, 
                    int maxGlslCacheSize, int multidrawMode, int angleDepthClearFixMode, int customGLVersion) {
        this.enableANGLE = enableANGLE;
        this.enableNoError = enableNoError;
        this.enableExtGL43 = enableExtGL43;
        this.enableExtTimerQuery = enableExtTimerQuery;
        this.enableExtComputeShader = enableExtComputeShader;
        this.enableExtDirectStateAccess = enableExtDirectStateAccess;
        this.maxGlslCacheSize = maxGlslCacheSize;
        this.multidrawMode = multidrawMode;
        this.angleDepthClearFixMode = angleDepthClearFixMode;
        this.customGLVersion = customGLVersion;
    }

    public static MGConfig loadConfig(Context context) {
        String configStr;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (MainActivity.MGDirectoryUri == null) return null;
                Uri configUri = DocumentsContract.buildDocumentUriUsingTree(
                        MainActivity.MGDirectoryUri,
                        DocumentsContract.getTreeDocumentId(MainActivity.MGDirectoryUri) + "/config.json");
                configStr = FileUtils.readText(context, configUri);
            } else {
                File configFile = new File(Constants.CONFIG_FILE_PATH);
                if (!Files.exists(configFile.toPath())) return null;
                configStr = FileUtils.readText(configFile);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }

        MGConfig config = new Gson().fromJson(configStr, MGConfig.class);

        try {
            JsonObject obj = JsonParser.parseString(configStr).getAsJsonObject();
            if (!obj.has("enableExtTimerQuery")) {
                config.enableExtTimerQuery = 1;
            }
            if (!obj.has("enableExtDirectStateAccess")) {
                config.enableExtDirectStateAccess = 1;
            }
        } catch (Exception ignored) {
        }

        return config;
    }

    public int getEnableANGLE() {
        return enableANGLE;
    }

    public void setEnableANGLE(int enableANGLE) throws IOException {
        this.enableANGLE = enableANGLE;
        saveConfig();
    }

    public int getEnableNoError() {
        return enableNoError;
    }

    public void setEnableNoError(int enableNoError) throws IOException {
        this.enableNoError = enableNoError;
        saveConfig();
    }

    public int getEnableExtTimerQuery() {
        return enableExtTimerQuery;
    }

    public void setEnableExtTimerQuery(int enableExtTimerQuery) throws IOException {
        this.enableExtTimerQuery = enableExtTimerQuery;
        saveConfig();
    }
    
    public int getEnableExtDirectStateAccess() {
        return enableExtDirectStateAccess;
    }

    public void setEnableExtDirectStateAccess(int enableExtDirectStateAccess) throws IOException {
        this.enableExtDirectStateAccess = enableExtDirectStateAccess;
        saveConfig();
    }

    public int getEnableExtGL43() {
        return enableExtGL43;
    }

    public void setEnableExtGL43(int enableExtGL43) throws IOException {
        this.enableExtGL43 = enableExtGL43;
        saveConfig();
    }

    public int getEnableExtComputeShader() {
        return enableExtComputeShader;
    }

    public void setEnableExtComputeShader(int enableExtComputeShader) throws IOException {
        this.enableExtComputeShader = enableExtComputeShader;
        saveConfig();
    }

    public int getMaxGlslCacheSize() {
        return maxGlslCacheSize;
    }

    public void setMaxGlslCacheSize(int maxGlslCacheSize) throws IOException {
        if (maxGlslCacheSize < -1 || maxGlslCacheSize == 0)
            return;
        if (maxGlslCacheSize == -1)
            clearCacheFile();
        this.maxGlslCacheSize = maxGlslCacheSize;
        saveConfig();
    }

    public int getMultidrawMode() {
        return multidrawMode;
    }

    public void setMultidrawMode(int multidrawMode) throws IOException {
        this.multidrawMode = multidrawMode;
        saveConfig();
    }

    public int getAngleDepthClearFixMode() {
        return angleDepthClearFixMode;
    }

    public void setAngleDepthClearFixMode(int angleDepthClearFixMode) throws IOException {
        this.angleDepthClearFixMode = angleDepthClearFixMode;
        saveConfig();
    }

    public int getCustomGLVersion() {
        return customGLVersion;
    }

    public void setCustomGLVersion(int customGLVersion) throws IOException {
        this.customGLVersion = customGLVersion;
        saveConfig();
    }

    private void clearCacheFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri cacheUri = DocumentsContract.buildDocumentUriUsingTree(MainActivity.MGDirectoryUri,
                    DocumentsContract.getTreeDocumentId(MainActivity.MGDirectoryUri) + "/glsl_cache.tmp");
            try {
                DocumentsContract.deleteDocument(MainActivityContext.getContentResolver(), cacheUri);
            } catch (IOException | RuntimeException ignored) {
            }
        } else {
            try {
                FileUtils.deleteFile(new File(Constants.GLSL_CACHE_FILE_PATH));
            } catch (IOException ignored) {
            }
        }
    }

    public void saveConfig() throws IOException {
        save(MainActivityContext);
    }

    public void saveConfig(Context context) {
        try {
            save(context);
        } catch (RuntimeException | IOException e) {
            Log.e("MG", "Failed to save the config file: " + e.getMessage());
        }
    }

    private void save(Context context) throws IOException {
        String configStr = new Gson().toJson(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (MainActivity.MGDirectoryUri == null) {
                throw new IOException("SAF directory not selected");
            }
            FileUtils.writeText(context, MainActivity.MGDirectoryUri, "config.json", configStr, "application/json");
        } else {
            FileUtils.writeText(new File(Constants.CONFIG_FILE_PATH), configStr);
        }
    }
	
	public void deleteConfig(Context context) throws IOException {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			if (MainActivity.MGDirectoryUri != null) {
				FileUtils.deleteFileViaSAF(context, MainActivity.MGDirectoryUri, "config.json");
			}
		} else {
			File configFile = new File(Environment.getExternalStorageDirectory(), "MG/config.json");
			if (configFile.exists()) {
				FileUtils.deleteFile(configFile);
			}
		}
	}

}
