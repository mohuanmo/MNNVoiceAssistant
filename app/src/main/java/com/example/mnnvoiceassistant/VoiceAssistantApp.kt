package com.example.mnnvoiceassistant

import android.app.Application
import android.util.Log

class VoiceAssistantApp : Application() {

    companion object {
        const val TAG = "MNNVoiceAssistant"
        lateinit var instance: VoiceAssistantApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "MNN Voice Assistant App initialized")
    }
}
