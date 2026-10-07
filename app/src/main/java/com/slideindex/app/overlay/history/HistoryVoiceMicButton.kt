package com.slideindex.app.overlay.history

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.voice.StashVoiceController
import com.slideindex.app.voice.StashVoiceSession
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 语音输入按钮 —— 三个输入面（输入槽 / 面板输入条 / 编辑条）**共用同一个**。
 *
 * 状态来自 [StashVoiceSession]：
 * - 点一下 → [StashVoiceController.toggle]（没权限会先拉权限跳板，授权后自动开始听）；
 * - 正在听 → 图标转成实心 + 呼吸缩放，给"它在听"的反馈；
 * - 出结果 → **只有点它的那一面**把文字取走（`ownsSession`），避免多处重复插入；
 * - 出错 → 交给调用方提示（面板用 snackbar、输入槽用 toast）。
 */
@Composable
internal fun HistoryVoiceMicButton(
    onResult: (String) -> Unit,
    onError: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 20.dp,
) {
    val context = LocalContext.current
    val listening = StashVoiceSession.state == StashVoiceSession.State.Listening
    val finalText = StashVoiceSession.finalText
    val errorResId = StashVoiceSession.errorResId

    // 哪一面发起的会话，就由哪一面取结果：记下"点击时的 sessionId"，
    // 只有**之后真的开了新会话**（sessionId 变大）才认领 —— 否则用户拒绝权限、
    // 或点了停止，这一面会一直等着，把别人那轮的识别结果抢走。
    var claimedSessionId by remember { mutableIntStateOf(-1) }
    LaunchedEffect(claimedSessionId, finalText, errorResId) {
        if (claimedSessionId < 0) return@LaunchedEffect
        if (StashVoiceSession.sessionId <= claimedSessionId) return@LaunchedEffect
        if (finalText.isNotBlank()) {
            claimedSessionId = -1
            onResult(finalText)
            StashVoiceSession.consumeResult()
        } else if (errorResId != 0) {
            claimedSessionId = -1
            onError(errorResId)
            StashVoiceSession.consumeResult()
        }
    }

    val scheme = MiuixTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "voiceMic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voiceMicPulse",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (listening) {
                    Modifier.background(scheme.primary.copy(alpha = 0.14f))
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (listening) {
                    claimedSessionId = -1
                } else {
                    claimedSessionId = StashVoiceSession.sessionId
                }
                StashVoiceController.toggle(context)
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (listening) Icons.Default.Mic else Icons.Outlined.MicNone,
            contentDescription = stringResource(R.string.stash_voice_mic),
            tint = if (listening) scheme.primary else scheme.onSurface,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    val scale = if (listening) pulse else 1f
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
