package com.jizai.tvbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 语音输入：Android SpeechRecognizer，中文识别。
 * 无语音服务的设备（部分国行电视）走 onError 优雅降级，不抛异常。
 */
object VoiceInput {

    fun isAvailable(act: Activity): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(act)
    } catch (_: Exception) {
        false
    }

    fun start(act: Activity, onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!isAvailable(act)) {
            onError("此设备不支持语音识别")
            return
        }
        val sr = try {
            SpeechRecognizer.createSpeechRecognizer(act)
        } catch (_: Exception) {
            onError("语音服务启动失败")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onError(error: Int) {
                destroyQuietly(sr)
                onError("没听清，请再试一次")
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                destroyQuietly(sr)
                if (text.isNotBlank()) onResult(text) else onError("没听清，请再试一次")
            }
        })
        try {
            sr.startListening(intent)
        } catch (_: Exception) {
            destroyQuietly(sr)
            onError("语音服务启动失败")
        }
    }

    private fun destroyQuietly(sr: SpeechRecognizer) {
        try {
            sr.destroy()
        } catch (_: Exception) {
        }
    }
}
