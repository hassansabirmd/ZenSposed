package com.hassan.zensposed.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * A snapping wheel picker. Emits [onSelected] as soon as the centered row changes
 * (including mid-scroll), so a fast tap on Start still sees the scrolled value.
 */
@Composable
fun WheelPicker(
    items: List<Int>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 3,
    rowHeight: Int = 48,
    label: String = "",
    textColor: Color = Color.Unspecified,
    onScrollInProgressChange: ((Boolean) -> Unit)? = null
) {
    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeSelected)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centeredIndex by remember {
        derivedStateOf {
            if (items.isEmpty()) 0
            else {
                (listState.firstVisibleItemIndex +
                    if (listState.firstVisibleItemScrollOffset > rowHeight / 2) 1 else 0)
                    .coerceIn(0, items.lastIndex)
            }
        }
    }

    // Keep parent in sync with whatever is currently centered — including mid-scroll.
    LaunchedEffect(items) {
        snapshotFlow { centeredIndex }
            .distinctUntilChanged()
            .collect { idx -> onSelected(idx) }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling -> onScrollInProgressChange?.invoke(scrolling) }
    }

    // Sync from external selection changes (quick-timer chips) once scrolling stops.
    LaunchedEffect(selectedIndex, items) {
        if (items.isEmpty()) return@LaunchedEffect
        val target = selectedIndex.coerceIn(0, items.lastIndex)
        if (listState.isScrollInProgress) {
            snapshotFlow { listState.isScrollInProgress }.first { !it }
        }
        if (centeredIndex != target) {
            listState.scrollToItem(target)
        }
    }

    Box(
        modifier = modifier.height((rowHeight * visibleCount).dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = (rowHeight * (visibleCount / 2)).dp
            )
        ) {
            itemsIndexed(items) { index, value ->
                val isSelected = index == centeredIndex
                Box(
                    modifier = Modifier
                        .height(rowHeight.dp)
                        .width(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString().padStart(2, '0'),
                        textAlign = TextAlign.Center,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (isSelected) 30.sp else 22.sp,
                        color = if (textColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else textColor,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .alpha(if (isSelected) 1f else 0.35f)
                    )
                }
            }
        }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
            )
        }
    }
}
