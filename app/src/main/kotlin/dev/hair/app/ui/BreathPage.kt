package dev.hair.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hair.app.data.MomentKind
import dev.hair.app.data.MomentLog
import kotlinx.coroutines.delay

private const val INHALE_MS = 4_000
private const val EXHALE_MS = 6_000
private const val PETALS = 6
private const val MIN_SCALE = 0.45f
private const val TICK_MS = 250L
private const val BREATH_TITLE = "呼吸练习"
private val SESSION_MINUTES = listOf(2, 5, 10)

/**
 * 呼吸花园：花瓣随 4 秒吸气、6 秒呼气张开和合拢，每次换气手机轻震一下，所以闭着眼也能跟。
 * 一次练习就是一段 [MomentKind.BREATH] 记录（起止时间落在 [log] 里），之后可以在心跳记忆里看心率变化。
 * 是否在练习只看 [log] 里有没有进行中的呼吸记录；离开本页时会结束它。
 */
@Composable
fun BreathPage(log: MomentLog, onChange: (MomentLog) -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val open = log.open
    val running = open?.kind == MomentKind.BREATH
    var sessionIndex by rememberSaveable { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val plannedMs = SESSION_MINUTES[sessionIndex] * 60_000L
    val leave = {
        if (running) onChange(log.finish(System.currentTimeMillis()))
        onBack()
    }

    PageScaffold("心跳记忆", leave, "呼吸花园") {
        Block {
            Garden(running)
            if (running) {
                Remaining(open.startMillis, plannedMs) {
                    finished = true
                    onChange(log.finish(System.currentTimeMillis()))
                }
            }
        }
        Block {
            when {
                running -> PillButton("结束", onClick = { finished = true; onChange(log.finish(System.currentTimeMillis())) }, modifier = Modifier.fillMaxWidth())
                open != null -> Text("有一段「${open.title}」正在记录，先去心跳记忆里结束它。", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                else -> {
                    if (finished) Text("练习完成。手环同步后，可以在心跳记忆里看这几分钟的心率。", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                    Segmented(SESSION_MINUTES.map { "$it 分钟" }, sessionIndex, { sessionIndex = it })
                    PillButton(
                        "开始",
                        onClick = {
                            finished = false
                            onChange(log.start(MomentKind.BREATH, BREATH_TITLE, System.currentTimeMillis()))
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            }
        }
        Footnote("吸气 4 秒、呼气 6 秒，每次换气手机轻震一下。练习期间屏幕保持常亮。心率要等手环同步之后才看得到。")
    }
}

/** 剩余时间；到点调用 [onElapsed]。靠定时器取当前时间，不累加，切后台回来也不会错。 */
@Composable
private fun Remaining(startMillis: Long, plannedMs: Long, onElapsed: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val currentOnElapsed by rememberUpdatedState(onElapsed)
    LaunchedEffect(startMillis) {
        while (true) {
            now = System.currentTimeMillis()
            if (now - startMillis >= plannedMs) {
                currentOnElapsed()
                return@LaunchedEffect
            }
            delay(TICK_MS)
        }
    }
    Text(
        "剩余 ${timerText((startMillis + plannedMs - now).coerceAtLeast(0))}",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun Garden(running: Boolean) {
    val view = LocalView.current
    val scale = remember { Animatable(MIN_SCALE) }
    var inhale by remember { mutableStateOf(true) }
    LaunchedEffect(running) {
        if (!running) {
            scale.snapTo(MIN_SCALE)
            return@LaunchedEffect
        }
        while (true) {
            inhale = true
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            scale.animateTo(1f, tween(INHALE_MS, easing = FastOutSlowInEasing))
            inhale = false
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            scale.animateTo(MIN_SCALE, tween(EXHALE_MS, easing = FastOutSlowInEasing))
        }
    }
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    val petal = MaterialTheme.colorScheme.secondary
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 * scale.value
            repeat(PETALS) { i ->
                rotate(i * 360f / PETALS, pivot = center) {
                    drawOval(petal.copy(alpha = 0.3f), Offset(center.x - radius * 0.3f, center.y - radius), Size(radius * 0.6f, radius))
                }
            }
            drawCircle(petal.copy(alpha = 0.55f), radius * 0.28f, center)
        }
        // 花收到最小时中心放不下整句话，等待时的提示放在花下面
        if (running) Text(if (inhale) "吸气" else "呼气", style = MaterialTheme.typography.titleMedium)
    }
    if (!running) {
        Text(
            "准备好了就开始",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
