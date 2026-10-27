package app.orchard.tv.ui.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.apps.AluHome
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.isSelect
import app.orchard.tv.ui.theme.Type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The personal shelf and grid share the same original icon, always-visible name and quiet focus. */
@Composable
internal fun AluAppTile(
    app: AppEntry, width: Dp, height: Dp, focusRequester: FocusRequester,
    enabled: Boolean, moving: Boolean, onFocused: () -> Unit,
    onClick: (Rect?) -> Unit, onLongPress: (Rect?) -> Unit,
    modifier: Modifier = Modifier, rowEnd: Boolean = false,
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var hold by remember { mutableStateOf<Job?>(null) }
    var longFired by remember { mutableStateOf(false) }
    var menuDown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val longPress by rememberUpdatedState(onLongPress)
    DisposableEffect(Unit) { onDispose { hold?.cancel() } }
    Pressable(
        onClick = { if (!longFired) onClick(bounds); longFired = false },
        enabled = enabled,
        onLongClick = { longPress(bounds) },
        focusRequester = focusRequester,
        focusedScale = 1.025f,
        modifier = modifier.size(width, height).onGloballyPositioned { bounds = it.boundsInWindow() }
            .focusProperties { if (rowEnd) right = FocusRequester.Cancel },
        onKey = { event ->
            if (event.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_MENU) {
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) menuDown = true
                if (event.type == KeyEventType.KeyUp && menuDown) { menuDown = false; longPress(bounds) }
                true
            } else {
                if (event.isSelect()) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        longFired = false
                        hold?.cancel()
                        hold = scope.launch { delay(550); longFired = true; longPress(bounds) }
                    } else if (event.type == KeyEventType.KeyUp) hold?.cancel()
                }
                false
            }
        },
    ) { state ->
        LaunchedEffect(state.focused, enabled) {
            if (state.focused) onFocused()
            if (!state.focused || !enabled) { hold?.cancel(); menuDown = false; longFired = false }
        }
        val shape = RoundedCornerShape(12.dp)
        val focused = state.focused || moving
        Column(
            Modifier.fillMaxSize()
                .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.085f), shape)
                    .border(1.dp, Color.White.copy(alpha = 0.92f), shape) else Modifier)
                .padding(top = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (app.folder != null || app.key == AluHome.ALL_APPS) {
                Canvas(Modifier.size(54.dp).background(Color(0xFF0A1828).copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))) {
                    val n = if (app.folder != null) 2 else 3
                    val cell = size.width * if (n == 2) 0.16f else 0.095f
                    val pitch = cell * 1.65f
                    val start = (size.width - (n - 1) * pitch - cell) / 2
                    repeat(n) { y -> repeat(n) { x ->
                        drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(start + x * pitch, start + y * pitch),
                            Size(cell, cell), CornerRadius(cell * 0.2f))
                    } }
                }
            } else {
                Image(app.art.back, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
            }
            BasicText(app.label, style = Type.Body.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.94f), textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 13.dp, start = 3.dp, end = 3.dp))
        }
    }
}
