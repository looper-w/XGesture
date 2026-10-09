package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.imageeditor.SlideIndexImageEditorActivity
import com.slideindex.app.overlay.StashPanelExternalUi
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
 *
 * ---
 * ## §0.16.23 回归修复：结果**必须**经 [editorLauncher] 回来
 *
 * 用户实测「涂鸦后保存，图片没有被替换」，而 trampoline 确实起过也结束了。根因是：
 * 这个 Activity 注册了 [editorLauncher]，但 `onCreate` 里起编辑器用的是
 * `SlideIndexImageEditorActivity.launchForResult(...)`（内部一句 `context.startActivity`）——
 * **没有走 launcher**，于是 `requestCode` 是 -1，编辑器的 `RESULT_OK` 永远不会回到这里：
 * `deliver()` 从不执行（面板收不到任何回调 → 挂起态永远不恢复），
 * 而 `onDestroy` 又把编辑器刚写好、没人认领的结果 PNG 当"残料"删掉。
 * 现在起编辑器一律 `editorLauncher.launch(SlideIndexImageEditorActivity.resultIntent(...))`。
 *
 * ## §0.16.23：`onDestroy` 兜底恢复
 *
 * 无论有没有结果，本 Activity 销毁时**一定**把面板恢复一次（[StashPanelExternalUi.resumeAfterExternalUi]，
 * 幂等）：这就是"没有任何路径能让 resume 丢失"里最后那道闸。
 * 另外 `onResume` 也是兜底：编辑器已经压过我们（[pausedWithEditor]）、现在回到前台却还没有结果，
 * 说明结果/生命周期丢了 → 直接按取消交付一次，面板立刻回来。
 */
class StashEditImageTrampolineActivity : ComponentActivity() {

    /** 给编辑器的**拷贝**（编辑器会把它删掉，所以不能是原图 —— 见类注释）。 */
    private var editorInputPath: File? = null

    /** 编辑器把结果写到这里（见 `SlideIndexImageEditorActivity.EXTRA_RESULT_OUTPUT_PATH`）。 */
    private var outputPath: String? = null

    /** 真的交给发起方的那个文件：`onDestroy` 里靠它分辨"该留的结果"和"该删的残料"。 */
    private var deliveredPath: String? = null

    /** 是否已经交付过（幂等闸：`onResume` 兜底 / launcher 回调 / `onDestroy` 都可能在同一次里跑）。 */
    private var delivered = false

    /** 编辑器是否已经真的压过我们（`onPause`）—— `onResume` 兜底的判据，见类注释。 */
    private var pausedWithEditor = false

    /** 是否已经起过编辑器（没起过就谈不上"结果丢了"）。 */
    private var editorLaunched = false

    /* ---------- 面板挂起租约（§0.16.23：防止"结果丢了 → 面板冻死"） ---------- */

    private val leaseHandler = Handler(Looper.getMainLooper())
    private var leaseRunning = false
    private val leaseTick = object : Runnable {
        override fun run() {
            if (!leaseRunning) return
            StashPanelExternalUi.renewExternalUiLease(owner = LEASE_OWNER)
            leaseHandler.postDelayed(this, LeaseIntervalMs)
        }
    }

    /** `onResume` 兜底交付（延迟一点跑，见 [onResume]）。 */
    private val resumeFallback = Runnable {
        if (!delivered) deliver(null)
    }

    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val file = outputPath?.let { File(it) }
        Log.i(
            LOG_TAG,
            "trampoline onActivityResult resultCode=${result.resultCode} outputPath=$outputPath " +
                "文件存在=${file?.isFile == true} 大小=${file?.length() ?: 0L}",
        )
        val path = outputPath?.takeIf { result.resultCode == RESULT_OK && File(it).length() > 0L }
        deliver(path)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 先把租约往后推一次：从"面板挂起"到"编辑器拿到前台"之间（onPause 之前）也有看门狗覆盖。
        StashPanelExternalUi.renewExternalUiLease(owner = LEASE_OWNER)
        StashEditImageTrampolineActivity.editInFlight = true
        val sourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH)?.takeIf { it.isNotBlank() }
        val source = sourcePath?.let { File(it) }
        Log.i(
            LOG_TAG,
            "trampoline onCreate sourcePath=$sourcePath 源文件存在=${source?.isFile == true} " +
                "大小=${source?.length() ?: 0L} token=${intent.getStringExtra(TrampolineResultPort.EXTRA_TOKEN) != null}",
        )
        if (source == null || !source.isFile || source.length() <= 0L) {
            // ⚠️ 这条早退也会"没有 EXTRA_RESULT_OUTPUT_PATH 可言"，必须在日志里能一眼认出来：
            // 它意味着调用方给的源路径不可读（文件名没拼成绝对路径 / 临时文件被清了）。
            Log.w(LOG_TAG, "trampoline 早退：源图不可读（sourcePath=$sourcePath）→ 按取消交付")
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
        }.onFailure { Log.w(LOG_TAG, "copy source image failed: $sourcePath", it) }.getOrDefault(false)
        if (!copied) {
            runCatching { input.delete() }
            Log.w(LOG_TAG, "trampoline 早退：拷贝源图失败（$sourcePath）→ 按取消交付")
            deliver(null)
            return
        }
        editorInputPath = input
        outputPath = File(File(cacheDir, CacheDirName).apply { mkdirs() }, "${UUID.randomUUID()}.png")
            .absolutePath
        // ⚠️ 起编辑器**必须**走 `editorLauncher`（§0.16.23）：`startActivity` 拿不到结果，
        // 结果丢了的直接后果就是"保存了但图片没换 + 面板挂起不恢复"。
        val intent = SlideIndexImageEditorActivity.resultIntent(
            context = this,
            imageCachePath = input.absolutePath,
            resultOutputPath = outputPath!!,
        )
        Log.i(
            LOG_TAG,
            "trampoline launch editor input=${input.absolutePath} output=${outputPath} " +
                // 逐字核实 extra 真的 put 进去了（用户报的 (甲) 假设就靠这一行判读）。
                "extra=${intent.hasExtra(SlideIndexImageEditorActivity.EXTRA_RESULT_OUTPUT_PATH)} " +
                "extra值=${intent.getStringExtra(SlideIndexImageEditorActivity.EXTRA_RESULT_OUTPUT_PATH)}",
        )
        val launched = runCatching { editorLauncher.launch(intent) }
            .onFailure { Log.w(LOG_TAG, "launch editor failed", it) }
            .isSuccess
        editorLaunched = launched
        if (!launched) {
            Log.w(LOG_TAG, "trampoline 早退：起编辑器抛异常 → 按取消交付")
            deliver(null)
        }
    }

    override fun onPause() {
        super.onPause()
        // 编辑器（或别的外部 UI）压上来了：这时候面板必须保持挂起 → 开始续租，看门狗不会误伤。
        if (editorLaunched) {
            pausedWithEditor = true
            startLease()
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台 = 外部 UI 已经不在我们上面了：停止续租，让看门狗重新变成"能自愈"的状态。
        stopLease()
        // 编辑器已经压过我们、现在回来了却还没有结果 → 结果或生命周期丢了（老代码就是这条路径把面板冻死）。
        // 延迟一小会儿再按取消交付：结果回调走的是 `ON_START`（比 `onResume` 早），正常情况到这里
        // 已经 `delivered`；万一某个 ROM 的顺序不同，这 [ResumeFallbackDelayMs] 也给真结果留出到达窗口
        // （真结果先到的话 `deliver` 已经把它交付了，这里的一句会幂等空转）。
        if (editorLaunched && pausedWithEditor && !delivered) {
            Log.w(
                LOG_TAG,
                "trampoline onResume 兜底：编辑器已退出但还没有结果 → ${ResumeFallbackDelayMs}ms 后按取消交付",
            )
            leaseHandler.postDelayed(resumeFallback, ResumeFallbackDelayMs)
        }
    }

    override fun onDestroy() {
        // 两份中转文件都只属于本次编辑：源图拷贝（编辑器多半已经删过，再删一次无害）
        // 与**没有交出去的结果**（取消 / 写失败时的半成品）。
        editorInputPath?.let { runCatching { it.delete() } }
        editorInputPath = null
        val kept = deliveredPath
        outputPath?.let { if (it != kept) runCatching { File(it).delete() } }
        outputPath = null
        stopLease()
        StashEditImageTrampolineActivity.editInFlight = false
        // §0.16.23 最后一道闸：无论有没有结果、走的是哪条路径，销毁时保证恢复一次（幂等）。
        StashPanelExternalUi.resumeAfterExternalUi(caller = "trampoline-onDestroy")
        super.onDestroy()
    }

    /** 把结果交回发起方（null = 取消），然后收掉自己。 */
    private fun deliver(path: String?) {
        if (delivered) {
            Log.i(LOG_TAG, "trampoline deliver 重复调用（幂等忽略） path=$path")
            return
        }
        delivered = true
        deliveredPath = path
        leaseHandler.removeCallbacks(resumeFallback)
        Log.i(
            LOG_TAG,
            "trampoline deliver cancelled=${path == null} path=$path " +
                "文件存在=${path != null && File(path).isFile} 大小=${path?.let { File(it).length() } ?: 0L}",
        )
        val token = intent.getStringExtra(TrampolineResultPort.EXTRA_TOKEN).orEmpty()
        if (token.isEmpty()) {
            Log.w(LOG_TAG, "trampoline deliver：intent 里没有 token → 发起方收不到结果（只能靠 onDestroy 兜底恢复）")
        } else {
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

    private fun startLease() {
        if (leaseRunning) return
        leaseRunning = true
        Log.i(LOG_TAG, "trampoline 开始续租（外部 UI 在前台，面板保持挂起）")
        leaseHandler.removeCallbacks(leaseTick)
        leaseHandler.postDelayed(leaseTick, LeaseIntervalMs)
    }

    private fun stopLease() {
        if (!leaseRunning) return
        leaseRunning = false
        Log.i(LOG_TAG, "trampoline 停止续租（回到前台，看门狗重新生效）")
        leaseHandler.removeCallbacks(leaseTick)
    }

    companion object {
        /**
         * 本链路统一日志 tag（§0.16.23）：`adb logcat -s StashImageEdit` 一次看全
         * "点 ✎ → 起中转 → 编辑器 → 结果 → 替换 → 恢复"。字符串必须与其它几处**逐字一致**。
         */
        const val LOG_TAG = "StashImageEdit"

        const val EXTRA_PATH = "stash_edit_image_path"
        private const val EXTRA_SOURCE_PATH = "stash_edit_image_source"
        private const val CacheDirName = "stash_composer_images"
        private const val LEASE_OWNER = "edit-image-block"
        private const val LeaseIntervalMs = 2_000L

        /** `onResume` 兜底交付的延迟（见 [onResume] 的 KDoc；只为给"顺序不同的 ROM"留窗口）。 */
        private const val ResumeFallbackDelayMs = 300L

        /**
         * 是否有一次"图片块 → 内置编辑器"的编辑**在途**（面板侧用它挡掉重复点击）。
         *
         * 进程级 volatile：本 Activity 与发起方（overlay 的 Compose）同进程。
         */
        @Volatile
        var editInFlight: Boolean = false
            private set

        /**
         * 由**发起方**（面板侧）在起本 Activity 之前同步置位 / 在结果回调里清位。
         *
         * 为什么发起方也要能写：`onCreate` 与"用户点下 ✎"之间有个几十毫秒的窗口，
         * 连点两下会叠起两个 trampoline（第二个挂起会被幂等忽略、第一个的结果回来才恢复，
         * 用户看到的是"点了没反应 + 莫名其妙换了两次图"）。发起方同步置位把窗口关掉。
         */
        fun markEditInFlight(inFlight: Boolean) {
            editInFlight = inFlight
        }

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
                Log.i(
                    LOG_TAG,
                    "面板侧回调 delivered cancelled=$cancelled path=${payload.getString(EXTRA_PATH)}",
                )
                onResult(if (cancelled) null else payload.getString(EXTRA_PATH))
            }
            val intent = Intent(appContext, StashEditImageTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(TrampolineResultPort.EXTRA_TOKEN, token)
                putExtra(EXTRA_SOURCE_PATH, sourcePath)
            }
            Log.i(LOG_TAG, "launch trampoline source=$sourcePath token=$token")
            runCatching { appContext.startActivity(intent) }.onFailure {
                Log.w(LOG_TAG, "launch trampoline 失败（startActivity 抛异常） source=$sourcePath", it)
                TrampolineResultPort.clearToken(token)
                onResult(null)
            }
        }
    }
}
