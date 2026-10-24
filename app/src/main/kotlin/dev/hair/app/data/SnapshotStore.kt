package dev.hair.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/** 一次完整拉取的结果，是界面和后台同步共用的唯一缓存单元。 */
@Serializable
data class Snapshot(
    val overview: Overview,
    val nights: List<Night>,
    val fetchedAtMillis: Long,
    /** 近 30 天的每日汇总，升序；旧缓存没有这一项时为空，下次刷新后补齐。 */
    val vitals: List<DailyVitals> = emptyList(),
)

/**
 * 把最近一次 [Snapshot] 存成单个 JSON 文件。缓存是派生数据：
 * 文件损坏或结构已变化时直接丢弃，由下一次刷新重建。
 */
class SnapshotStore(private val file: File) {

    fun load(): Snapshot? {
        if (!file.exists()) return null
        return try {
            HealthJson.decodeFromString<Snapshot>(file.readText())
        } catch (e: SerializationException) {
            file.delete()
            null
        }
    }

    fun save(snapshot: Snapshot) = file.writeTextAtomically(HealthJson.encodeToString(snapshot))

    fun clear() {
        file.delete()
    }
}
