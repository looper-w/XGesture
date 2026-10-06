package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.quickwheel.QuickWheelIconResolver

private const val ICON_LIBRARY_COLUMNS = 5

/**
 * 内置图标库选择对话框。
 *
 * 用对话框而非独立页面，是为了让容器编辑页的**草稿不丢失**（跳页会卸载编辑页）。
 */
@Composable
fun QuickWheelIconLibraryDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_wheel_icon_library)) },
        text = {
            val iconSize = 60.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                QuickWheelIconResolver.library.chunked(ICON_LIBRARY_COLUMNS).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.forEach { entry ->
                            Box(
                                modifier = Modifier
                                    .size(iconSize)
                                    .clickable { onPick(entry.key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = entry.vector,
                                    contentDescription = entry.key,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        repeat(ICON_LIBRARY_COLUMNS - row.size) {
                            Spacer(modifier = Modifier.width(iconSize))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
