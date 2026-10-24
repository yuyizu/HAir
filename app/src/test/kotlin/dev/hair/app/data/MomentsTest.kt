package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration

class MomentsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val t0 = 1_700_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `开始、标记、结束一段记录`() {
        val log = MomentLog()
            .start(MomentKind.FILM, "  沙丘  ", t0)
            .mark("紧张", t0 + 36 * minute)
            .finish(t0 + 2 * hour)

        val m = log.moments.single()
        assertEquals("沙丘", m.title)
        assertEquals(listOf(MomentMark(t0 + 36 * minute, "紧张")), m.marks)
        assertEquals(t0 + 2 * hour, m.endMillis)
        assertNull(log.open)
    }

    @Test
    fun `没起名时用类型名，新的记录排在前面`() {
        val log = MomentLog().start(MomentKind.GAME, " ", t0).finish(t0 + hour).start(MomentKind.BREATH, "", t0 + 2 * hour)

        assertEquals(listOf("呼吸", "游戏"), log.moments.map { it.title })
        assertTrue(log.open!!.isOpen)
    }

    @Test
    fun `同一时间只能有一段在记录；没有进行中的记录时不能标记或结束`() {
        val running = MomentLog().start(MomentKind.FILM, "a", t0)

        assertThrows(IllegalStateException::class.java) { running.start(MomentKind.GAME, "b", t0 + minute) }
        assertThrows(IllegalStateException::class.java) { MomentLog().mark("紧张", t0) }
        assertThrows(IllegalStateException::class.java) { MomentLog().finish(t0) }
    }

    @Test
    fun `忘了结束时最长只算 12 小时，太短的也至少 1 秒`() {
        val long = MomentLog().start(MomentKind.FILM, "a", t0).finish(t0 + 30 * hour).moments.single()
        val instant = MomentLog().start(MomentKind.FILM, "a", t0).finish(t0).moments.single()

        assertEquals(t0 + 12 * hour, long.endMillis)
        assertEquals(t0 + 1_000, instant.endMillis)
    }

    @Test
    fun `结束时间不晚于开始时间的记录不合法`() {
        assertThrows(IllegalArgumentException::class.java) { Moment(MomentKind.FILM, "a", t0, endMillis = t0) }
    }

    @Test
    fun `删除按开始时间匹配`() {
        val log = MomentLog().start(MomentKind.FILM, "a", t0).finish(t0 + hour).start(MomentKind.GAME, "b", t0 + 2 * hour)

        assertEquals(listOf("b"), log.delete(t0).moments.map { it.title })
    }

    @Test
    fun `存储：保存后读回一致，包括进行中的记录；坏文件回到空记录`() {
        val file = File(tmp.root, "moments.json")
        val store = MomentStore(file)
        val log = MomentLog().start(MomentKind.FILM, "a", t0).mark("兴奋", t0 + minute)

        store.save(log)
        assertEquals(log, store.load())

        file.writeText("{坏的")
        assertEquals(MomentLog(), store.load())
    }

    @Test
    fun `汇总窗口取整分钟，窗口数不超过 600`() {
        assertEquals(Duration.ofMinutes(1), momentWindow(30 * minute))
        assertEquals(Duration.ofMinutes(1), momentWindow(10 * hour))
        assertEquals(Duration.ofMinutes(2), momentWindow(10 * hour + minute))
        assertEquals(Duration.ofMinutes(2), momentWindow(12 * hour))
    }

    private fun bucket(startMin: Int, avg: Double) = HeartBucket(startMin * 60L, avg - 3, avg, avg + 5)

    @Test
    fun `最高点、所在窗口和首尾对比`() {
        val buckets = listOf(60.0, 62.0, 70.0, 104.0, 80.0, 66.0, 64.0).mapIndexed { i, avg -> bucket(i, avg) }

        assertEquals(104.0, peakBucket(buckets)!!.avgBpm, 0.0)
        assertEquals(3 * 60L, peakBucket(buckets)!!.startSec)
        assertEquals(70.0, bucketAt(buckets, 2 * 60L + 59)!!.avgBpm, 0.0)
        assertNull(bucketAt(buckets, -1))
        val (before, after) = beforeAfter(buckets)!!
        assertEquals(61.0, before, 0.0)
        assertEquals(65.0, after, 0.0)
    }

    @Test
    fun `窗口少于 4 个时没有首尾对比，空数据没有最高点`() {
        assertNull(beforeAfter(listOf(bucket(0, 60.0), bucket(1, 62.0), bucket(2, 70.0))))
        assertNull(peakBucket(emptyList()))
        assertFalse(beforeAfter(emptyList()) != null)
    }
}
