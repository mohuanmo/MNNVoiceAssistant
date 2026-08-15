package com.example.mnnvoiceassistant.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.mnnvoiceassistant.engine.ASREngine
import com.example.mnnvoiceassistant.model.ModelManager

/**
 * 后台 ASR 服务
 * 用于持续监听唤醒词或进行后台语音识别
 */
class ASRService : Service() {

    private lateinit var asrEngine: ASREngine
    private lateinit var modelManager: ModelManager
    private val binder = ASRBinder()

    inner class ASRBinder : Binder() {
        fun getService(): ASRService = this@ASRService
    }

    override fun onCreate() {
        super.onCreate()
        modelManager = ModelManager(this)
        asrEngine = ASREngine(this, modelManager)
        Log.i(TAG, "ASRService created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun startRecognition(callback: (String, Boolean) -> Unit) {
        asrEngine.startStreamingRecognition(callback)
    }

    fun stopRecognition(): String {
        return asrEngine.stopStreamingRecognition()
    }

    override fun onDestroy() {
        super.onDestroy()
        asrEngine.release()
    }

    companion object {
        const val TAG = "ASRService"
    }
}
