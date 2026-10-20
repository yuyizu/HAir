package dev.hair.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hair.app.data.CardioLevel
import dev.hair.app.data.DetailRange
import dev.hair.app.data.Goals
import dev.hair.app.data.Metric

private val TABS = listOf("摘要", "睡眠", "我的")
private val INDICATOR_WIDTH = 28.dp

@Composable
fun HairScreen(
    state: UiState,
    goals: Goals,
    onGoalsChange: (Goals) -> Unit,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    loadSeries: suspend (Metric, DetailRange) -> SeriesResult,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            UiState.Loading -> Centered { CircularProgressIndicator() }
            UiState.SignedOut -> SignedOutScreen(onSignIn)
            is UiState.Error -> Centered {
                Text(state.message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRefresh) { Text("重试") }
                    OutlinedButton(onClick = onSignOut) { Text("退出登录") }
                }
            }
            is UiState.Ready -> ReadyScreen(state, goals, onGoalsChange, onRefresh, onSignOut, loadSeries)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun SignedOutScreen(onSignIn: () -> Unit) = Centered {
    Text("HAir", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(12.dp))
    // Google 要求在使用过程中明确说明访问哪些健康数据及用途，隐私政策不能替代
    Text(
        "HAir 会读取你 Fitbit Air 的睡眠、心率、心率变异性和步数数据，" +
            "仅用于在本机展示睡眠与恢复情况。数据不会上传到任何服务器。",
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onSignIn) { Text("使用 Google 账号连接") }
}

@Composable
private fun ReadyScreen(
    state: UiState.Ready,
    goals: Goals,
    onGoalsChange: (Goals) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    loadSeries: suspend (Metric, DetailRange) -> SeriesResult,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var introPlayed by rememberSaveable { mutableStateOf(false) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                TabBar(selected = tab, onSelect = { tab = it })
            },
        ) { padding ->
            Box(Modifier.padding(padding).statusBarsPadding()) {
                // 往右边的标签切换，新内容从右边滑入、旧内容往左退出；往左切反过来。进入比离开慢。
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(320, easing = LinearOutSlowInEasing)) { dir * it / 8 } +
                            fadeIn(tween(320, easing = LinearOutSlowInEasing))) togetherWith
                            (slideOutHorizontally(tween(200, easing = FastOutLinearInEasing)) { -dir * it / 8 } +
                                fadeOut(tween(200, easing = FastOutLinearInEasing)))
                    },
                    label = "tab",
                ) { current ->
                    when (current) {
                        0 -> TodayTab(
                            state,
                            goals,
                            onRefresh,
                            animateIn = !introPlayed,
                            onIntroDone = { introPlayed = true },
                            onOpenDetail = { detailOpen = true },
                        )
                        1 -> SleepTab(state.snapshot.nights)
                        else -> ProfileTab(state, goals, onGoalsChange, onRefresh, onSignOut)
                    }
                }
            }
        }
        // 详情页从右侧整页滑入，盖住标签栏；摘要页保持在下面，返回时滚动位置还在。进入比离开慢。
        AnimatedVisibility(
            visible = detailOpen,
            enter = slideInHorizontally(tween(320, easing = LinearOutSlowInEasing)) { it } +
                fadeIn(tween(320, easing = LinearOutSlowInEasing)),
            exit = slideOutHorizontally(tween(200, easing = FastOutLinearInEasing)) { it } +
                fadeOut(tween(200, easing = FastOutLinearInEasing)),
        ) {
            DetailScreen(onBack = { detailOpen = false }, loadSeries = loadSeries)
        }
    }
}

/** 纯文字标签栏：顶部细线；选中指示是一条会滑到新标签下的细线，首次显示直接就位。 */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.background(colors.surface).navigationBarsPadding()) {
        HorizontalDivider(color = colors.outlineVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tabWidth = maxWidth / TABS.size
            val indicatorX by animateDpAsState(
                targetValue = tabWidth * selected + (tabWidth - INDICATOR_WIDTH) / 2,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "tabIndicator",
            )
            Row(Modifier.fillMaxWidth()) {
                TABS.forEachIndexed { i, label ->
                    val isSelected = i == selected
                    val source = remember { MutableInteractionSource() }
                    Text(
                        label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .pressScale(source)
                            .selectable(
                                selected = isSelected,
                                interactionSource = source,
                                indication = LocalIndication.current,
                                role = Role.Tab,
                                onClick = { onSelect(i) },
                            )
                            .padding(vertical = 14.dp),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = indicatorX)
                    .width(INDICATOR_WIDTH)
                    .height(3.dp)
                    .background(colors.onSurface, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
            )
        }
    }
}

fun cardioLevelLabel(level: CardioLevel): String = when (level) {
    CardioLevel.POOR -> "较差"
    CardioLevel.FAIR -> "一般"
    CardioLevel.AVERAGE -> "平均"
    CardioLevel.GOOD -> "良好"
    CardioLevel.VERY_GOOD -> "很好"
    CardioLevel.EXCELLENT -> "优秀"
}
