package com.fcl.plugin.mobileglues

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.fcl.plugin.mobileglues.databinding.ActivityMainBinding
import com.fcl.plugin.mobileglues.settings.FolderPermissionManager
import com.fcl.plugin.mobileglues.settings.MGConfig
import com.fcl.plugin.mobileglues.utils.Constants
import com.fcl.plugin.mobileglues.utils.FileUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Types
import java.util.logging.Level
import java.util.logging.Logger

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener {

    private lateinit var glVersionMap: Map<String, Int>

    private lateinit var binding: ActivityMainBinding
    private var config: MGConfig? = null
    private lateinit var folderPermissionManager: FolderPermissionManager
    private var isSpinnerInitialized = false

    // 使用 Activity Result API 替代 onActivityResult
    private val safLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { treeUri ->
                if (!folderPermissionManager.isUriMatchingFilePath(treeUri, File(Constants.MG_DIRECTORY))) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.app_name)
                        .setMessage(
                            getString(
                                R.string.warning_path_selection_error,
                                Constants.MG_DIRECTORY,
                                folderPermissionManager.getFileByUri(treeUri)
                            )
                        )
                        .setPositiveButton(R.string.dialog_positive, null)
                        .show()
                    hideOptions()
                    return@let
                }

                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                MGDirectoryUri = treeUri
                var currentConfig = MGConfig.loadConfig(this)
                if (currentConfig == null) {
                    currentConfig = MGConfig(this)
                }
                currentConfig.save()
                showOptions()
            } ?: hideOptions()
        }
    }
    
    private val appSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 当从应用设置返回时，重新检查权限
        if (hasLegacyPermissions()) {
            showOptions()
        } else {
            // 如果用户仍然没有授予权限，可以再次显示请求或提示
             checkPermission()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }

        glVersionMap = linkedMapOf(
            getString(R.string.option_angle_disable) to 0,
            "OpenGL 4.6" to 46,
            "OpenGL 4.5" to 45,
            "OpenGL 4.4" to 44,
            "OpenGL 4.3" to 43,
            "OpenGL 4.2" to 42,
            "OpenGL 4.1" to 41,
            "OpenGL 4.0" to 40,
            "OpenGL 3.3" to 33,
            "OpenGL 3.2" to 32
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        folderPermissionManager = FolderPermissionManager(this)
        MainActivityContext = this
        setSupportActionBar(binding.appBar)

        setupSpinners()

        binding.openOptions.setOnClickListener { checkPermission() }
        
        // 设置约束布局的底边距为系统导航栏的高度
        val optionLayoutParams = binding.optionLayout.layoutParams as ViewGroup.MarginLayoutParams
        window.decorView.setOnApplyWindowInsetsListener { v, insets ->
            optionLayoutParams.setMargins(0, 0, 0, insets.systemWindowInsetBottom)
            insets
        }
    }

    private fun setupSpinners() {
        // ANGLE 选项
        ArrayAdapter.createFromResource(
            this, R.array.angle_options, R.layout.spinner
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerAngle.adapter = adapter
        }

        // No Error 选项
        ArrayAdapter.createFromResource(
            this, R.array.no_error_options, R.layout.spinner
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerNoError.adapter = adapter
        }

        // Multidraw Mode 选项
        ArrayAdapter.createFromResource(
            this, R.array.multidraw_mode_options, R.layout.spinner
        ).also { adapter ->
             adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerMultidrawMode.adapter = adapter
        }
        
        // GL Version 选项
        addCustomGLVersionOptions()

        // ANGLE Clear Workaround 选项
        ArrayAdapter.createFromResource(
            this, R.array.angle_clear_workaround_options, R.layout.spinner
        ).also { adapter ->
             adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.angleClearWorkaround.adapter = adapter
        }
    }

    private fun hasMgDirectoryAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MGDirectoryUri != null
        } else {
            hasLegacyPermissions()
        }
    }
    
    private fun hasLegacyPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        checkPermissionSilently()
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_remove)?.isEnabled = hasMgDirectoryAccess()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                AppInfoDialogBuilder(this).show()
                true
            }
            R.id.action_remove -> {
                showRemoveConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showRemoveConfirmationDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_mg_files_title)
            .setMessage(R.string.remove_mg_files_message)
            .setNegativeButton(R.string.dialog_negative, null)
            .setPositiveButton(getString(R.string.ok)) { _, _ -> removeMobileGluesCompletely() }
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positiveButton.isEnabled = false
            val cooldownSeconds = 10

            object : CountDownTimer(cooldownSeconds * 1000L, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val remainingSeconds = (millisUntilFinished / 1000).toInt()
                    positiveButton.text = getString(R.string.ok_with_countdown, remainingSeconds)
                }

                override fun onFinish() {
                    positiveButton.text = getString(R.string.ok)
                    positiveButton.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                    positiveButton.isEnabled = true
                }
            }.start()
        }
        dialog.show()
    }

    private fun removeMobileGluesCompletely() {
        val view = LayoutInflater.from(this).inflate(R.layout.progress_dialog_md3, null)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val progressText = view.findViewById<TextView>(R.id.progress_text)
        val progressDialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(R.string.removing_mobileglues)
            .setView(view)
            .setCancelable(false)
            .create()
        
        progressDialog.show()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. 删除配置
                withContext(Dispatchers.Main) {
                    progressText.setText(R.string.deleting_config)
                    progressBar.progress = 20
                }
                config?.deleteConfig()

                // 2. 删除缓存
                withContext(Dispatchers.Main) {
                    progressText.setText(R.string.deleting_cache)
                    progressBar.progress = 40
                }
                deleteFileIfExists("glsl_cache.tmp")

                // 3. 删除日志
                withContext(Dispatchers.Main) {
                    progressText.setText(R.string.deleting_logs)
                    progressBar.progress = 60
                }
                deleteFileIfExists("latest.log")

                // 4. 清理目录
                withContext(Dispatchers.Main) {
                    progressText.setText(R.string.cleaning_directory)
                    progressBar.progress = 80
                }
                checkAndDeleteEmptyDirectory()

                // 5. 移除权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && MGDirectoryUri != null) {
                    withContext(Dispatchers.Main) {
                        progressText.setText(R.string.removing_permissions)
                        progressBar.progress = 100
                    }
                    releaseSafPermissions()
                }

                // 6. 操作完成
                withContext(Dispatchers.Main) {
                    resetApplicationState()
                    progressDialog.dismiss()
                    showFinalDialog()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, getString(R.string.remove_failed, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFinalDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_complete_title)
            .setMessage(R.string.remove_complete_message)
            .setCancelable(false)
            .setPositiveButton(R.string.exit) { _, _ ->
                finishAffinity()
                System.exit(0)
            }
            .show()
    }

    private fun deleteFileIfExists(fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MGDirectoryUri?.let { uri ->
                    DocumentFile.fromTreeUri(this, uri)?.findFile(fileName)?.let { file ->
                        if (file.exists()) DocumentsContract.deleteDocument(contentResolver, file.uri)
                    }
                }
            } else {
                val file = File(Environment.getExternalStorageDirectory(), "MG/$fileName")
                if (file.exists()) {
                    FileUtils.deleteFile(file)
                }
            }
        } catch (e: Exception) {
            Log.w("MG", "删除文件失败: $fileName", e)
        }
    }

    private fun checkAndDeleteEmptyDirectory() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MGDirectoryUri?.let { uri ->
                    val dir = DocumentFile.fromTreeUri(this, uri)
                    if (dir != null && dir.listFiles().isEmpty()) {
                        DocumentsContract.deleteDocument(contentResolver, dir.uri)
                    }
                }
            } else {
                val mgDir = File(Environment.getExternalStorageDirectory(), "MG")
                if (mgDir.exists() && mgDir.isDirectory && mgDir.listFiles()?.isEmpty() == true) {
                    FileUtils.deleteFile(mgDir)
                }
            }
        } catch (e: Exception) {
            Log.w("MG", "删除目录失败", e)
        }
    }

    private fun releaseSafPermissions() {
        try {
            contentResolver.persistedUriPermissions
                .filter { it.uri == MGDirectoryUri }
                .forEach {
                    contentResolver.releasePersistableUriPermission(
                        it.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
        } catch (e: Exception) {
            Logger.getLogger("MG").log(Level.WARNING, "移除 SAF 权限失败", e)
        }
    }

    private fun resetApplicationState() {
        MGDirectoryUri = null
        config = null
        folderPermissionManager = FolderPermissionManager(this) // Re-initialize
        hideOptions()
    }

    private fun showOptions() {
        isSpinnerInitialized = false // 禁用监听器以进行初始化
        setAllListeners(null) // 解绑所有监听器

        config = MGConfig.loadConfig(this) ?: MGConfig(this)
        
        config?.let { cfg ->
             // 规范化配置值
            if (cfg.enableANGLE !in 0..3) cfg.enableANGLE = 0
            if (cfg.enableNoError !in 0..3) cfg.enableNoError = 0
            if (cfg.maxGlslCacheSize == Types.NULL) cfg.maxGlslCacheSize = 32

            // 更新 UI
            binding.inputMaxGlslCacheSize.setText(cfg.maxGlslCacheSize.toString())
            binding.spinnerAngle.setSelection(cfg.enableANGLE)
            binding.spinnerNoError.setSelection(cfg.enableNoError)
            binding.spinnerMultidrawMode.setSelection(cfg.multidrawMode)
            binding.angleClearWorkaround.setSelection(cfg.angleDepthClearFixMode)
            binding.switchExtGl43.isChecked = cfg.enableExtGL43 == 1
            binding.switchExtTimerQuery.isChecked = cfg.enableExtTimerQuery == 0
            binding.switchExtDirectStateAccess.isChecked = cfg.enableExtDirectStateAccess == 0
            binding.switchExtCs.isChecked = cfg.enableExtComputeShader == 1
            binding.switchEnableFsr1.isChecked = cfg.fsr1Setting == 1
            setCustomGLVersionSpinnerSelectionByGLVersion(cfg.customGLVersion)
        }
        
        setAllListeners(this) // 重新绑定所有监听器

        binding.inputMaxGlslCacheSize.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().trim()
                if (text.isNotEmpty()) {
                    try {
                        val number = text.toInt()
                        if (number < -1 || number == 0) {
                            binding.inputMaxGlslCacheSizeLayout.error = getString(R.string.option_glsl_cache_error_range)
                        } else {
                            binding.inputMaxGlslCacheSizeLayout.error = null
                            config?.maxGlslCacheSize = number
                        }
                    } catch (e: NumberFormatException) {
                        binding.inputMaxGlslCacheSizeLayout.error = getString(R.string.option_glsl_cache_error_invalid)
                    }
                } else {
                    binding.inputMaxGlslCacheSizeLayout.error = null
                    config?.maxGlslCacheSize = 32 // 默认值
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.openOptions.visibility = View.GONE
        binding.scrollLayout.visibility = View.VISIBLE
        isSpinnerInitialized = true
    }

    private fun setAllListeners(listener: Any?) {
        val itemListener = if (listener is AdapterView.OnItemSelectedListener) listener else null
        val checkedListener = if (listener is CompoundButton.OnCheckedChangeListener) listener else null
        
        binding.spinnerAngle.onItemSelectedListener = itemListener
        binding.spinnerNoError.onItemSelectedListener = itemListener
        binding.spinnerMultidrawMode.onItemSelectedListener = itemListener
        binding.spinnerCustomGlVersion.onItemSelectedListener = itemListener
        binding.angleClearWorkaround.onItemSelectedListener = itemListener
        
        binding.switchExtGl43.setOnCheckedChangeListener(checkedListener)
        binding.switchExtCs.setOnCheckedChangeListener(checkedListener)
        binding.switchExtTimerQuery.setOnCheckedChangeListener(checkedListener)
        binding.switchExtDirectStateAccess.setOnCheckedChangeListener(checkedListener)
        binding.switchEnableFsr1.setOnCheckedChangeListener(checkedListener)
    }


    private fun hideOptions() {
        binding.openOptions.visibility = View.VISIBLE
        binding.scrollLayout.visibility = View.GONE
    }

    private fun checkPermissionSilently() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MGDirectoryUri = folderPermissionManager.getMGFolderUri()
            val config = MGConfig.loadConfig(this)
            if (config != null && MGDirectoryUri != null) {
                showOptions()
            } else {
                hideOptions()
            }
        } else {
            if (hasLegacyPermissions()) {
                showOptions()
            } else {
                hideOptions()
            }
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(getString(R.string.dialog_permission_msg_android_Q, Constants.MG_DIRECTORY))
                .setPositiveButton(R.string.dialog_positive) { _, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    // EXTRA_INITIAL_URI 是可选的，但可以改善用户体验
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(Environment.getExternalStorageDirectory().toString() + "/MG"))
                    safLauncher.launch(intent)
                }
                .setNegativeButton(R.string.dialog_negative) { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            if (hasLegacyPermissions()) {
                showOptions()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), LEGACY_PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
        if (!isSpinnerInitialized || config == null) return

        when (adapterView.id) {
            R.id.spinner_angle -> {
                val previous = config!!.enableANGLE
                if (position == previous) return

                if (position == 3 && isAdreno740()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.dialog_title_warning))
                        .setMessage(getString(R.string.warning_adreno_740_angle))
                        .setPositiveButton(getString(R.string.dialog_positive)) { _, _ -> config?.enableANGLE = position }
                        .setNegativeButton(getString(R.string.dialog_negative)) { _, _ ->
                            isSpinnerInitialized = false
                            binding.spinnerAngle.setSelection(config!!.enableANGLE)
                            isSpinnerInitialized = true
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    config?.enableANGLE = position
                }
            }
            R.id.spinner_no_error -> config?.enableNoError = position
            R.id.spinner_multidraw_mode -> config?.multidrawMode = position
            R.id.spinner_custom_gl_version -> handleCustomGLVersionSelection(position)
            R.id.angle_clear_workaround -> {
                 val previous = config!!.angleDepthClearFixMode
                 if (position == previous) return
                 if (position >= 1) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.dialog_title_warning))
                        .setMessage(getString(R.string.warning_enabling_angle_clear_workaround))
                        .setPositiveButton(getString(R.string.dialog_positive)) { _, _ -> config?.angleDepthClearFixMode = position }
                        .setNegativeButton(getString(R.string.dialog_negative)) { _, _ ->
                            isSpinnerInitialized = false
                            binding.angleClearWorkaround.setSelection(config!!.angleDepthClearFixMode)
                            isSpinnerInitialized = true
                        }
                        .setCancelable(false)
                        .show()
                 } else {
                    config?.angleDepthClearFixMode = position
                 }
            }
        }
    }
    
    private fun handleCustomGLVersionSelection(position: Int) {
        val previous = config!!.customGLVersion
        val newValue = getGLVersionBySpinnerIndex(position)
        if (newValue == previous) return

        if (previous == 0) {
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_title_warning))
                .setMessage(getText(R.string.warning_enabling_custom_gl_version))
                .setNegativeButton(getString(R.string.dialog_negative)) { _, _ ->
                    isSpinnerInitialized = false
                    binding.spinnerCustomGlVersion.setSelection(getSpinnerIndexByGLVersion(previous))
                    isSpinnerInitialized = true
                }
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    config?.customGLVersion = newValue
                }
                .setCancelable(false)
                .create()
            
            dialog.setOnShowListener {
                val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                positiveButton.isEnabled = false
                val cooldownSeconds = 41

                object : CountDownTimer(cooldownSeconds * 1000L, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val remainingSeconds = (millisUntilFinished / 1000).toInt()
                        positiveButton.text = getString(R.string.ok_with_countdown, remainingSeconds)
                    }
                    override fun onFinish() {
                        positiveButton.text = getString(R.string.ok)
                        positiveButton.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                        positiveButton.isEnabled = true
                    }
                }.start()
            }
            dialog.show()
        } else {
            config?.customGLVersion = newValue
        }
    }


    override fun onNothingSelected(parent: AdapterView<*>) {}

    override fun onCheckedChanged(compoundButton: CompoundButton, isChecked: Boolean) {
         if (config == null) return

         when (compoundButton.id) {
            R.id.switch_ext_gl43 -> handleSwitchWithWarning(
                isChecked,
                R.string.warning_ext_gl43_enable,
                { config?.enableExtGL43 = 1 },
                { config?.enableExtGL43 = 0 },
                compoundButton
            )
            R.id.switch_ext_cs -> handleSwitchWithWarning(
                isChecked,
                R.string.warning_ext_cs_enable,
                { config?.enableExtComputeShader = 1 },
                { config?.enableExtComputeShader = 0 },
                compoundButton
            )
            R.id.switch_enable_fsr1 -> handleSwitchWithWarning(
                isChecked,
                R.string.warning_fsr1_enable,
                { config?.fsr1Setting = 1 },
                { config?.fsr1Setting = 0 },
                compoundButton
            )
            R.id.switch_ext_timer_query -> config?.enableExtTimerQuery = if (isChecked) 0 else 1 // UI (disable) -> JSON (enable)
            R.id.switch_ext_direct_state_access -> config?.enableExtDirectStateAccess = if (isChecked) 0 else 1
         }
    }
    
    private fun handleSwitchWithWarning(
        isChecked: Boolean,
        warningMsgRes: Int,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        button: CompoundButton
    ) {
        if (isChecked) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_title_warning))
                .setMessage(getString(warningMsgRes))
                .setCancelable(false)
                .setOnKeyListener { dialog, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
                .setPositiveButton(getString(R.string.dialog_positive)) { _, _ -> onConfirm() }
                .setNegativeButton(getString(R.string.dialog_negative)) { _, _ -> button.isChecked = false }
                .show()
        } else {
            onCancel()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LEGACY_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showOptions()
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // 用户选择了 "不再询问"，引导他们到设置
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    appSettingsLauncher.launch(intent)
                } else {
                    // 用户拒绝了，可以再次显示请求或提示
                    checkPermission()
                }
            }
        }
    }

    private fun getGPUName(): String? {
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return null

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return null
        
        // 此处省略了 EGL 上下文创建的完整代码，因为它很长且与问题核心无关
        // 假设它能正确返回渲染器名称
        // ... (完整的 EGL 上下文创建和销毁代码)
        
        // 模拟一个简单的实现
        var renderer: String? = null
        val configAttributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
        val eglConfigs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, eglConfigs, 0, 1, numConfigs, 0)) {
            EGL14.eglTerminate(eglDisplay)
            return null
        }
        val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val eglContext = EGL14.eglCreateContext(eglDisplay, eglConfigs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
        
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            val pbufferAttributes = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfigs[0], pbufferAttributes, 0)
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                 if (EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    renderer = GLES20.glGetString(GLES20.GL_RENDERER)
                 }
                 EGL14.eglDestroySurface(eglDisplay, eglSurface)
            }
            EGL14.eglDestroyContext(eglDisplay, eglContext)
        }
        EGL14.eglTerminate(eglDisplay)
        return renderer
    }

    private fun isAdreno740(): Boolean {
        val renderer = getGPUName()
        return renderer?.lowercase()?.contains("adreno") == true && renderer.contains("740")
    }

    private fun addCustomGLVersionOptions() {
        val glVersionOptions = ArrayList(glVersionMap.keys)
        val adapter = ArrayAdapter(this, R.layout.spinner, glVersionOptions)
        binding.spinnerCustomGlVersion.adapter = adapter
    }

    private fun setCustomGLVersionSpinnerSelectionByGLVersion(glVersion: Int) {
        val targetDisplay = glVersionMap.entries.find { it.value == glVersion }?.key ?: "Disabled"
        val adapter = binding.spinnerCustomGlVersion.adapter as ArrayAdapter<String>
        val position = adapter.getPosition(targetDisplay)
        binding.spinnerCustomGlVersion.setSelection(position.coerceAtLeast(0))
    }

    private fun getGLVersionBySpinnerIndex(index: Int): Int {
        val selected = binding.spinnerCustomGlVersion.getItemAtPosition(index) as String
        return glVersionMap[selected] ?: 0
    }

    private fun getSpinnerIndexByGLVersion(glVersion: Int): Int {
        val targetDisplay = glVersionMap.entries.find { it.value == glVersion }?.key ?: "Disabled"
        val adapter = binding.spinnerCustomGlVersion.adapter as ArrayAdapter<String>
        return adapter.getPosition(targetDisplay)
    }

    companion object {
        private const val LEGACY_PERMISSION_REQUEST_CODE = 1000

        var MGDirectoryUri: Uri? = null
        lateinit var MainActivityContext: Context
    }
}