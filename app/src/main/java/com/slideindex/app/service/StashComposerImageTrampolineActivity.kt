package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.util.TrampolineResultPort
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「记一条」里加图片的**中转 Activity**（§0.16.12）。
 *
 * 为什么要中转：**overlay 窗里不能直接拉起系统选图** —— `rememberLauncherForActivityResult` 需要一个
 * `ActivityResultRegistryOwner`，而 overlay 的 Compose 树里没有（App 里所有这类调用都在 `ui` 包下的
 * Activity 里，overlay 侧一个都没有）。所以 overlay 侧的选图一律走 trampoline。
 *
 * 选图用 `PickMultipleVisualMedia`（**系统相册选择器，不需要任何读图权限**，设备没有照片选择器时会自动
 * 回退到 `ACTION_OPEN_DOCUMENT`）。
 *
 * ⚠️ 关键细节：**在 trampoline 里就把图解码并落到 App 自己的 cache**，只回传文件路径。
 * 相册选择器给的 URI 读权限是**绑在发起 Activity 的生命周期**上的，trampoline `finish()` 之后
 * 再让面板去读那个 URI 可能就 EACCES 了；落成自己的文件则永远读得到，也绕开了 Bundle 不能塞大图的问题。
 */
class StashComposerImageTrampolineActivity : ComponentActivity() {

    private val maxItems: Int
        get() = intent.getIntExtra(EXTRA_MAX_ITEMS, DefaultMaxItems).coerceIn(2, 20)

    private val pickLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(DefaultMaxItems)
    ) { uris ->
        if (uris.isEmpty()) {
            deliver(emptyList())
        } else {
            lifecycleScope.launch {
                val paths = withContext(Dispatchers.IO) {
                    uris.take(maxItems).mapNotNull { uri -> copyToCache(uri) }
                }
                if (paths.isEmpty()) {
                    Toast.makeText(
                        this@StashComposerImageTrampolineActivity,
                        R.string.stash_pin_add_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                deliver(paths)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { pickLauncher.launch(imageOnlyRequest) }.onFailure {
            Toast.makeText(this, R.string.stash_pin_add_failed, Toast.LENGTH_SHORT).show()
            deliver(emptyList())
        }
    }

    private fun deliver(paths: List<String>) {
        val token = intent.getStringExtra(TrampolineResultPort.EXTRA_TOKEN).orEmpty()
        if (token.isNotEmpty()) {
            TrampolineResultPort.deliver(
                this,
                token,
                Bundle().apply {
                    putStringArrayList(EXTRA_PATHS, ArrayList(paths))
                    putBoolean(TrampolineResultPort.EXTRA_CANCELLED, paths.isEmpty())
                },
            )
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    /** 解码（长边 ≤ 2048）后写成 App cache 里的 JPEG，返回绝对路径。 */
    private fun copyToCache(uri: Uri): String? = runCatching {
        val bitmap = decodeSampled(uri) ?: return null
        val dir = File(cacheDir, CacheDirName).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        bitmap.recycle()
        file.absolutePath
    }.getOrNull()

    private fun decodeSampled(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MaxDimension || bounds.outHeight / sample > MaxDimension) {
            sample *= 2
        }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }
    }.getOrNull()

    companion object {
        const val EXTRA_PATHS = "stash_composer_image_paths"
        private const val EXTRA_MAX_ITEMS = "stash_composer_image_max"
        private const val DefaultMaxItems = 9
        private const val CacheDirName = "stash_composer_images"
        private const val MaxDimension = 2048

        /** 照片选择器请求（`PickVisualMediaRequest(ImageOnly)`，避免把视频也选进来）。 */
        private val imageOnlyRequest =
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
            )

        /**
         * 从 overlay 侧发起选图：注册一次性回调 → 启动中转 Activity → 结果回来时回调路径列表
         * （空列表 = 用户取消或全部解码失败）。
         */
        fun launch(context: Context, onPicked: (List<String>) -> Unit) {
            val appContext = context.applicationContext
            val token = UUID.randomUUID().toString()
            TrampolineResultPort.register(appContext, token) { payload ->
                onPicked(payload.getStringArrayList(EXTRA_PATHS).orEmpty())
            }
            val intent = Intent(appContext, StashComposerImageTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(TrampolineResultPort.EXTRA_TOKEN, token)
            }
            runCatching { appContext.startActivity(intent) }.onFailure {
                TrampolineResultPort.clearToken(token)
                onPicked(emptyList())
            }
        }
    }
}

/**
 * 把 trampoline 落下来的临时图读成 Bitmap（存条目时用）。
 *
 * 与 [StashComposerImageTrampolineActivity] 同一套降采样口径，避免同一张图两条路径解出不同尺寸。
 */
internal fun decodeStashImageFile(path: String, maxDimension: Int = 2048): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
        sample *= 2
    }
    BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}.getOrNull()
