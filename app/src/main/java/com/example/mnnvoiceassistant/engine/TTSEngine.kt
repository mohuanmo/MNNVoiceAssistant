package com.example.mnnvoiceassistant.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.mnnvoiceassistant.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TTS 语音合成引擎
 * 基于 MNN 框架，支持 Supertonic-MNN / Bert-VITS2-MNN 模型
 * 
 * 支持的模型:
 * - Supertonic v2/v3 (30+ 语言, 10 种音色)
 * - Bert-VITS2 (中日英, 情感控制)
 * - Piper (轻量多语言)
 */
class TTSEngine(
    private val context: Context,
    private val modelManager: ModelManager
) {
    private var nativeHandle: Long = 0
    private var isModelLoaded = false
    private var sampleRate = 22050
    private var audioTrack: AudioTrack? = null

    // 合成参数
    var speed = 1.0f
    var speakerId = 0
    var voiceStyle = "M1" // M1-M5 男声, F1-F5 女声

    init {
        loadModel()
        initAudioTrack()
    }

    private fun loadModel() {
        if (!modelManager.isTTSModelLoaded()) {
            Log.w(TAG, "TTS model not loaded yet")
            return
        }

        try {
            val modelPath = modelManager.getTTSModelPath()!!
            val configPath = modelManager.getTTSConfigPath()

            // 通过 JNI 加载 MNN TTS 模型
            // nativeHandle = nativeCreateTTS(modelPath, configPath)

            // 从配置中读取采样率
            sampleRate = 22050 // 默认，实际应从模型配置读取

            isModelLoaded = true
            Log.i(TAG, "TTS model loaded: $modelPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TTS model", e)
            isModelLoaded = false
        }
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /**
     * 合成语音
     * @param text 要合成的文本
     * @return PCM float 音频数据
     */
    suspend fun synthesize(text: String): FloatArray = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            throw IllegalStateException("TTS model not loaded")
        }

        try {
            Log.d(TAG, "Synthesizing: $text")

            // 实际调用: nativeSynthesize(nativeHandle, text, speakerId, speed)
            // 这里返回模拟数据
            mockSynthesize(text)
        } catch (e: Exception) {
            Log.e(TAG, "TTS synthesis error", e)
            throw e
        }
    }

    /**
     * 播放音频数据
     */
    fun playAudio(audioData: FloatArray) {
        audioTrack?.apply {
            if (playState != AudioTrack.PLAYSTATE_PLAYING) {
                play()
            }
            write(audioData, 0, audioData.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    /**
     * 合成并直接播放
     */
    suspend fun synthesizeAndPlay(text: String) {
        val audio = synthesize(text)
        playAudio(audio)
    }

    /**
     * 停止播放
     */
    fun stop() {
        audioTrack?.apply {
            stop()
            reloadStaticData()
        }
    }

    /**
     * 模拟合成（用于演示）
     * 实际项目中替换为 MNN 推理
     */
    private fun mockSynthesize(text: String): FloatArray {
        // 生成一个简单的正弦波作为占位音频
        val durationSeconds = text.length * 0.15f // 粗略估计时长
        val numSamples = (sampleRate * durationSeconds).toInt()
        val audio = FloatArray(numSamples)

        val frequency = 220.0 // 基础频率
        for (i in audio.indices) {
            val t = i / sampleRate.toFloat()
            // 简单的正弦波 + 一些谐波模拟语音
            audio[i] = (
                Math.sin(2 * Math.PI * frequency * t) * 0.5 +
                Math.sin(2 * Math.PI * frequency * 2 * t) * 0.25 +
                Math.sin(2 * Math.PI * frequency * 0.5 * t) * 0.25
            ).toFloat() * 0.3f

            // 添加简单的包络
            val envelope = when {
                i < sampleRate * 0.05 -> i / (sampleRate * 0.05f)
                i > numSamples - sampleRate * 0.05 -> (numSamples - i) / (sampleRate * 0.05f)
                else -> 1.0f
            }
            audio[i] *= envelope
        }

        return audio
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null

        if (nativeHandle != 0L) {
            // nativeReleaseTTS(nativeHandle)
            nativeHandle = 0
        }
    }

    // JNI 方法声明
    // private external fun nativeCreateTTS(modelPath: String, configPath: String?): Long
    // private external fun nativeSynthesize(handle: Long, text: String, speakerId: Int, speed: Float): FloatArray
    // private external fun nativeGetSampleRate(handle: Long): Int
    // private external fun nativeGetNumSpeakers(handle: Long): Int
    // private external fun nativeReleaseTTS(handle: Long)

    companion object {
        const val TAG = "TTSEngine"
    }
}
