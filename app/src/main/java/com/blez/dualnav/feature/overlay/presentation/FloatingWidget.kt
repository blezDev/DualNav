package com.blez.dualnav.feature.overlay.presentation

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blez.dualnav.R
import com.blez.dualnav.ui.theme.DualNavTheme

@Composable
fun FloatingWidget(
    onStopClick: () -> Unit,
    onResumeClick: () -> Unit,
    onOpenAppClick: () -> Unit,
    onCloseClick: () -> Unit,
    onDrag: (Offset) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDrag(dragAmount)
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onStopClick) {
                Icon(imageVector = Icons.Filled.Stop, contentDescription = stringResource(R.string.control_home_stop))
            }
            IconButton(onClick = onResumeClick) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.control_home_resume))
            }
            IconButton(onClick = onOpenAppClick) {
                Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.cd_open_app))
            }
            IconButton(onClick = onCloseClick) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_overlay))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FloatingWidgetPreview() {
    DualNavTheme {
        FloatingWidget(
            onStopClick = {},
            onResumeClick = {},
            onOpenAppClick = {},
            onCloseClick = {},
            onDrag = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FloatingWidgetDarkPreview() {
    DualNavTheme {
        FloatingWidget(
            onStopClick = {},
            onResumeClick = {},
            onOpenAppClick = {},
            onCloseClick = {},
            onDrag = {}
        )
    }
}
