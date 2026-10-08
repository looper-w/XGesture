package com.slideindex.app.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 悬浮球「启动器对齐手指按下位置」开关。
 *
 * 默认关 = 历史行为（对齐手势判定成立时的手指位置，滑动即松手处），
 * 只有用户显式打开才改用按下位置，因此升级不会改变任何人的观感。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FloatBallOverlayAnchorAtTouchDownTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = testSettingsRepository(context)
    }

    @Test
    fun freshInstall_keepsLegacyReleasePointAnchor() {
        val settings = SettingsSnapshotReader.read(emptyPreferences(), context)

        assertFalse(settings.floatBallOverlayAnchorAtTouchDown)
    }

    @Test
    fun storedOn_isReadBack() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.FLOAT_BALL_OVERLAY_ANCHOR_AT_TOUCH_DOWN to true,
        )

        assertTrue(SettingsSnapshotReader.read(prefs, context).floatBallOverlayAnchorAtTouchDown)
    }

    @Test
    fun repositoryWrite_roundTrips() = runBlocking {
        val write = repository.setFloatBallOverlayAnchorAtTouchDown(true)
        assertTrue("写入锚点开关失败: $write", write.isSuccess)

        val settings = withTimeout(5_000) {
            repository.settings.first { it.floatBallOverlayAnchorAtTouchDown }
        }

        assertTrue(settings.floatBallOverlayAnchorAtTouchDown)
    }
}
