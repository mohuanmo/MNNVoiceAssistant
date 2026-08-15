package com.example.mnnvoiceassistant.service

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.service.voice.VoiceInteractionService
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.example.mnnvoiceassistant.R
import com.example.mnnvoiceassistant.engine.ASREngine
import com.example.mnnvoiceassistant.engine.TTSEngine
import com.example.mnnvoiceassistant.model.ModelManager
import kotlinx.coroutines.*

/**
 * 数字助理交互服务
 * 当用户通过长按Home键或语音唤醒触发系统助理时，会启动此服务
 */
class VoiceAssistantInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "VoiceAssistantInteractionService ready")
    }
}

/**
 * 数字助理会话
 * 提供语音交互的 UI 和逻辑
 */
class VoiceAssistantSession(context: Context) : VoiceInteractionSession(context) {

    private lateinit var asrEngine: ASREngine
    private lateinit var ttsEngine: TTSEngine
    private lateinit var modelManager: ModelManager
    private var contentView: View? = null
    private var tvStatus: TextView? = null
    private var tvResult: TextView? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        modelManager = ModelManager(context)
        asrEngine = ASREngine(context, modelManager)
        ttsEngine = TTSEngine(context, modelManager)
    }

    override fun onCreateContentView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.assistant_overlay, null)
        contentView = view
        tvStatus = view.findViewById(R.id.tv_assistant_status)
        tvResult = view.findViewById(R.id.tv_assistant_result)
        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(TAG, "Assistant session shown")

        tvStatus?.text = "正在聆听..."
        tvResult?.text = ""

        // 启动语音识别
        if (modelManager.isASRModelLoaded()) {
            scope.launch(Dispatchers.IO) {
                try {
                    asrEngine.startStreamingRecognition { text, isFinal ->
                        scope.launch(Dispatchers.Main) {
                            tvResult?.text = text
                            if (isFinal) {
                                tvStatus?.text = "识别完成"
                                // 可以在这里接入 LLM 进行回复
                                handleCommand(text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ASR error in assistant", e)
                }
            }
        } else {
            tvStatus?.text = "请先导入 ASR 模型"
        }
    }

    override fun onHide() {
        super.onHide()
        asrEngine.stopStreamingRecognition()
        ttsEngine.stop()
    }

    private fun handleCommand(text: String) {
        // 简单的命令处理逻辑
        val response = when {
            text.contains("时间") || text.contains("几点") -> {
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                    .format(java.util.Date())
                "现在是 $time"
            }
            text.contains("天气") -> "抱歉，我目前无法获取实时天气信息"
            text.contains("你好") || text.contains("您好") -> "你好！我是你的 MNN 语音助手"
            else -> "你说的是: $text"
        }

        tvResult?.text = response

        // TTS 回复
        scope.launch(Dispatchers.IO) {
            try {
                val audio = ttsEngine.synthesize(response)
                withContext(Dispatchers.Main) {
                    ttsEngine.playAudio(audio)
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS error in assistant", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        asrEngine.release()
        ttsEngine.release()
    }

    companion object {
        const val TAG = "VoiceAssistantSession"
    }
}

/**
 * 会话服务工厂
 */
class VoiceAssistantSessionService : VoiceInteractionSessionService() {
    override fun onCreateSession(args: Bundle?): VoiceInteractionSession {
        return VoiceAssistantSession(this)
    }
}
