package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.imageeditor.SlideIndexImageEditorActivity
import com.slideindex.app.util.TrampolineResultPort
import java.io.File
import java.util.UUID

/**
 * 「闪念编辑浮窗里的图片块点 ✎」的**中转 Activity**（§0.16.22）。
 *
 * 为什么必须中转（与 [StashComposerImageTrampolineActivity] 同一个理由）：
 * 面板是 overlay 窗，它的 Compose 树里**没有** `ActivityResultRegistryOwner`，
 * 拿不到内置编辑器保存后的结果。所以由本中转 Activity 用
 * `registerForActivityResult` 去起编辑器、再接它的 `RESULT_OK`，
 * 最后把"编辑后的图片路径"通过 [TrampolineResultPort] 交回发起方。
 *
 * 为什么要交**文件路径**而不是 Bitmap：Bitmap 走 Binder 有 1MB 级事务上限，
 * 一张 2048 长边的 PNG 必定 `TransactionTooLargeException`。
 *
 * ⚠️ **必须先把原图拷一份再交给编辑器**：编辑器读源图之后会把它删掉
 * （`loadSourceBitmap` 里那一步"读过就删"是为跨进程传图设计的）。直接给它原文件的话，
 * 用户**取消**编辑回来，那一块的图已经没了 —— 而取消的约定是"什么都不动"。
 * 所以：原图 → 拷贝 → 编辑器；拷贝在 [onDestroy] 里删。
 *
 * 结果的两种含义（发起方按这个分流）：
 * - `EXTRA_CANCELLED = true` → 用户取消 / 编辑器没交出结果 → **发起方什么都不动**；
 * - 否则 [EXTRA_PATH] 是编辑后的图片（PNG）在**本 App cache** 里的绝对路径
 *   → 发起方把它换进那个图片块，并删掉被替换掉的临时文件。
 */
class StashEditImageTrampolineActivity : ComponentActivity() {

    /** 给编辑器的**拷贝**（编辑器会把它删掉，所以不能是原图 —— 见类注释）。 */
    private var editorInputPath: File? = null

    /** 编辑器把结果写到这里（见 `SlideIndexImageEditorActivity.EXTRA_RESULT_OUTPUT_PATH`）。 */
    private var outputPath: String? = null

    /** 真的交给发起方的那个文件：`onDestroy` 里靠它分辨"该留的结果"和"该删的残料"。 */
    private var deliveredPath: String? = null

    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val path = outputPath?.takeIf { result.resultCode == RESULT_OK && File(it).length() > 0L }
        deliver(path)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH)?.takeIf { it.isNotBlank() }
        val source = sourcePath?.let { File(it) }
        if (source == null || !source.isFile || source.length() <= 0L) {
            deliver(null)
            return
        }
        // 拷贝原图（**保留后缀**：编辑器按文件内容解码，后缀只是为了让日志/缓存好认）。
        val extension = source.name.substringAfterLast('.', "png").takeIf { it.isNotEmpty() } ?: "png"
        val input = File(File(cacheDir, CacheDirName).apply { mkdirs() }, "src_${UUID.randomUUID()}.$extension")
        val copied = runCatching {
            source.inputStream().use { inputStream ->
                input.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
            }
            input.length() > 0L
        }.onFailure { Log.w(TAG, "copy source image failed: $sourcePath", it) }.getOrDefault(false)
        if (!copied) {
            runCatching { input.delete() }
            deliver(null)
            return
        }
        editorInputPath = input
        outputPath = File(File(cacheDir, CacheDirName).apply { mkdirs() }, "${UUID.randomUUID()}.png")
            .absolutePath
        val launched = runCatching {
            SlideIndexImageEditorActivity.launchForResult(
                context = this,
                imageCachePath = input.absolutePath,
                resultOutputPath = outputPath!!,
            )
        }.onFailure { Log.w(TAG, "launch editor failed", it) }.isSuccess
        if (!launched) deliver(null)
    }

    override fun onDestroy() {
        // 两份中转文件都只属于本次编辑：源图拷贝（编辑器多半已经删过，再删一次无害）
        // 与**没有交出去的结果**（取消 / 写失败时的半成品）。
        editorInputPath?.let { runCatching { it.delete() } }
        editorInputPath = null
        val delivered = deliveredPath
        outputPath?.let { if (it != delivered) runCatching { File(it).delete() } }
        outputPath = null
        super.onDestroy()
    }

    /** 把结果交回发起方（null = 取消），然后收掉自己。 */    private fun deliver(path: String?) {
        deliveredPath = path
        val token = intent.getStringExtra(TrampolineResultPort.EXTRA_TOKEN).orEmpty()
        if (token.isNotEmpty()) {
            TrampolineResultPort.deliver(
                this,
                token,
                Bundle().apply {
                    putBoolean(TrampolineResultPort.EXTRA_CANCELLED, path == null)
                    if (path != null) putString(EXTRA_PATH, path)
                },
            )
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_PATH = "stash_edit_image_path"
        private const val EXTRA_SOURCE_PATH = "stash_edit_image_source"
        private const val CacheDirName = "stash_composer_images"
        private const val TAG = "StashEditImageTrampoline"

        /**
         * 从 overlay 侧发起"编辑这张图"：注册一次性回调 → 起本 Activity → 结果回来时给
         * **编辑后的图片绝对路径**（null = 取消 / 失败）。
         *
         * ⚠️ 回调在主线程（`TrampolineResultPort` 是进程内静态表，本 Activity 与发起方同进程）。
         */
        fun launch(context: Context, sourcePath: String, onResult: (String?) -> Unit) {
            val appContext = context.applicationContext
            val token = UUID.randomUUID().toString()
            TrampolineResultPort.register(appContext, token) { payload ->
                val cancelled = payload.getBoolean(TrampolineResultPort.EXTRA_CANCELLED, true)
                onResult(if (cancelled) null else payload.getString(EXTRA_PATH))
            }
            val intent = Intent(appContext, StashEditImageTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(TrampolineResultPort.EXTRA_TOKEN, token)
                putExtra(EXTRA_SOURCE_PATH, sourcePath)
            }
            runCatching { appContext.startActivity(intent) }.onFailure {
                TrampolineResultPort.clearToken(token)
                onResult(null)
            }
        }
    }
}
