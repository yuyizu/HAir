package dev.hair.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.util.EnumMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 水景里点一下会响的声音。全部由代码合成，不带音频文件。 */
internal enum class SceneSound { DROP, FISH, SCHOOL, SHRIMP, RUSTLE, CRINKLE, CHIME, SQUISH, BIRD, CRICKET, WIND, ICE }

/** 一次点击该响什么、音调高低（[rate] 大于 1 变高）。 */
internal class Cue(val sound: SceneSound, val rate: Float = 1f)

internal const val SAMPLE_RATE = 22_050
internal const val PEAK = 0.35f
private const val EDGE_SECONDS = 0.006f
private const val TWO_PI = (2.0 * PI).toFloat()

/** 合成 [sound] 的单声道 16 位 PCM：峰值固定在 [PEAK]（留足余量，媒体音量开得高也不刺耳），头尾各淡 [EDGE_SECONDS]，不会有爆音。 */
internal fun renderSound(sound: SceneSound): ShortArray {
    val seconds = when (sound) {
        SceneSound.DROP -> 0.22f
        SceneSound.FISH -> 0.38f
        SceneSound.SCHOOL -> 0.26f
        SceneSound.SHRIMP -> 0.14f
        SceneSound.RUSTLE -> 0.36f
        SceneSound.CRINKLE -> 0.42f
        SceneSound.CHIME -> 0.55f
        SceneSound.SQUISH -> 0.2f
        SceneSound.BIRD -> 0.36f
        SceneSound.CRICKET -> 0.45f
        SceneSound.WIND -> 0.6f
        SceneSound.ICE -> 0.38f
    }
    val n = (seconds * SAMPLE_RATE).toInt()
    val rng = Random(sound.ordinal + 1)
    val raw = FloatArray(n)
    val noise = FloatArray(n) { rng.nextFloat() * 2f - 1f }
    when (sound) {
        SceneSound.DROP -> chirp(raw, 0f, 0.22f, { t -> 700f + 900f * (1f - exp(-t * 45f)) }, { t -> exp(-t * 22f) })
        SceneSound.FISH -> {
            // 水花：低通的一小团噪声；接着一声上扬的气泡
            val splash = lowpass(noise, 0.25f)
            for (i in 0 until (0.12f * SAMPLE_RATE).toInt()) raw[i] += splash[i] * exp(-i / SAMPLE_RATE.toFloat() * 28f) * 0.9f
            chirp(raw, 0.05f, 0.33f, { t -> 380f + 900f * (1f - exp(-t * 40f)) }, { t -> exp(-t * 18f) * 0.8f })
        }
        SceneSound.SCHOOL -> repeat(4) { k ->
            chirp(raw, k * 0.05f, 0.03f, { 1800f + 220f * k }, { t -> exp(-t * 120f) })
        }
        SceneSound.SHRIMP -> listOf(0f, 0.07f).forEach { start ->
            val from = (start * SAMPLE_RATE).toInt()
            for (i in 0 until (0.03f * SAMPLE_RATE).toInt()) {
                val t = i / SAMPLE_RATE.toFloat()
                // 高频的“嗒”：噪声的差分去掉低频，叠一点高音
                val click = noise[from + i] - noise[max(0, from + i - 1)]
                raw[from + i] += click * exp(-t * 400f) * 0.5f + sin(TWO_PI * 2400f * t) * exp(-t * 300f) * 0.4f
            }
        }
        SceneSound.RUSTLE -> {
            val band = highpass(lowpass(noise, 0.4f), 0.9f)
            for (i in 0 until n) {
                val p = i / n.toFloat()
                val swell = sin(PI.toFloat() * p)
                raw[i] = band[i] * swell * swell * (0.7f + 0.3f * sin(TWO_PI * 17f * p))
            }
        }
        SceneSound.CRINKLE -> {
            repeat(10) {
                val from = rng.nextInt(0, n - 400)
                val len = rng.nextInt(80, 300)
                val gain = 0.4f + 0.6f * rng.nextFloat()
                for (i in 0 until len) raw[from + i] += noise[from + i] * exp(-i / len.toFloat() * 6f) * gain
            }
        }
        SceneSound.CHIME -> {
            partial(raw, 0f, 880f, 9f, 1f)
            partial(raw, 0.09f, 1319f, 9f, 0.6f)
            partial(raw, 0.09f, 2640f, 14f, 0.15f)
        }
        SceneSound.SQUISH -> chirp(raw, 0f, 0.2f, { t -> 240f - 700f * t + 25f * sin(TWO_PI * 28f * t) }, { t -> exp(-t * 20f) })
        SceneSound.BIRD -> {
            chirp(raw, 0f, 0.08f, { t -> 3300f + 1500f * t / 0.08f + 80f * sin(TWO_PI * 60f * t) }, { t -> sin(PI.toFloat() * t / 0.08f) })
            chirp(raw, 0.12f, 0.09f, { t -> 3800f + 1800f * t / 0.09f }, { t -> sin(PI.toFloat() * t / 0.09f) })
            chirp(raw, 0.25f, 0.07f, { t -> 3000f + 1200f * t / 0.07f }, { t -> sin(PI.toFloat() * t / 0.07f) * 0.8f })
        }
        SceneSound.CRICKET -> listOf(0f, 0.2f).forEach { group ->
            repeat(4) { k ->
                chirp(raw, group + k * 0.045f, 0.03f, { 4300f }, { t -> sin(PI.toFloat() * t / 0.03f) })
            }
        }
        SceneSound.WIND -> {
            val gust = lowpass(noise, 0.07f)
            val whoosh = highpass(lowpass(noise, 0.2f), 0.95f)
            for (i in 0 until n) {
                val p = i / n.toFloat()
                val swell = sin(PI.toFloat() * p)
                raw[i] = (gust[i] * 2.2f + whoosh[i] * 0.5f) * swell * swell
            }
        }
        SceneSound.ICE -> {
            for (i in 0 until n) {
                val t = i / SAMPLE_RATE.toFloat()
                raw[i] = (sin(TWO_PI * 2400f * t) * 0.6f + sin(TWO_PI * 3730f * t) * 0.4f + sin(TWO_PI * 5100f * t) * 0.2f) * exp(-t * 16f)
            }
            for (i in 0 until 60) raw[i] += noise[i] * (1f - i / 60f) * 0.5f
        }
    }
    return toPcm(raw)
}

/** 从 [start] 秒起、持续 [length] 秒的一段扫频正弦：[frequency] 和 [envelope] 的参数是这一段内的秒数。 */
private fun chirp(out: FloatArray, start: Float, length: Float, frequency: (Float) -> Float, envelope: (Float) -> Float) {
    val from = (start * SAMPLE_RATE).toInt()
    val count = min((length * SAMPLE_RATE).toInt(), out.size - from)
    var phase = 0f
    for (i in 0 until count) {
        val t = i / SAMPLE_RATE.toFloat()
        phase += TWO_PI * frequency(t) / SAMPLE_RATE
        out[from + i] += sin(phase) * envelope(t)
    }
}

/** 从 [start] 秒起的一个衰减正弦分音。 */
private fun partial(out: FloatArray, start: Float, frequency: Float, decay: Float, gain: Float) {
    val from = (start * SAMPLE_RATE).toInt()
    for (i in 0 until out.size - from) {
        val t = i / SAMPLE_RATE.toFloat()
        out[from + i] += sin(TWO_PI * frequency * t) * exp(-t * decay) * gain
    }
}

private fun lowpass(x: FloatArray, alpha: Float): FloatArray {
    val y = FloatArray(x.size)
    var state = 0f
    for (i in x.indices) {
        state += alpha * (x[i] - state)
        y[i] = state
    }
    return y
}

private fun highpass(x: FloatArray, alpha: Float): FloatArray {
    val y = FloatArray(x.size)
    var low = 0f
    for (i in x.indices) {
        low += (1f - alpha) * (x[i] - low)
        y[i] = x[i] - low
    }
    return y
}

/** 先淡入淡出再定峰值：开头就最响的声音（水滴、冰裂）也和别的声音一样响。 */
private fun toPcm(raw: FloatArray): ShortArray {
    val edge = (EDGE_SECONDS * SAMPLE_RATE).toInt()
    val faded = FloatArray(raw.size) { i -> raw[i] * min(1f, min(i, raw.size - 1 - i) / edge.toFloat()) }
    val peak = faded.maxOf { abs(it) }
    val gain = if (peak > 0f) PEAK / peak else 0f
    return ShortArray(faded.size) { i -> (faded[i] * gain * Short.MAX_VALUE).toInt().toShort() }
}

/**
 * 播放水景音效。每种声音合成一次、各占一个静态的 AudioTrack，再点就从头重放。
 * 走媒体音量（和游戏同一通道）：手机处于静音、振动模式时也会响，媒体音量为 0 时不响；
 * 不抢音频焦点，不打断正在放的音乐。要不要出声由设置里的开关决定。
 */
internal class SceneSounds {
    private val tracks = EnumMap<SceneSound, AudioTrack>(SceneSound::class.java)
    private var lastPlayedAt = 0L

    /** [rate] 大于 1 音调变高变短，小于 1 变低变长；鱼越大音越低。 */
    fun play(sound: SceneSound, rate: Float = 1f) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayedAt < MIN_GAP_MS) return
        lastPlayedAt = now
        val track = tracks.getOrPut(sound) { build(renderSound(sound)) }
        track.stop()
        track.reloadStaticData()
        track.playbackRate = (SAMPLE_RATE * rate.coerceIn(0.6f, 1.8f)).toInt()
        track.play()
    }

    fun release() {
        tracks.values.forEach(AudioTrack::release)
        tracks.clear()
    }

    private fun build(pcm: ShortArray): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(pcm, 0, pcm.size) }

    private companion object {
        /** 连点时每秒最多响这么多次，免得声音糊成一团。 */
        const val MIN_GAP_MS = 120L
    }
}
