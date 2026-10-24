package dev.hair.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.hair.app.ui.theme.TideTitle
import dev.hair.app.ui.theme.tide

/** 滚动多少 dp 后状态栏下的渐隐罩完全盖住内容。 */
private const val SCRIM_FULL_DP = 48
private val SCRIM_FADE = 20.dp

/**
 * 页眉：状态栏下一片天光，往下渐变成页面底色，标题写在天上（和今日页的天空同一组颜色）。
 * 传入 [onBack] 时顶部多一个“‹ [backLabel]”。
 */
@Composable
fun SkyHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, backLabel: String? = null, onBack: (() -> Unit)? = null) {
    val sky = MaterialTheme.tide
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(sky.skyTop, MaterialTheme.colorScheme.background)))
            .statusBarsPadding()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 24.dp),
    ) {
        if (onBack != null) {
            // 文字按钮自带 12dp 内边距，往左挪回去，“‹”才和标题对齐
            TextAction("‹ $backLabel", Modifier.offset(x = (-12).dp), color = sky.skyInk, onClick = onBack)
        } else {
            Spacer(Modifier.height(20.dp))
        }
        Text(title, style = TideTitle, color = sky.skyInk)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = sky.skyInk.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * 内容滚到状态栏下面时，用页面底色盖住：状态栏整个高度内不透明（免得文字和状态栏图标叠在一起），
 * 再往下渐隐 [SCRIM_FADE]；在顶部时完全透明。
 */
@Composable
fun BoxScope.StatusScrim(scrollPx: () -> Float) {
    val background = MaterialTheme.colorScheme.background
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(statusBar + SCRIM_FADE)
            .drawBehind {
                val a = (scrollPx() / SCRIM_FULL_DP.dp.toPx()).coerceIn(0f, 1f)
                if (a > 0f) {
                    val solid = statusBar.toPx() / size.height
                    drawRect(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = a),
                            solid to background.copy(alpha = a),
                            1f to background.copy(alpha = 0f),
                        ),
                    )
                }
            },
    )
}

/** 标签页：天空页眉加一列行，整页铺到屏幕顶，页眉随列表一起滚走。 */
@Composable
fun TabPage(title: String, subtitle: String? = null, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SkyHeader(title, subtitle = subtitle) }
            content()
        }
        StatusScrim { if (state.firstVisibleItemIndex > 0) Float.MAX_VALUE else state.firstVisibleItemScrollOffset.toFloat() }
    }
}

/**
 * 盖在标签页上的整页：天空页眉（带“‹ 上一页”）加可滚动的内容列。
 * 系统返回键和左上按钮都走 [onBack]。
 */
@Composable
fun PageScaffold(backLabel: String, onBack: () -> Unit, title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    val scroll = rememberScrollState()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 盖在下面的页面上，不让点击穿透
            .pointerInput(Unit) {},
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).navigationBarsPadding()) {
            SkyHeader(title, subtitle = subtitle, backLabel = backLabel, onBack = onBack)
            content()
            Spacer(Modifier.height(24.dp))
        }
        StatusScrim { scroll.value.toFloat() }
    }
}
