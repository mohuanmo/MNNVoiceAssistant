package com.example.mnnvoiceassistant.engine

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.mnnvoiceassistant.model.ModelManager
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ASR 语音识别引擎
 * 基于 MNN 框架，支持 Sherpa-MNN 模型
 * 
 * 支持的模型架构:
 * - Zipformer (流式/离线双语)
 * - SenseVoice (多语言)
 * - Whisper (多语言)
 * - Paraformer (中文优化)
 */
class ASREngine(
    private val context: Context,
    private val modelManager: ModelManager
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    // 音频参数
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    // MNN 推理相关（实际项目中通过 JNI 调用 sherpa-mnn）
    private var nativeHandle: Long = 0
    private var isModelLoaded = false

    init {
        loadModel()
    }

    /**
     * 加载 ASR 模型
     */
    private fun loadModel() {
        if (!modelManager.isASRModelLoaded()) {
            Log.w(TAG, "ASR model not loaded yet")
            return
        }

        try {
            val modelPath = modelManager.getASRModelPath()!!
            val configPath = modelManager.getASRConfigPath()

            // 通过 JNI 加载 MNN 模型
            // nativeHandle = nativeCreateRecognizer(modelPath, configPath)
            // 这里使用模拟实现，实际应调用 sherpa-mnn JNI

            isModelLoaded = true
            Log.i(TAG, "ASR model loaded: $modelPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ASR model", e)
            isModelLoaded = false
        }
    }

    /**
     * 开始流式语音识别
     */
    fun startStreamingRecognition(onResult: (String, Boolean) -> Unit) {
        if (!isModelLoaded) {
            onResult("模型未加载", true)
            return
        }

        if (isRecording) return

        isRecording = true

        // 初始化 AudioRecord
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(bufferSize)
            val samplesBuffer = ArrayList<Float>()

            while (isRecording && isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // 将 PCM 16bit 转换为 float 数组
                    val samples = pcm16ToFloat(buffer, read)
                    samplesBuffer.addAll(samples.toList())

                    // 当积累足够数据时进行推理（例如 0.5 秒）
                    if (samplesBuffer.size >= sampleRate / 2) {
                        val chunk = samplesBuffer.toFloatArray()
                        samplesBuffer.clear()

                        // 流式推理
                        val partialResult = streamingRecognize(chunk)
                        withContext(Dispatchers.Main) {
                            onResult(partialResult, false)
                        }
                    }
                }
            }

            // 最后一批数据推理
            if (samplesBuffer.isNotEmpty()) {
                val finalResult = recognize(samplesBuffer.toFloatArray())
                withContext(Dispatchers.Main) {
                    onResult(finalResult, true)
                }
            }
        }
    }

    /**
     * 停止识别并返回最终结果
     */
    fun stopStreamingRecognition(): String {
        isRecording = false
        recordingJob?.cancel()

        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null

        // 返回最后识别结果
        return ""
    }

    /**
     * 离线识别（非流式）
     */
    fun recognize(samples: FloatArray): String {
        if (!isModelLoaded) return "模型未加载"

        return try {
            // 实际调用: nativeRecognize(nativeHandle, samples)
            // 模拟识别结果
            mockRecognize(samples)
        } catch (e: Exception) {
            Log.e(TAG, "Recognition error", e)
            "识别失败"
        }
    }

    /**
     * 流式识别单段音频
     */
    private fun streamingRecognize(samples: FloatArray): String {
        // 实际调用 sherpa-mnn 流式接口
        return mockRecognize(samples)
    }

    /**
     * 模拟识别（用于演示，实际项目替换为真实推理）
     */
    private fun mockRecognize(samples: FloatArray): String {
        // 这里应该是真实的 MNN 推理调用
        // 例如通过 JNI 调用 sherpa-mnn 的 C++ API
        return "[识别中... 音频长度: ${samples.size} 采样点]"
    }

    /**
     * PCM 16bit 转 float (-1.0 ~ 1.0)
     */
    private fun pcm16ToFloat(pcmData: ByteArray, length: Int): FloatArray {
        val shorts = ShortArray(length / 2)
        val byteBuffer = ByteBuffer.wrap(pcmData, 0, length)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in shorts.indices) {
            shorts[i] = byteBuffer.short
        }

        return FloatArray(shorts.size) { i ->
            shorts[i] / 32768.0f
        }
    }

    fun release() {
        stopStreamingRecognition()
        if (nativeHandle != 0L) {
            // nativeReleaseRecognizer(nativeHandle)
            nativeHandle = 0
        }
    }

    // JNI 方法声明（实际项目中实现）
    // private external fun nativeCreateRecognizer(modelPath: String, configPath: String?): Long
    // private external fun nativeRecognize(handle: Long, samples: FloatArray): String
    // private external fun nativeStreamingRecognize(handle: Long, samples: FloatArray): String
    // private external fun nativeReleaseRecognizer(handle: Long)

    companion object {
        const val TAG = "ASREngine"
    }
}
