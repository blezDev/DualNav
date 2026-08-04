package com.blez.dualnav.feature.overlay.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blez.dualnav.R
import com.blez.dualnav.ui.theme.DualNavTheme

/**
 * A small persistent bubble, not a control panel — it exists only to show DualNav is still
 * running while another app (Google Maps) is in the foreground. Tap opens the app; drag moves it.
 */
@Composable
fun FloatingWidget(
    onOpenAppClick: () -> Unit,
    onDrag: (Offset) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .size(56.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.cd_open_app),
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onOpenAppClick)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FloatingWidgetPreview() {
    DualNavTheme {
        FloatingWidget(onOpenAppClick = {}, onDrag = {})
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FloatingWidgetDarkPreview() {
    DualNavTheme {
        FloatingWidget(onOpenAppClick = {}, onDrag = {})
    }
}
