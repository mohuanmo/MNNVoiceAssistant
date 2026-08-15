package com.example.mnnvoiceassistant.service

import android.content.Intent
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import com.example.mnnvoiceassistant.engine.TTSEngine
import com.example.mnnvoiceassistant.model.ModelManager
import kotlinx.coroutines.*

/**
 * MNN TTS 引擎服务
 * 实现 Android 系统 TTS 接口，可被其他应用调用
 */
class MNNTTSEngine : TextToSpeechService() {

    private lateinit var ttsEngine: TTSEngine
    private lateinit var modelManager: ModelManager
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        modelManager = ModelManager(this)
        ttsEngine = TTSEngine(this, modelManager)
        Log.i(TAG, "MNN TTS Engine created")
    }

    override fun onGetLanguage(): Array<String> {
        // 返回支持的语言: [语言, 国家, 变体]
        return arrayOf("zh", "CN", "")
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        return when {
            lang == "zh" -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            lang == "en" -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            lang == "ja" -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            lang == "ko" -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return

        val text = request.charSequenceText?.toString() ?: return

        if (!modelManager.isTTSModelLoaded()) {
            callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
            return
        }

        try {
            // 设置音频格式
            val sampleRate = 22050
            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)

            // 合成音频
            scope.launch {
                try {
                    val audioData = ttsEngine.synthesize(text)

                    // 将 float 转换为 PCM 16bit
                    val pcmData = floatToPcm16(audioData)
                    callback.audioAvailable(pcmData, 0, pcmData.size)
                    callback.done()
                } catch (e: Exception) {
                    Log.e(TAG, "TTS synthesis failed", e)
                    callback.error(TextToSpeech.ERROR_SYNTHESIS)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS error", e)
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
        }
    }

    override fun onStop() {
        ttsEngine.stop()
    }

    private fun floatToPcm16(floatData: FloatArray): ByteArray {
        val pcmData = ByteArray(floatData.size * 2)
        for (i in floatData.indices) {
            val sample = (floatData[i] * 32767).toInt().coerceIn(-32768, 32767)
            pcmData[i * 2] = (sample and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return pcmData
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        ttsEngine.release()
    }

    companion object {
        const val TAG = "MNNTTSEngine"
    }
}
