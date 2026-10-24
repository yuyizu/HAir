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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hair.app.data.CardioLevel
import dev.hair.app.data.Companion
import dev.hair.app.data.DetailRange
import dev.hair.app.data.Goals
import dev.hair.app.data.HabitLog
import dev.hair.app.data.HeartBucket
import dev.hair.app.data.Metric
import dev.hair.app.data.Moment
import dev.hair.app.data.MomentLog
import dev.hair.app.data.Night
import dev.hair.app.data.NightDetail
import dev.hair.app.data.Recovery
import dev.hair.app.data.Series
import dev.hair.app.data.SkinTempSignal
import dev.hair.app.data.Snapshot
import dev.hair.app.data.UserSettings
import dev.hair.app.data.companion
import dev.hair.app.data.recovery
import dev.hair.app.data.skinTempSignal
import dev.hair.app.ui.theme.TideDisplay
import dev.hair.app.ui.theme.tide
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.sin

private val TABS = listOf("今日", "睡眠", "探索", "我的")
private const val TAB_SLEEP = 1
private const val TAB_EXPLORE = 2
private val WAVE_HEIGHT = 28.dp
private val INDICATOR_SIZE = 5.dp

/** 用户在本机写下的内容：设置、习惯记录、心跳记忆。它们不是健康数据，不随退出登录清除。 */
data class UserData(val settings: UserSettings, val habits: HabitLog, val moments: MomentLog)

/** 界面能触发的全部动作：登录态、本机数据的写入，以及详情页现取数据的函数。 */
class HairActions(
    val signIn: () -> Unit,
    val refresh: () -> Unit,
    val signOut: () -> Unit,
    val setSettings: (UserSettings) -> Unit,
    val setHabits: (HabitLog) -> Unit,
    val setMoments: (MomentLog) -> Unit,
    val loadSeries: suspend (Metric, DetailRange) -> LoadResult<Series>,
    val loadNightDetail: suspend (Night) -> LoadResult<NightDetail?>,
    val loadHeart: suspend (Moment) -> LoadResult<List<HeartBucket>>,
)

@Composable
fun HairScreen(state: UiState, data: UserData, actions: HairActions) {
    // Surface 给整棵树提供内容色；盖在 Scaffold 外面的整页、登录页、错误页里没写颜色的文字也靠它
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TideLoader() }
            UiState.SignedOut -> SignedOutScreen(actions.signIn)
            is UiState.Error -> ErrorScreen(state.message, actions.refresh, actions.signOut)
            is UiState.Ready -> ReadyScreen(state, data, actions)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(PAGE_GUTTER),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("重试", onRetry)
            PillButton("退出登录", onSignOut, filled = false)
        }
    }
}

/** 上面是天，下面是水：名字写在天上，水里是用途说明和唯一的按钮。 */
@Composable
private fun SignedOutScreen(onSignIn: () -> Unit) {
    val sea = MaterialTheme.tide
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(sea.skyTop, sea.skyBottom)))
                .statusBarsPadding()
                .padding(PAGE_GUTTER),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text("HAir", style = TideDisplay.copy(fontSize = 88.sp, lineHeight = 88.sp), color = sea.skyInk)
            Text(
                "Fitbit Air 的睡眠与恢复，只在这台手机上看。",
                style = MaterialTheme.typography.bodyLarge,
                color = sea.skyInk.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
        Canvas(Modifier.fillMaxWidth().height(WAVE_HEIGHT)) {
            drawRect(sea.skyBottom)
            val path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width) {
                    lineTo(x, size.height * 0.5f + sin(x / size.width * 2 * PI * 1.5).toFloat() * size.height * 0.3f)
                    x += 4.dp.toPx()
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, sea.light)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(sea.light)
                .navigationBarsPadding()
                .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 32.dp),
        ) {
            // Google 要求在使用过程中明确说明访问哪些健康数据及用途，隐私政策不能替代
            Text(
                "HAir 会读取你 Fitbit Air 的睡眠、心率、心率变异性和步数数据，" +
                    "仅用于在本机展示睡眠与恢复情况。数据不会上传到任何服务器。",
                style = MaterialTheme.typography.bodyMedium,
                color = sea.onWater,
            )
            Box(
                Modifier
                    .padding(top = 24.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(sea.onWater)
                    .clickable(role = Role.Button, onClick = onSignIn)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("使用 Google 账号连接", style = MaterialTheme.typography.labelLarge, color = sea.deep)
            }
        }
    }
}

@Composable
private fun ReadyScreen(state: UiState.Ready, data: UserData, actions: HairActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var introPlayed by rememberSaveable { mutableStateOf(false) }
    // 睡眠、探索标签页首次出场的动画只播一次：第 i 位置 1 表示第 i 个标签页已经出场过
    var seenTabs by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tab) {
        delay(INTRO_TOTAL_MS)
        seenTabs = seenTabs or (1 shl tab)
    }
    var routeKey by rememberSaveable { mutableStateOf<String?>(null) }
    val route = routeKey?.let(Route::parse)
    // 退出动画播放时 route 已经是 null，内容要继续显示最后一个页面直到滑出
    var lastRoute by remember { mutableStateOf<Route?>(null) }
    SideEffect { if (route != null) lastRoute = route }
    val shown = route ?: lastRoute

    val snapshot = state.snapshot
    val derived = remember(snapshot, data.settings.goals) { derive(snapshot, data.settings.goals) }
    val open = { target: Route -> routeKey = target.route }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // 状态栏由各页自己处理：每页的天空都要铺到屏幕顶
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                TabBar(selected = tab, onSelect = { tab = it })
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
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
                            data.settings,
                            actions.refresh,
                            animateIn = !introPlayed,
                            onIntroDone = { introPlayed = true },
                            recovery = derived.recovery,
                            onOpenDetail = { open(Route.Activity) },
                            onOpenForecast = { open(Route.Forecast) },
                            onOpenNight = { open(Route.NightView(it)) },
                        )
                        TAB_SLEEP -> SleepTab(
                            snapshot.nights,
                            snapshot.vitals,
                            data.settings.goals.sleepMin,
                            animateIn = seenTabs and (1 shl TAB_SLEEP) == 0,
                        ) { open(Route.NightView(it)) }
                        TAB_EXPLORE -> ExploreTab(derived.companion, data.habits, data.moments, animateIn = seenTabs and (1 shl TAB_EXPLORE) == 0, open)
                        else -> ProfileTab(state, data.settings, actions.setSettings, actions.refresh, actions.signOut)
                    }
                }
            }
        }
        // 整页从右侧滑入，盖住标签栏；标签页保持在下面，返回时滚动位置还在。进入比离开慢。
        AnimatedVisibility(
            visible = route != null,
            enter = slideInHorizontally(tween(320, easing = LinearOutSlowInEasing)) { it } +
                fadeIn(tween(320, easing = LinearOutSlowInEasing)),
            exit = slideOutHorizontally(tween(200, easing = FastOutLinearInEasing)) { it } +
                fadeOut(tween(200, easing = FastOutLinearInEasing)),
        ) {
            shown?.let { page ->
                RoutePage(page, snapshot, data, actions, derived, open) { routeKey = page.parent?.route }
            }
        }
    }
}

/** 由快照和目标即时算出、多个页面共用的结果，只在数据变化时重算。 */
private class Derived(val recovery: Recovery?, val skin: SkinTempSignal?, val companion: Companion)

private fun derive(snapshot: Snapshot, goals: Goals): Derived {
    val today = LocalDate.now()
    return Derived(
        recovery(snapshot.nights, snapshot.vitals, today),
        skinTempSignal(snapshot.vitals, today),
        companion(snapshot.nights, snapshot.vitals, goals, today),
    )
}

@Composable
private fun RoutePage(
    route: Route,
    snapshot: Snapshot,
    data: UserData,
    actions: HairActions,
    derived: Derived,
    open: (Route) -> Unit,
    onBack: () -> Unit,
) {
    when (route) {
        Route.Activity -> DetailScreen(onBack, actions.loadSeries)
        Route.Forecast -> ForecastPage(derived.recovery, derived.skin, snapshot.nights.size, snapshot.vitals.size, onBack)
        is Route.NightView -> NightPage(
            snapshot.nights.firstOrNull { it.date == route.date },
            route.date,
            snapshot.vitals.firstOrNull { it.date == route.date },
            actions.loadNightDetail,
            onBack,
        )
        Route.Lab -> LabPage(data.habits, snapshot.nights, snapshot.vitals, actions.setHabits, onBack)
        Route.Pet -> PetPage(derived.companion, derived.recovery?.weather, data.settings.goals, onBack)
        Route.Sky -> SkyPage(snapshot.nights, data.settings.goals, onBack)
        Route.Art -> ArtPage(snapshot.nights, snapshot.vitals, data.settings.goals, onBack)
        Route.Moments -> MomentsPage(
            data.moments,
            actions.setMoments,
            onBack,
            onOpenBreath = { open(Route.Breath) },
            onOpenMoment = { open(Route.MomentView(it.startMillis)) },
        )
        Route.Breath -> BreathPage(data.moments, actions.setMoments, onBack)
        is Route.MomentView -> MomentPage(
            data.moments.moments.firstOrNull { it.startMillis == route.startMillis },
            actions.loadHeart,
            onDelete = {
                actions.setMoments(data.moments.delete(route.startMillis))
                onBack()
            },
            onBack = onBack,
        )
    }
}

/** 抽屉里的一行安静导航：纯文字，选中的加粗，下面一个小圆点会滑到新标签下，首次显示直接就位。 */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.background(colors.background).navigationBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tabWidth = maxWidth / TABS.size
            val dotX by animateDpAsState(
                targetValue = tabWidth * selected + (tabWidth - INDICATOR_SIZE) / 2,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "tabDot",
            )
            Row(Modifier.fillMaxWidth()) {
                TABS.forEachIndexed { i, label ->
                    val isSelected = i == selected
                    val source = remember { MutableInteractionSource() }
                    Text(
                        label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
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
                            .padding(top = 14.dp, bottom = 24.dp),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = dotX, y = (-12).dp)
                    .size(INDICATOR_SIZE)
                    .background(colors.secondary, CircleShape),
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
