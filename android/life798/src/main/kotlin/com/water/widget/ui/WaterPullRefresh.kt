package com.water.widget.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun PullRefreshOffsetContent(
    pullOffset: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().offset(y = pullOffset),
        content = content
    )
}

/**
 * STmall 使用 Material3 自带的下拉刷新，避免为了一个手势再引入整套 Miuix UI。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorTopOffset: Dp = 0.dp,
    content: @Composable BoxScope.(Dp) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        content(0.dp)
    }
}
