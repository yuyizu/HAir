package dev.hair.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.Duration
import kotlin.math.ceil

enum class MomentKind(val label: String) { FILM("观影"), GAME("游戏"), BREATH("呼吸") }

/** 记录中途用户按下的一个标记，[label] 是用户自己选的感受。 */
@Serializable
data class MomentMark(val atMillis: Long, val label: String)

/**
 * 一段用户自己记下起止的时间：看电影、玩游戏或做呼吸练习。心率不存在这里，
 * 等手环同步之后再按 [startMillis]..[endMillis] 去取，所以心率和感受各有各的来源。
 */
@Serializable
data class Moment(
    val kind: MomentKind,
    val title: String,
    val startMillis: Long,
    val endMillis: Long? = null,
    val marks: List<MomentMark> = emptyList(),
) {
    init {
        require(endMillis == null || endMillis > startMillis) { "结束时间 $endMillis 必须晚于开始时间 $startMillis" }
    }

    val isOpen: Boolean get() = endMillis == null
}

private const val MAX_MOMENT_MS = 12 * 60 * 60 * 1000L
private const val MIN_MOMENT_MS = 1_000L

/** 全部片段，新的在前；同一时间最多一段在进行中。 */
@Serializable
data class MomentLog(val moments: List<Moment> = emptyList()) {
    val open: Moment? get() = moments.firstOrNull { it.isOpen }

    fun start(kind: MomentKind, title: String, nowMillis: Long): MomentLog {
        check(open == null) { "已有一段正在记录" }
        return copy(moments = listOf(Moment(kind, title.trim().ifEmpty { kind.label }, nowMillis)) + moments)
    }

    fun mark(label: String, nowMillis: Long): MomentLog = updateOpen { it.copy(marks = it.marks + MomentMark(nowMillis, label)) }

    /** 结束进行中的片段；忘了结束时最长只算 12 小时，太短的片段也至少 1 秒，保证起止有先后。 */
    fun finish(nowMillis: Long): MomentLog = updateOpen {
        it.copy(endMillis = nowMillis.coerceIn(it.startMillis + MIN_MOMENT_MS, it.startMillis + MAX_MOMENT_MS))
    }

    fun delete(startMillis: Long): MomentLog = copy(moments = moments.filterNot { it.startMillis == startMillis })

    private fun updateOpen(change: (Moment) -> Moment): MomentLog {
        val current = checkNotNull(open) { "没有正在记录的片段" }
        return copy(moments = moments.map { if (it === current) change(it) else it })
    }
}

/** 把 [MomentLog] 存成单个 JSON 文件；用户自己写的记录，不随退出登录清除。 */
class MomentStore(private val file: File) {

    /** 文件不存在、损坏或内容不合法时回到空记录；下一次 [save] 会覆盖坏文件。 */
    fun load(): MomentLog {
        if (!file.exists()) return MomentLog()
        return try {
            HealthJson.decodeFromString<MomentLog>(file.readText())
        } catch (e: IllegalArgumentException) {
            // SerializationException 是它的子类；init 里的 require 失败也会走到这里
            MomentLog()
        }
    }

    fun save(log: MomentLog) = file.writeTextAtomically(HealthJson.encodeToString(log))
}

private const val MAX_BUCKETS = 600
private const val EDGE_BUCKETS = 2

/** 汇总窗口取整分钟，让任意长度的片段都不超过 [MAX_BUCKETS] 个窗口，一次请求取完。 */
fun momentWindow(durationMillis: Long): Duration =
    Duration.ofMinutes(ceil(durationMillis / 60_000.0 / MAX_BUCKETS).toLong().coerceAtLeast(1))

/** 平均心率最高的窗口；没有数据返回 null。 */
fun peakBucket(buckets: List<HeartBucket>): HeartBucket? = buckets.maxByOrNull { it.avgBpm }

/** 包含 [sec] 的窗口：起点不晚于 [sec] 的最后一个；[sec] 早于第一个窗口时返回 null。 */
fun bucketAt(buckets: List<HeartBucket>, sec: Long): HeartBucket? = buckets.lastOrNull { it.startSec <= sec }

/** 片段开头和结尾各 [EDGE_BUCKETS] 个窗口的平均心率；窗口不够 4 个（两头会重叠）时返回 null。 */
fun beforeAfter(buckets: List<HeartBucket>): Pair<Double, Double>? {
    if (buckets.size < EDGE_BUCKETS * 2) return null
    return buckets.take(EDGE_BUCKETS).map { it.avgBpm }.average() to buckets.takeLast(EDGE_BUCKETS).map { it.avgBpm }.average()
}
