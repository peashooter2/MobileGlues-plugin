package com.fcl.plugin.mobileglues;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static java.sql.Types.NULL;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fcl.plugin.mobileglues.databinding.ActivityMainBinding;
import com.fcl.plugin.mobileglues.settings.FolderPermissionManager;
import com.fcl.plugin.mobileglues.settings.MGConfig;
import com.fcl.plugin.mobileglues.utils.Constants;
import com.fcl.plugin.mobileglues.utils.ResultListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import androidx.documentfile.provider.DocumentFile;
import com.fcl.plugin.mobileglues.utils.FileUtils;

import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import java.util.List;
import android.content.UriPermission;
import java.util.Objects;

import android.content.DialogInterface;
import android.os.CountDownTimer;
import android.os.Message;
import android.widget.Button;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.StringRes;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener {
    private static final int REQUEST_CODE_SAF = 2000;
    public static Uri MGDirectoryUri;
    public static Context MainActivityContext;
    private ActivityMainBinding binding;
    private MGConfig config = null;
    private FolderPermissionManager folderPermissionManager;
    private boolean isSpinnerInitialized = false;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        folderPermissionManager = new FolderPermissionManager(this);
        MainActivityContext = this;
        setSupportActionBar(binding.appBar);

        ArrayList<String> angleOptions = new ArrayList<>();
        angleOptions.add(getString(R.string.option_angle_disable_if_possible));
        angleOptions.add(getString(R.string.option_angle_enable_if_possible));
        angleOptions.add(getString(R.string.option_angle_disable));
        angleOptions.add(getString(R.string.option_angle_enable));
        ArrayAdapter<String> angleAdapter = new ArrayAdapter<>(this, R.layout.spinner, angleOptions);
        binding.spinnerAngle.setAdapter(angleAdapter);

        ArrayList<String> noErrorOptions = new ArrayList<>();
        noErrorOptions.add(getString(R.string.option_no_error_auto));
        noErrorOptions.add(getString(R.string.option_no_error_enable));
        noErrorOptions.add(getString(R.string.option_no_error_disable_pri));
        noErrorOptions.add(getString(R.string.option_no_error_disable_sec));
        ArrayAdapter<String> noErrorAdapter = new ArrayAdapter<>(this, R.layout.spinner, noErrorOptions);
        binding.spinnerNoError.setAdapter(noErrorAdapter);

        ArrayList<String> multidrawModeOptions = new ArrayList<>();
        multidrawModeOptions.add(getString(R.string.option_multidraw_mode_auto));
        multidrawModeOptions.add(getString(R.string.option_multidraw_mode_indirect));
        multidrawModeOptions.add(getString(R.string.option_multidraw_mode_basevertex));
        multidrawModeOptions.add(getString(R.string.option_multidraw_mode_multidraw_indirect));
        multidrawModeOptions.add(getString(R.string.option_multidraw_mode_drawelements));
        multidrawModeOptions.add(getString(R.string.option_multidraw_mode_compute));
        ArrayAdapter<String> multidrawModeAdapter = new ArrayAdapter<>(this, R.layout.spinner, multidrawModeOptions);
        binding.spinnerMultidrawMode.setAdapter(multidrawModeAdapter);

        ArrayList<String> angleClearWorkaroundOptions = new ArrayList<>();
        angleClearWorkaroundOptions.add(getString(R.string.option_angle_clear_workaround_disable));
        angleClearWorkaroundOptions.add(getString(R.string.option_angle_clear_workaround_enable_1));
        ArrayAdapter<String> angleClearWorkaroundAdapter = new ArrayAdapter<>(this, R.layout.spinner, angleClearWorkaroundOptions);
        binding.angleClearWorkaround.setAdapter(angleClearWorkaroundAdapter);

        binding.openOptions.setOnClickListener(view -> checkPermission());
    }
	
	private boolean hasMgDirectoryAccess() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return MGDirectoryUri != null;
    } else {
        return ActivityCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
}

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissionSilently();
		invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		
		MenuItem removeItem = menu.findItem(R.id.action_remove);
		if (removeItem != null) {
			removeItem.setEnabled(hasMgDirectoryAccess());
		}
		
		return true;
	}

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            new AppInfoDialogBuilder(MainActivityContext).show();
            return true;
        } else if (item.getItemId() == R.id.action_remove) { 
            showRemoveConfirmationDialog();
            return true;
        } else return super.onOptionsItemSelected(item);
    }
	
	private void showRemoveConfirmationDialog() {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
	
		builder.setTitle(R.string.remove_mg_files_title)
			.setMessage(R.string.remove_mg_files_message)
			.setNegativeButton(R.string.dialog_negative, null);
	
		androidx.appcompat.app.AlertDialog dialog = builder.create();
	
		final int cooldownSeconds = 10;
		final int[] remainingSeconds = {cooldownSeconds};
	
		dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok), (dialogInterface, which) -> {
			removeMobileGluesCompletely();
		});
	
		dialog.setOnShowListener(dialogInterface -> {
			Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
			
			positiveButton.setText(getString(R.string.ok_with_countdown, remainingSeconds[0]));
			positiveButton.setEnabled(false);
			
			new CountDownTimer(cooldownSeconds * 1000, 1000) {
				public void onTick(long millisUntilFinished) {
					remainingSeconds[0] = (int) (millisUntilFinished / 1000);
					positiveButton.setText(getString(R.string.ok_with_countdown, remainingSeconds[0]));
				}
	
				public void onFinish() {
					positiveButton.setText(R.string.ok);
					positiveButton.setTextColor(ContextCompat.getColor(MainActivityContext, android.R.color.holo_red_dark));
					positiveButton.setEnabled(true);
				}
			}.start();
		});
	
		dialog.show();
	}	

    private void removeMobileGluesCompletely() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        );
        View view = LayoutInflater.from(this)
                    .inflate(R.layout.progress_dialog_md3, null);
        ProgressBar progressBar = view.findViewById(R.id.progress_bar);
        TextView  progressText = view.findViewById(R.id.progress_text);
        builder.setTitle(R.string.removing_mobileglues)
               .setView(view)
               .setCancelable(false);

        final androidx.appcompat.app.AlertDialog progressDialog = builder.create();
        runOnUiThread(progressDialog::show);

        new Thread(() -> {
            try {
                final int[] step = {0};

                runOnUiThread(() -> {
                    progressText.setText(R.string.deleting_config);
                    progressBar.setProgress((step[0] + 1) * 20);
                });
                if (config != null) {
                    config.deleteConfig(MainActivity.this);
                }

                step[0]++;
                runOnUiThread(() -> {
                    progressText.setText(R.string.deleting_cache);
                    progressBar.setProgress((step[0] + 1) * 20);
                });
                deleteFileIfExists("glsl_cache.tmp");

                step[0]++;
                runOnUiThread(() -> {
                    progressText.setText(R.string.deleting_logs);
                    progressBar.setProgress((step[0] + 1) * 20);
                });
                deleteFileIfExists("latest.log");

                step[0]++;
                runOnUiThread(() -> {
                    progressText.setText(R.string.cleaning_directory);
                    progressBar.setProgress((step[0] + 1) * 20);
                });
                checkAndDeleteEmptyDirectory();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && MGDirectoryUri != null) {
                    step[0]++;
                    runOnUiThread(() -> {
                        progressText.setText(R.string.removing_permissions);
                        progressBar.setProgress((step[0] + 1) * 20);
                    });
                    releaseSafPermissions();
                }

                runOnUiThread(() -> {
					resetApplicationState();
                    progressDialog.dismiss();
                    showFinalDialog();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(
                        MainActivity.this,
                        getString(R.string.remove_failed, e.getMessage()),
                        Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

	
	private void showFinalDialog() {
		new MaterialAlertDialogBuilder(this)
				.setTitle(R.string.remove_complete_title)
				.setMessage(R.string.remove_complete_message)
				.setCancelable(false)
				.setPositiveButton(R.string.exit, (dialog, which) -> {
					finishAffinity();
					System.exit(0);
				})
            .show();
	}

    private void deleteFileIfExists(String fileName) {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				if (MGDirectoryUri != null) {
					DocumentFile dir = DocumentFile.fromTreeUri(this, MGDirectoryUri);
					if (dir != null) {
						DocumentFile file = dir.findFile(fileName);
						if (file != null && file.exists()) {
							DocumentsContract.deleteDocument(getContentResolver(), file.getUri());
						}
					}
				}
			} else {
				File file = new File(Environment.getExternalStorageDirectory(), "MG/" + fileName);
				if (file.exists()) {
					FileUtils.deleteFile(file);
				}
			}
		} catch (Exception e) {
			Log.w("MG", "删除文件失败: " + fileName, e);
		}
	}	

    private void checkAndDeleteEmptyDirectory() {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				if (MGDirectoryUri != null) {
					DocumentFile dir = DocumentFile.fromTreeUri(this, MGDirectoryUri);
					if (dir != null && dir.listFiles().length == 0) {
						DocumentsContract.deleteDocument(getContentResolver(), dir.getUri());
					}
				}
			} else {
				File mgDir = new File(Environment.getExternalStorageDirectory(), "MG");
				if (mgDir.exists() && mgDir.isDirectory()) {
					File[] files = mgDir.listFiles();
					if (files != null && files.length == 0) {
						FileUtils.deleteFile(mgDir);
					}
				}
			}
		} catch (Exception e) {
			Log.w("MG", "删除目录失败", e);
		}
	}

    private void releaseSafPermissions() {
        try {
            List<UriPermission> permissions = getContentResolver().getPersistedUriPermissions();
            for (UriPermission permission : permissions) {
                if (permission.getUri().equals(MGDirectoryUri)) {
                    getContentResolver().releasePersistableUriPermission(
                        MGDirectoryUri, 
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                }
            }
        } catch (Exception e) {
            Logger.getLogger("MG").log(Level.WARNING, "移除 SAF 权限失败", e);
        }
    }

    private void resetApplicationState() {
        MGDirectoryUri = null;
        config = null;
        folderPermissionManager = new FolderPermissionManager(this);
        hideOptions();
    }

    private void showOptions() {
        try {
            isSpinnerInitialized = false;
            binding.spinnerAngle.setOnItemSelectedListener(null);
            binding.spinnerNoError.setOnItemSelectedListener(null);
            binding.spinnerMultidrawMode.setOnItemSelectedListener(null);
            binding.angleClearWorkaround.setOnItemSelectedListener(null);
            binding.switchExtGl43.setOnCheckedChangeListener(null);
            binding.switchExtCs.setOnCheckedChangeListener(null);
            binding.switchExtTimerQuery.setOnCheckedChangeListener(null);
            config = MGConfig.loadConfig(this);

            if (config == null) {
                config = new MGConfig(1, 0, 0, 1, 0, 32, 0, 0);
            }
            if (config.getEnableANGLE() > 3 || config.getEnableANGLE() < 0)
                config.setEnableANGLE(0);
            if (config.getEnableNoError() > 3 || config.getEnableNoError() < 0)
                config.setEnableNoError(0);

            if (config.getMaxGlslCacheSize() == NULL)
                config.setMaxGlslCacheSize(32);

            binding.inputMaxGlslCacheSize.setText(String.valueOf(config.getMaxGlslCacheSize()));
            binding.spinnerAngle.setSelection(config.getEnableANGLE());
            binding.spinnerNoError.setSelection(config.getEnableNoError());
            binding.spinnerMultidrawMode.setSelection(config.getMultidrawMode());
            binding.angleClearWorkaround.setSelection(config.getAngleDepthClearFixMode());
            binding.switchExtGl43.setChecked(config.getEnableExtGL43() == 1);
            binding.switchExtTimerQuery.setChecked(config.getEnableExtTimerQuery() == 0);
            binding.switchExtCs.setChecked(config.getEnableExtComputeShader() == 1);

            binding.spinnerAngle.setOnItemSelectedListener(this);
            binding.spinnerNoError.setOnItemSelectedListener(this);
            binding.spinnerMultidrawMode.setOnItemSelectedListener(this);
            binding.angleClearWorkaround.setOnItemSelectedListener(this);
            binding.switchExtGl43.setOnCheckedChangeListener(this);
            binding.switchExtTimerQuery.setOnCheckedChangeListener(this);
            binding.switchExtCs.setOnCheckedChangeListener(this);
            binding.inputMaxGlslCacheSize.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    if (!text.isEmpty()) {
                        try {
                            int number = Integer.parseInt(text);
                            if (number < -1 || number == 0) {
                                binding.inputMaxGlslCacheSizeLayout.setError(getString(R.string.option_glsl_cache_error_range));
                            } else {
                                binding.inputMaxGlslCacheSizeLayout.setError(null);
                                config.setMaxGlslCacheSize(number);
                            }
                        } catch (NumberFormatException e) {
                            binding.inputMaxGlslCacheSizeLayout.setError(getString(R.string.option_glsl_cache_error_invalid));
                        } catch (IOException e) {
                            binding.inputMaxGlslCacheSizeLayout.setError(getString(R.string.option_glsl_cache_error_unexpected));
                            throw new RuntimeException(e);
                        }
                    } else {
                        binding.inputMaxGlslCacheSizeLayout.setError(null);
                        try {
                            config.setMaxGlslCacheSize(32);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                @Override
                public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int start, int before, int after) {
                }
            });
            binding.openOptions.setVisibility(View.GONE);
            binding.optionLayout.setVisibility(View.VISIBLE);

            binding.spinnerAngle.setOnItemSelectedListener(this);
            binding.spinnerNoError.setOnItemSelectedListener(this);
            binding.spinnerMultidrawMode.setOnItemSelectedListener(this);
            binding.angleClearWorkaround.setOnItemSelectedListener(this);
            binding.switchExtGl43.setOnCheckedChangeListener(this);
            binding.switchExtTimerQuery.setOnCheckedChangeListener(this);
            binding.switchExtCs.setOnCheckedChangeListener(this);
            isSpinnerInitialized = true;

        } catch (IOException e) {
            Logger.getLogger("MG").log(Level.SEVERE, "Failed to load config! Exception: ", e.getCause());
            Toast.makeText(this, getString(R.string.warning_load_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void hideOptions() {
        binding.openOptions.setVisibility(View.VISIBLE);
        binding.optionLayout.setVisibility(View.GONE);
    }

    private void checkPermissionSilently() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			MGDirectoryUri = folderPermissionManager.getMGFolderUri();
	
			MGConfig config = MGConfig.loadConfig(this);
			if (config != null && MGDirectoryUri != null) {
				showOptions();
			} else {
				hideOptions();
			}
		} else {
			if (ActivityCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
					&& ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
				showOptions();
			} else {
				hideOptions();
			}
		}
	}

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(getString(R.string.dialog_permission_msg_android_Q, Constants.MG_DIRECTORY))
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(Environment.getExternalStorageDirectory() + "/MG"));
                    ResultListener.startActivityForResult(this, intent, REQUEST_CODE_SAF, (requestCode, resultCode, data) -> {
                        if (requestCode == REQUEST_CODE_SAF && resultCode == RESULT_OK && data != null) {
                            Uri treeUri = data.getData();
                            if (treeUri == null) {
                                hideOptions();
                                return;
                            }

                            if (!folderPermissionManager.isUriMatchingFilePath(treeUri, new File(Constants.MG_DIRECTORY))) {
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle(R.string.app_name)
                                        .setMessage(getString(R.string.warning_path_selection_error, Constants.MG_DIRECTORY, folderPermissionManager.getFileByUri(treeUri)))
                                        .setPositiveButton(R.string.dialog_positive, null)
                                        .show();
                                hideOptions();
                                return;
                            }

                            getContentResolver().takePersistableUriPermission(treeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                            MGDirectoryUri = treeUri;
                            MGConfig config = MGConfig.loadConfig(this);
                            if (config == null) config = new MGConfig(1, 0, 0, 1, 0, 32, 0, 0);
                            config.saveConfig(this);
                            showOptions();
                        }
                    });
                })
                .setNegativeButton(R.string.dialog_negative, (dialog, which) -> dialog.dismiss())
                .show();
        else {
            if (ActivityCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                showOptions();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE}, 1000);
                hideOptions();
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (!isSpinnerInitialized)
            return;
        
        if (adapterView == binding.spinnerAngle && config != null) {
            int previous = config.getEnableANGLE();
            if (i == previous) {
                return;
            }

            try {
                if (i == 3 && isAdreno740()) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.dialog_title_warning))
                            .setMessage(getString(R.string.warning_adreno_740_angle))
                            .setPositiveButton(getString(R.string.dialog_positive), (dialog, which) -> {
                                try {
                                    config.setEnableANGLE(i);
                                } catch (IOException e) {
                                    Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                                    Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton(getString(R.string.dialog_negative), (dialog, which) -> {
                                isSpinnerInitialized = false;
                                binding.spinnerAngle.setSelection(config.getEnableANGLE());
                                isSpinnerInitialized = true;
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    config.setEnableANGLE(i);
                }
            } catch (IOException e) {
                Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e.getCause());
                Toast.makeText(this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
            }
        }

        if (adapterView == binding.spinnerNoError && config != null) {
            try {
                config.setEnableNoError(i);
            } catch (IOException e) {
                Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e.getCause());
                Toast.makeText(this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
            }
        }

        if (adapterView == binding.spinnerMultidrawMode && config != null) {
            try {
                config.setMultidrawMode(i);
            } catch (IOException e) {
                Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e.getCause());
                Toast.makeText(this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
            }
        }

        if (adapterView == binding.angleClearWorkaround && config != null) {
            try {
                int previous = config.getAngleDepthClearFixMode();
                if (i == previous) {
                    return;
                }
                if (i >= 1) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.dialog_title_warning))
                            .setMessage(getString(R.string.warning_enabling_angle_clear_workaround))
                            .setPositiveButton(getString(R.string.dialog_positive), (dialog, which) -> {
                                try {
                                    config.setAngleDepthClearFixMode(i);
                                } catch (IOException e) {
                                    Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                                    Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton(getString(R.string.dialog_negative), (dialog, which) -> {
                                isSpinnerInitialized = false;
                                binding.angleClearWorkaround.setSelection(config.getAngleDepthClearFixMode());
                                isSpinnerInitialized = true;
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    config.setAngleDepthClearFixMode(i);
                }
            } catch (IOException e) {
                Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e.getCause());
                Toast.makeText(this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
        if (compoundButton == binding.switchExtGl43 && config != null) {
            if (isChecked) {
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(getString(R.string.dialog_title_warning))
                        .setMessage(getString(R.string.warning_ext_gl43_enable))
                        .setCancelable(false)
                        .setOnKeyListener((dialog, keyCode, event) -> keyCode == KeyEvent.KEYCODE_BACK)
                        .setPositiveButton(getString(R.string.dialog_positive), (dialog, which) -> {
                            try {
                                config.setEnableExtGL43(1);
                            } catch (IOException e) {
                                Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                                Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(getString(R.string.dialog_negative), (dialog, which) -> binding.switchExtGl43.setChecked(false))
                        .show();
            } else {
                try {
                    config.setEnableExtGL43(0);
                } catch (IOException e) {
                    Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                    Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                }
            }
        }
        if (compoundButton == binding.switchExtCs && config != null) {
            if (isChecked) {
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(getString(R.string.dialog_title_warning))
                        .setMessage(getString(R.string.warning_ext_cs_enable)).setCancelable(false)
                        .setOnKeyListener((dialog, keyCode, event) -> keyCode == KeyEvent.KEYCODE_BACK)
                        .setPositiveButton(getString(R.string.dialog_positive), (dialog, which) -> {
                            try {
                                config.setEnableExtComputeShader(1);
                            } catch (IOException e) {
                                Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                                Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(getString(R.string.dialog_negative), (dialog, which) -> binding.switchExtCs.setChecked(false))
                        .show();
            } else {
                try {
                    config.setEnableExtComputeShader(0);
                } catch (IOException e) {
                    Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                    Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                }
            }
        }
		if (compoundButton == binding.switchExtTimerQuery && config != null) {
			try {
				config.setEnableExtTimerQuery(isChecked ? 0 : 1); // disable (ui) -> enable (json)
			} catch (IOException e) {
				Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
				Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
			}
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ResultListener.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1000) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showOptions();
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) || !ActivityCompat.shouldShowRequestPermissionRationale(this, READ_EXTERNAL_STORAGE)) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    ResultListener.startActivityForResult(this, intent, 1000, (requestCode1, resultCode, data) -> {
                        if (requestCode1 == 1000) {
                            if (ActivityCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                showOptions();
                            } else {
                                onRequestPermissionsResult(requestCode1, permissions, grantResults);
                            }
                        }
                    });
                } else {
                    checkPermission();
                }
            }
        }
    }

    private String getGPUName() {
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            return null;
        }

        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            return null;
        }

        int[] configAttributes = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        android.opengl.EGLConfig[] eglConfigs = new android.opengl.EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, eglConfigs, 0, eglConfigs.length, numConfigs, 0)) {
            EGL14.eglTerminate(eglDisplay);
            return null;
        }

        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext eglContext = EGL14.eglCreateContext(eglDisplay, eglConfigs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglTerminate(eglDisplay);
            return null;
        }

        int[] surfaceAttributes = {
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
        };
        EGLSurface eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfigs[0], surfaceAttributes, 0);
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglTerminate(eglDisplay);
            return null;
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglTerminate(eglDisplay);
            return null;
        }

        String renderer = GLES20.glGetString(GLES20.GL_RENDERER);

        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(eglDisplay, eglSurface);
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        EGL14.eglTerminate(eglDisplay);

        return renderer;
    }

    private boolean isAdreno740() {
        String renderer = getGPUName();
        return renderer != null && renderer.toLowerCase().contains("adreno") && renderer.contains("740");
    }

}
