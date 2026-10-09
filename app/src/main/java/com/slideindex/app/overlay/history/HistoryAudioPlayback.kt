package com.slideindex.app.overlay.history

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 语音块的**单例播放器**（§0.16.21）。
 *
 * 为什么是单例而不是每个块一个 `MediaPlayer`：
 * ① 「同一时刻只该有一条在响」是产品要求，也是用户预期 —— 多条一起响是纯 bug；
 * ② 播放器要能被"切条目 / 关面板 / 开始录音"这些**本组件之外**的事件统一停掉，
 *    分散在每块里的实例没法被外部叫停。
 * 所以这里持有唯一的 `MediaPlayer`，并对外暴露 [playingPath]（**哪一条在放**）。
 *
 * 用 `MediaPlayer` 而不是 ExoPlayer：本仓库不许为了这个功能引新依赖，而本地 m4a 的
 * `setDataSource(path)` + `prepare()` + `start()` 就是 `MediaPlayer` 最拿手的场景。
 *
 * ⚠️ 所有方法都必须在**主线程**调用（`MediaPlayer` 的既有约束，也是这些调用点的实际情况：
 * 全部来自 Compose 的点击回调）。
 */
internal object HistoryAudioPlayback {
    private const val TAG = "HistoryAudioPlayback"

    /** 进度轮询间隔：200ms 足够让"播放中"看起来是连续走动的，又不至于每帧写状态。 */
    private const val TickIntervalMs = 200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: MediaPlayer? = null
    private var tickJob: Job? = null

    /** 正在播放的那份音频的**绝对路径**；null = 没在放。UI 靠它画"当前播放的是哪条"。 */
    var playingPath by mutableStateOf<String?>(null)
        private set

    /** 当前播放位置（毫秒），由 [TickIntervalMs] 的心跳推进。 */
    var positionMs by mutableStateOf(0L)
        private set

    fun isPlaying(path: String): Boolean = playingPath != null && playingPath == path

    /**
     * 点一下语音块：**在放就停，没在放就从头放**（切换 = 先停旧的，所以不会两条一起响）。
     *
     * [onError] 在"文件不在 / 解不开 / 播到一半出错"时回调（UI 自己决定怎么提示）。
     */
    fun toggle(path: String, onError: () -> Unit) {
        if (path.isBlank() || !File(path).exists()) {
            stop()
            onError()
            return
        }
        if (isPlaying(path)) {
            stop()
            return
        }
        stop()
        val created = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(path)
                setOnCompletionListener { stop() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "playback error what=$what extra=$extra path=$path")
                    // 不要在 MediaPlayer 自己的回调里 release 它：回到主线程队列再收尾。
                    scope.launch {
                        stop()
                        onError()
                    }
                    true
                }
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "prepare/start failed: $path", it) }.getOrNull()
        if (created == null) {
            stop()
            onError()
            return
        }
        player = created
        playingPath = path
        positionMs = 0L
        tickJob = scope.launch {
            while (true) {
                delay(TickIntervalMs)
                val current = player ?: break
                positionMs = runCatching { current.currentPosition.toLong() }.getOrDefault(0L)
            }
        }
    }

    /** 停掉当前播放（没有在放时是空操作）。切条目 / 关面板 / 开始录音都调它。 */
    fun stop() {
        tickJob?.cancel()
        tickJob = null
        val current = player
        player = null
        playingPath = null
        positionMs = 0L
        if (current != null) {
            runCatching { if (current.isPlaying) current.stop() }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
    }
}
