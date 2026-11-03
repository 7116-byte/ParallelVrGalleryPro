package com.local.parallelvrgallerypro

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/** Tab taps are immediate; a completed swipe updates the same controlled selection. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LinkedPager(
    selected: String,
    keys: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    content: @Composable (String) -> Unit,
) {
    val pager = rememberPagerState(initialPage = keys.indexOf(selected).coerceAtLeast(0)) { keys.size }
    val latestSelection by rememberUpdatedState(selected)
    val latestOnSelected by rememberUpdatedState(onSelected)
    LaunchedEffect(selected) {
        val target = keys.indexOf(selected)
        if (target >= 0 && pager.settledPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().drop(1).collect { page ->
            keys.getOrNull(page)?.takeIf { it != latestSelection }?.let(latestOnSelected)
        }
    }
    HorizontalPager(state = pager, modifier = modifier, userScrollEnabled = swipeEnabled, key = { keys[it] }) { page -> content(keys[page]) }
}
