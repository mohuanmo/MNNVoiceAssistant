package com.example.mnnvoiceassistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.mnnvoiceassistant.databinding.ActivityMainBinding
import com.example.mnnvoiceassistant.engine.ASREngine
import com.example.mnnvoiceassistant.engine.TTSEngine
import com.example.mnnvoiceassistant.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var asrEngine: ASREngine
    private lateinit var ttsEngine: TTSEngine
    private lateinit var modelManager: ModelManager

    private var isRecording = false
    private var systemTts: TextToSpeech? = null

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "需要录音和存储权限才能使用语音助手", Toast.LENGTH_LONG).show()
        }
    }

    // ASR 模型导入
    private val importAsrLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { importModel(it, ModelManager.ModelType.ASR) }
    }

    // TTS 模型导入
    private val importTtsLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { importModel(it, ModelManager.ModelType.TTS) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        initEngines()
        setupUI()
        updateModelStatus()

        // 检查是否已设为默认数字助理
        checkDefaultAssistant()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needPermissions.isNotEmpty()) {
            permissionLauncher.launch(needPermissions.toTypedArray())
        }
    }

    private fun initEngines() {
        modelManager = ModelManager(this)
        asrEngine = ASREngine(this, modelManager)
        ttsEngine = TTSEngine(this, modelManager)

        // 初始化系统 TTS（备用）
        systemTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "System TTS initialized")
            }
        }
    }

    private fun setupUI() {
        // ASR 模型导入
        binding.btnImportAsr.setOnClickListener {
            importAsrLauncher.launch("*/*")
        }

        // TTS 模型导入
        binding.btnImportTts.setOnClickListener {
            importTtsLauncher.launch("*/*")
        }

        // 按住说话
        binding.btnRecord.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }

        // TTS 朗读
        binding.btnSpeak.setOnClickListener {
            val text = binding.etInputText.text.toString().trim()
            if (text.isNotEmpty()) {
                speakText(text)
            } else {
                Toast.makeText(this, "请输入要朗读的文本", Toast.LENGTH_SHORT).show()
            }
        }

        // 设为默认助理
        binding.btnSetDefault.setOnClickListener {
            openAssistantSettings()
        }

        // 模型管理
        binding.btnModelManager.setOnClickListener {
            openModelManager()
        }
    }

    private fun startRecording() {
        if (!modelManager.isASRModelLoaded()) {
            Toast.makeText(this, R.string.msg_model_not_loaded, Toast.LENGTH_SHORT).show()
            return
        }

        isRecording = true
        binding.btnRecord.text = getString(R.string.btn_stop_record)
        binding.tvStatus.text = getString(R.string.status_listening)
        binding.waveformView.startAnimating()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                asrEngine.startStreamingRecognition { text, isFinal ->
                    runOnUiThread {
                        if (isFinal) {
                            binding.tvResult.text = text
                            binding.tvStatus.text = getString(R.string.status_ready)
                        } else {
                            binding.tvResult.text = text
                            binding.tvStatus.text = getString(R.string.status_recognizing)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ASR error", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        binding.btnRecord.text = getString(R.string.btn_start_record)
        binding.waveformView.stopAnimating()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = asrEngine.stopStreamingRecognition()
            withContext(Dispatchers.Main) {
                if (result.isNotEmpty()) {
                    binding.tvResult.text = result
                }
                binding.tvStatus.text = getString(R.string.status_ready)
            }
        }
    }

    private fun speakText(text: String) {
        if (!modelManager.isTTSModelLoaded()) {
            // 回退到系统 TTS
            systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_1")
            Toast.makeText(this, "使用系统 TTS（未导入 MNN 模型）", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvStatus.text = getString(R.string.status_synthesizing)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val audioData = ttsEngine.synthesize(text)
                withContext(Dispatchers.Main) {
                    ttsEngine.playAudio(audioData)
                    binding.tvStatus.text = getString(R.string.status_playing)
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "合成失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.tvStatus.text = getString(R.string.status_ready)
                }
            }
        }
    }

    private fun importModel(uri: Uri, type: ModelManager.ModelType) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = modelManager.importModel(uri, type)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@MainActivity, 
                            "${if (type == ModelManager.ModelType.ASR) "ASR" else "TTS"} 模型导入成功", 
                            Toast.LENGTH_SHORT).show()
                        updateModelStatus()
                    } else {
                        Toast.makeText(this@MainActivity, "模型导入失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "导入错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateModelStatus() {
        binding.tvAsrStatus.text = if (modelManager.isASRModelLoaded()) {
            "ASR 模型: ✓ 已加载 (${modelManager.getASRModelInfo()})"
        } else {
            "ASR 模型: ✗ 未加载"
        }

        binding.tvTtsStatus.text = if (modelManager.isTTSModelLoaded()) {
            "TTS 模型: ✓ 已加载 (${modelManager.getTTSModelInfo()})"
        } else {
            "TTS 模型: ✗ 未加载"
        }
    }

    private fun checkDefaultAssistant() {
        // 检查当前默认助理是否为本应用
        val assistant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
            roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT)
        } else {
            false
        }

        if (!assistant) {
            binding.tvAssistantStatus.text = "未设为默认数字助理"
            binding.btnSetDefault.visibility = android.view.View.VISIBLE
        } else {
            binding.tvAssistantStatus.text = "已是默认数字助理 ✓"
            binding.btnSetDefault.visibility = android.view.View.GONE
        }
    }

    private fun openAssistantSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                startActivity(intent)
            } else {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "请手动在设置中更改默认助理", Toast.LENGTH_LONG).show()
        }
    }

    private fun openModelManager() {
        // 打开模型管理页面（可扩展）
        AlertDialog.Builder(this)
            .setTitle("模型管理")
            .setMessage("""
                ASR 模型路径: ${modelManager.getASRModelPath()}
                TTS 模型路径: ${modelManager.getTTSModelPath()}

                支持的模型格式:
                • ASR: .mnn (Sherpa-MNN / SenseVoice / Zipformer)
                • TTS: .mnn (Supertonic / Bert-VITS2 / Piper)
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        asrEngine.release()
        ttsEngine.release()
        systemTts?.shutdown()
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
