package com.slideindex.app.voice

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.slideindex.app.R
import com.slideindex.app.util.ForegroundNotificationChannels
import java.util.Locale

/**
 * 语音输入会话的前台服务（`microphone` 类型）。
 *
 * **为什么必须是前台服务**：面板/输入槽都是 overlay 窗，App 进程对系统来说不在前台，
 * 直连麦克风会被判成后台录音而拒绝 —— 前台服务 + `microphone` 类型是唯一的正路
 * （overlay 权限让 App 可以在后台启动这个前台服务）。
 *
 * 识别用系统 [SpeechRecognizer]：优先**端上识别**（`createOnDeviceSpeechRecognizer`，离线、
 * 不把语音送云端），设备不支持时退回标准识别器。
 */
class StashVoiceInputService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false
    private var started = false
    /** 正在主动收尾：此期间的 `onError(ERROR_CLIENT)` 是 `cancel()` 的回声，不要当成失败报给用户。 */
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening(cancelled = true)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startListening()
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (started) return
        if (!SpeechRecognizer.isRecognitionAvailable(this) && !isOnDeviceAvailable()) {
            Log.w(TAG, "no recognition service")
            StashVoiceSession.finishWithError(R.string.stash_voice_error_unavailable)
            stopSelf()
            return
        }
        started = true
        if (!promoteToForeground()) {
            // 渠道不可用时不前台化：这时录音大概率也拿不到，直接给失败反馈，别让用户对着空气说话。
            StashVoiceSession.finishWithError(R.string.stash_voice_error_unavailable)
            stopSelf()
            return
        }
        StashVoiceSession.beginSession()
        createRecognizer()?.let { engine ->
            runCatching { engine.startListening(buildIntent()) }
                .onFailure { error ->
                    Log.e(TAG, "startListening failed", error)
                    StashVoiceSession.finishWithError(R.string.stash_voice_error_generic)
                    stopSelf()
                }
        } ?: run {
            StashVoiceSession.finishWithError(R.string.stash_voice_error_unavailable)
            stopSelf()
        }
    }

    private fun isOnDeviceAvailable(): Boolean =
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(this) }.getOrDefault(false)

    private fun createRecognizer(): SpeechRecognizer? {
        if (recognizer != null) return recognizer
        val engine = runCatching {
            if (isOnDeviceAvailable()) {
                onDevice = true
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                onDevice = false
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        }.getOrNull()
        if (engine == null) {
            StashVoiceSession.finishWithError(R.string.stash_voice_error_unavailable)
            return null
        }
        engine.setRecognitionListener(listener)
        recognizer = engine
        return engine
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // 跟随系统语言；识别器不认识时会自己回退。
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            if (onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (stopping) return
            val text = partialResults?.bestResult() ?: return
            if (text.isNotBlank()) StashVoiceSession.updatePartial(text)
        }

        override fun onResults(results: Bundle?) {
            if (stopping) return
            val text = results?.bestResult().orEmpty()
            if (text.isBlank()) {
                StashVoiceSession.finishWithError(R.string.stash_voice_error_no_match)
            } else {
                StashVoiceSession.finishWithResult(text)
            }
            stopSelf()
        }

        override fun onError(error: Int) {
            if (stopping) return
            Log.w(TAG, "recognition error $error")
            StashVoiceSession.finishWithError(errorMessageRes(error))
            stopSelf()
        }
    }

    private fun Bundle.bestResult(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    private fun errorMessageRes(error: Int): Int = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> R.string.stash_voice_error_no_match

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        -> R.string.stash_voice_error_network

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.stash_voice_error_permission

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_CLIENT,
        -> R.string.stash_voice_error_generic

        else -> R.string.stash_voice_error_generic
    }

    /** 起前台通知。返回 false 表示渠道不可用（此时不前台化）。 */
    private fun promoteToForeground(): Boolean {
        val channel = ForegroundNotificationChannels.ensureUsable(
            context = this,
            id = CHANNEL_ID,
            name = getString(R.string.stash_voice_channel_name),
            importance = NotificationManager.IMPORTANCE_LOW,
            tag = TAG,
        )
        if (!channel.canPromote) {
            Log.e(TAG, "语音输入通知渠道不可用")
            return false
        }
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, StashVoiceInputService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.stash_voice_notification_title))
            .setContentText(getString(R.string.stash_voice_notification_body))
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.stash_voice_cancel), cancelIntent)
            .build()
        runCatching { startForeground(NOTIFY_ID, notification) }
            .onFailure { error ->
                Log.e(TAG, "startForeground failed", error)
                return false
            }
        return true
    }

    private fun stopListening(cancelled: Boolean) {
        stopping = true
        val engine = recognizer
        recognizer = null
        runCatching { engine?.stopListening() }
        runCatching { engine?.cancel() }
        runCatching { engine?.destroy() }
        if (cancelled) StashVoiceSession.cancelSession()
        if (started) {
            started = false
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        }
    }

    override fun onDestroy() {
        stopListening(cancelled = true)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.slideindex.app.action.STASH_VOICE_START"
        const val ACTION_STOP = "com.slideindex.app.action.STASH_VOICE_STOP"

        private const val TAG = "StashVoiceInput"
        private const val CHANNEL_ID = "stash_voice_input"
        private const val NOTIFY_ID = 24_200

        /** 语音输入的麦克风权限是否已给（给 UI 判断要不要提示）。 */
        fun hasPermission(context: Context): Boolean =
            StashVoiceController.hasRecordAudioPermission(context)
    }
}
