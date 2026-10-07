package com.slideindex.app.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.slideindex.app.util.finishWithoutTransition

/**
 * 录音权限的瞬发跳板。
 *
 * overlay 窗**不能**弹运行时权限（权限对话框只能由 Activity 发起），所以三个输入面的麦克风
 * 第一次点都是走这里：已授权时什么都不做；未授权时拉系统对话框，**授权后直接开始听**
 * （这样"第一次点麦克风"也是一步到位，不用点两次）。
 */
class StashVoicePermissionActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        if (granted) {
            StashVoiceController.start(applicationContext)
        }
        finishWithoutTransition()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (StashVoiceController.hasRecordAudioPermission(this)) {
            finishWithoutTransition()
            return
        }
        requestPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        /** 权限确实缺失时才跳；已授权直接返回 true（不产生任何跳转）。 */
        fun ensureGranted(context: Context): Boolean {
            if (StashVoiceController.hasRecordAudioPermission(context)) return true
            val intent = Intent(context, StashVoicePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching { context.startActivity(intent) }.isSuccess
        }
    }
}
