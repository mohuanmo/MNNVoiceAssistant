package com.example.mnnvoiceassistant.model

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 模型管理器
 * 负责模型的导入、存储、加载和状态管理
 * 
 * 支持的模型:
 * ASR: Sherpa-MNN (SenseVoice, Zipformer, Whisper)
 * TTS: Supertonic-MNN, Bert-VITS2-MNN, Piper-MNN
 */
class ModelManager(private val context: Context) {

    enum class ModelType { ASR, TTS }

    private val modelDir = File(context.getExternalFilesDir(null), "models")
    private val asrModelDir = File(modelDir, "asr")
    private val ttsModelDir = File(modelDir, "tts")

    // 模型文件路径
    private var asrModelPath: String? = null
    private var ttsModelPath: String? = null
    private var asrConfigPath: String? = null
    private var ttsConfigPath: String? = null

    init {
        modelDir.mkdirs()
        asrModelDir.mkdirs()
        ttsModelDir.mkdirs()
        scanExistingModels()
    }

    /**
     * 扫描已存在的模型文件
     */
    private fun scanExistingModels() {
        // ASR 模型扫描
        asrModelDir.listFiles()?.forEach { file ->
            when {
                file.name.endsWith(".mnn") -> asrModelPath = file.absolutePath
                file.name.endsWith(".json") || file.name.endsWith(".yaml") || file.name.endsWith(".txt") -> 
                    asrConfigPath = file.absolutePath
            }
        }

        // TTS 模型扫描
        ttsModelDir.listFiles()?.forEach { file ->
            when {
                file.name.endsWith(".mnn") -> ttsModelPath = file.absolutePath
                file.name.endsWith(".json") || file.name.endsWith(".yaml") || file.name.endsWith(".txt") -> 
                    ttsConfigPath = file.absolutePath
            }
        }

        Log.i(TAG, "Scanned models - ASR: $asrModelPath, TTS: $ttsModelPath")
    }

    /**
     * 导入模型文件
     */
    suspend fun importModel(uri: Uri, type: ModelType): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = when (type) {
                ModelType.ASR -> asrModelDir
                ModelType.TTS -> ttsModelDir
            }

            // 清空旧模型
            targetDir.listFiles()?.forEach { it.delete() }

            // 复制新模型
            context.contentResolver.openInputStream(uri)?.use { input ->
                val fileName = getFileNameFromUri(uri) ?: "model.mnn"
                val targetFile = File(targetDir, fileName)
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }

                when (type) {
                    ModelType.ASR -> {
                        asrModelPath = targetFile.absolutePath
                        // 尝试查找同目录下的配置文件
                        findConfigFile(targetDir)?.let { asrConfigPath = it }
                    }
                    ModelType.TTS -> {
                        ttsModelPath = targetFile.absolutePath
                        findConfigFile(targetDir)?.let { ttsConfigPath = it }
                    }
                }

                Log.i(TAG, "Imported ${type.name} model to ${targetFile.absolutePath}")
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model", e)
            false
        }
    }

    /**
     * 批量导入模型（支持多文件，如模型+词典+配置）
     */
    suspend fun importModelFiles(uris: List<Uri>, type: ModelType): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = when (type) {
                ModelType.ASR -> asrModelDir
                ModelType.TTS -> ttsModelDir
            }
            targetDir.listFiles()?.forEach { it.delete() }

            uris.forEach { uri ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val fileName = getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
                    val targetFile = File(targetDir, fileName)
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }

                    if (fileName.endsWith(".mnn")) {
                        when (type) {
                            ModelType.ASR -> asrModelPath = targetFile.absolutePath
                            ModelType.TTS -> ttsModelPath = targetFile.absolutePath
                        }
                    }
                }
            }

            findConfigFile(targetDir)?.let {
                when (type) {
                    ModelType.ASR -> asrConfigPath = it
                    ModelType.TTS -> ttsConfigPath = it
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model files", e)
            false
        }
    }

    fun isASRModelLoaded(): Boolean = asrModelPath != null && File(asrModelPath!!).exists()
    fun isTTSModelLoaded(): Boolean = ttsModelPath != null && File(ttsModelPath!!).exists()

    fun getASRModelPath(): String? = asrModelPath
    fun getTTSModelPath(): String? = ttsModelPath
    fun getASRConfigPath(): String? = asrConfigPath
    fun getTTSConfigPath(): String? = ttsConfigPath

    fun getASRModelInfo(): String {
        val file = asrModelPath?.let { File(it) }
        return if (file != null && file.exists()) {
            val sizeMB = file.length() / (1024 * 1024)
            "${file.name} (${sizeMB}MB)"
        } else "未加载"
    }

    fun getTTSModelInfo(): String {
        val file = ttsModelPath?.let { File(it) }
        return if (file != null && file.exists()) {
            val sizeMB = file.length() / (1024 * 1024)
            "${file.name} (${sizeMB}MB)"
        } else "未加载"
    }

    fun deleteModel(type: ModelType) {
        when (type) {
            ModelType.ASR -> {
                asrModelDir.listFiles()?.forEach { it.delete() }
                asrModelPath = null
                asrConfigPath = null
            }
            ModelType.TTS -> {
                ttsModelDir.listFiles()?.forEach { it.delete() }
                ttsModelPath = null
                ttsConfigPath = null
            }
        }
    }

    private fun findConfigFile(dir: File): String? {
        return dir.listFiles()?.find { 
            it.name.endsWith(".json") || it.name.endsWith(".yaml") || it.name == "tokens.txt" 
        }?.absolutePath
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { File(it).name }
        }
        return result
    }

    companion object {
        const val TAG = "ModelManager"

        // 推荐的预训练模型下载地址
        const val MODEL_HUB_URL = "https://huggingface.co/yunfengwang/supertonic-tts-mnn"
        const val SHERPA_MNN_URL = "https://github.com/k2-fsa/sherpa-onnx"
    }
}
