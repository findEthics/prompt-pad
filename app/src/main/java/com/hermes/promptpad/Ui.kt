package com.hermes.promptpad

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices || from == to) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
fun ReorderButtons(up: (() -> Unit)?, down: (() -> Unit)?) {
    IconButton(onClick = { up?.invoke() }, enabled = up != null) {
        Icon(Icons.Outlined.KeyboardArrowUp, "Move up", tint = if (up != null) Accent else DotIdle)
    }
    IconButton(onClick = { down?.invoke() }, enabled = down != null) {
        Icon(Icons.Outlined.KeyboardArrowDown, "Move down", tint = if (down != null) Accent else DotIdle)
    }
}

fun Modifier.orangeScrollbar(state: ScrollState) = drawWithContent {
    drawContent()
    if (state.maxValue > 0) drawScrollbar(state.value.toFloat() / state.maxValue,
        size.height / (size.height + state.maxValue))
}

fun Modifier.orangeScrollbar(state: LazyListState) = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull()
    if (first != null && (state.canScrollForward || state.canScrollBackward)) {
        // ponytail: index-based thumb for variable-height rows, no measurement cache.
        val visible = info.visibleItemsInfo.sumOf {
            (minOf(it.offset + it.size, info.viewportEndOffset) - maxOf(it.offset, info.viewportStartOffset)).coerceAtLeast(0).toDouble() / it.size.coerceAtLeast(1)
        }.toFloat()
        val offset = first.index + (-first.offset).coerceAtLeast(0).toFloat() / first.size.coerceAtLeast(1)
        drawScrollbar(offset / (info.totalItemsCount - visible).coerceAtLeast(1f), visible / info.totalItemsCount)
    }
}

private fun DrawScope.drawScrollbar(progress: Float, fraction: Float) {
    val height = (size.height * fraction).coerceIn(minOf(24.dp.toPx(), size.height), size.height)
    val top = (size.height - height) * progress.coerceIn(0f, 1f)
    drawLine(Accent, Offset(size.width - 2.dp.toPx(), top),
        Offset(size.width - 2.dp.toPx(), top + height), 3.dp.toPx(), StrokeCap.Round)
}

/** Titan 2 Elite is 4.04in / 4:3 — compact paddings everywhere. */
object Dim2 {
    val screen = 10.dp
    val gap = 8.dp
    val touch = 48.dp
    val cutoutLeft = 32.dp
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Black)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content
    )
}

@Composable
fun Row48(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim2.touch).clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun Header(text: String, back: (() -> Unit)? = null, center: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim2.touch).then(if (center) Modifier.padding(top = 4.dp) else Modifier),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
        verticalAlignment = if (center) Alignment.Top else Alignment.CenterVertically,
    ) {
        if (back != null) Text("‹", Modifier.clickable { back() }.padding(end = 12.dp), style = MaterialTheme.typography.headlineSmall)
        Text(text, style = if (center) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun EdgeScreen(title: String, modifier: Modifier = Modifier, heading: (@Composable () -> Unit)? = null, topRight: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Black)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Dim2.screen)) {
            Spacer(Modifier.height(Dim2.touch))
            content()
        }
        Box(Modifier.fillMaxWidth().statusBarsPadding().align(Alignment.TopCenter)) {
            if (heading != null) heading() else Text(
                title, Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            // ponytail: right-inset by cutoutLeft mirrors the date's curved-edge padding.
            if (topRight != null) Box(Modifier.align(Alignment.CenterEnd).padding(end = Dim2.cutoutLeft)) { topRight() }
        }
    }
}

@Composable
fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, l ->
            Text(
                l,
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (i == selected) Accent else Black)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (i == selected) Black else Dim,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
