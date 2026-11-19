package dev.hair.app.ui

import android.app.Application
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.hair.app.data.CardioFitness
import dev.hair.app.data.CardioLevel
import dev.hair.app.data.DailyVitals
import dev.hair.app.data.Goals
import dev.hair.app.data.Night
import dev.hair.app.data.Overview
import dev.hair.app.data.SceneSettings
import dev.hair.app.data.Season
import dev.hair.app.data.Sex
import dev.hair.app.data.SleepStage
import dev.hair.app.data.Snapshot
import dev.hair.app.data.StageSegment
import dev.hair.app.data.UserSettings
import dev.hair.app.ui.theme.HairTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 截屏基线：今日页浅色、今日页深色、睡眠页浅色。
 * 基线图存在 `app/src/test/screenshots/`，用 `./gradlew recordRoborazziDebug` 更新，
 * CI 和本地用 `./gradlew verifyRoborazziDebug` 校验。
 *
 * 确定性：固定日期、固定时区、固定 fixture、动画关闭（`animateIn = false` 且主时钟不走），
 * 不读系统日期、不发网络、不出声。基线换了环境（JDK、Robolectric、渲染模式）必须重录。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi",
    // 真实 Application 会初始化 AndroidKeyStore 里的登录凭据，Robolectric 不支持；截屏只用 fixture
    application = Application::class,
)
class ScreenShotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var defaultZone: TimeZone

    @Before
    fun freezeTimeAndAnimations() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE))
        // rememberSceneClock 在动画时长缩放为 0 时静止，水景停在初始帧
        Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(defaultZone)
    }

    @Test
    fun todayLight() {
        compose.setContent {
            HairTheme(darkTheme = false) {
                TodayTab(
                    state = UiState.Ready(SNAPSHOT),
                    settings = SETTINGS,
                    onRefresh = {},
                    animateIn = false,
                    onIntroDone = {},
                    recovery = null,
                    onOpenDetail = {},
                    onOpenForecast = {},
                    onOpenNight = {},
                    today = TODAY,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().captureRoboImage("today-light.png", OPTIONS)
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-night-xxhdpi")
    fun todayDark() {
        compose.setContent {
            HairTheme(darkTheme = true) {
                TodayTab(
                    state = UiState.Ready(SNAPSHOT),
                    settings = SETTINGS,
                    onRefresh = {},
                    animateIn = false,
                    onIntroDone = {},
                    recovery = null,
                    onOpenDetail = {},
                    onOpenForecast = {},
                    onOpenNight = {},
                    today = TODAY,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().captureRoboImage("today-dark.png", OPTIONS)
    }

    @Test
    fun sleepLight() {
        compose.setContent {
            HairTheme(darkTheme = false) {
                SleepTab(
                    nights = NIGHTS,
                    vitals = VITALS,
                    goalMin = SETTINGS.goals.sleepMin,
                    animateIn = false,
                    today = TODAY,
                    onOpenNight = {},
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().captureRoboImage("sleep-light.png", OPTIONS)
    }
}

private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val TODAY: LocalDate = LocalDate.of(2026, 3, 15)
private val OPTIONS = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f))

private val SETTINGS = UserSettings(
    goals = Goals(steps = 8_000, sleepMin = 480),
    sex = Sex.FEMALE,
    birthYear = 1990,
    scene = SceneSettings(
        // 固定季节与水域，不看系统日期和位置
        season = Season.SPRING,
        sound = false,
    ),
)

/** 近 7 晚；最后一晚带分期，用来画睡眠页的睡眠分期图和今日页的水体分层。 */
private val NIGHTS: List<Night> = listOf(
    night(TODAY.minusDays(6), asleepMin = 430),
    night(TODAY.minusDays(5), asleepMin = 452),
    night(TODAY.minusDays(4), asleepMin = 401),
    night(TODAY.minusDays(3), asleepMin = 468),
    night(TODAY.minusDays(2), asleepMin = 445),
    night(TODAY.minusDays(1), asleepMin = 510),
    night(TODAY, asleepMin = 486, withStages = true),
)

private val VITALS: List<DailyVitals> = NIGHTS.map {
    DailyVitals(date = it.date, restingHr = 56, hrvMs = 48.0, steps = 6_240, spo2Avg = 97.0, respRate = 14.2)
}

private val SNAPSHOT = Snapshot(
    overview = Overview(
        lastNight = NIGHTS.last(),
        restingHr = 56,
        hrvMs = 48.0,
        stepsToday = 6_240,
        latestHr = 72,
        cardioFitness = CardioFitness(date = TODAY, vo2Max = 42.5, level = CardioLevel.GOOD),
    ),
    nights = NIGHTS,
    // 2026-03-15 08:30（Asia/Shanghai），对应抽屉第一行的更新时间
    fetchedAtMillis = LocalDateTime.of(2026, 3, 15, 8, 30).atZone(ZONE).toInstant().toEpochMilli(),
    vitals = VITALS,
)

private fun night(date: LocalDate, asleepMin: Long, withStages: Boolean = false): Night {
    val awakeMin = 24L
    val inBedMin = asleepMin + awakeMin
    val stages = if (!withStages) {
        emptyList()
    } else {
        // 22:48 入睡到 07:18 醒来，合计 510 分钟；分期固定，不随环境变化
        var start = date.minusDays(1).atTime(22, 48).atZone(ZONE).toEpochSecond()
        listOf(
            SleepStage.AWAKE to 14L,
            SleepStage.LIGHT to 96L,
            SleepStage.DEEP to 80L,
            SleepStage.REM to 30L,
            SleepStage.LIGHT to 86L,
            SleepStage.DEEP to 70L,
            SleepStage.REM to 46L,
            SleepStage.LIGHT to 78L,
            SleepStage.AWAKE to 10L,
        ).map { (stage, minutes) ->
            StageSegment(stage, start, start + minutes * 60).also { start += minutes * 60 }
        }
    }
    val deepMin = if (withStages) 150 else asleepMin * 35 / 100
    val remMin = if (withStages) 76 else asleepMin * 15 / 100
    return Night(
        date = date,
        asleepMin = asleepMin,
        inBedMin = inBedMin,
        deepMin = deepMin,
        remMin = remMin,
        lightMin = asleepMin - deepMin - remMin,
        awakeMin = awakeMin,
        stages = stages,
    )
}