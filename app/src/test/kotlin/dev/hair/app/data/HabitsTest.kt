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
import java.time.LocalDate

class HabitsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val coffee = "下午喝咖啡"
    private val d0 = LocalDate.of(2026, 10, 1)

    private fun date(offset: Int) = d0.plusDays(offset.toLong())

    @Test
    fun `同一天同一习惯只留最后一次记录，传 null 清除`() {
        val log = HabitLog().withMark(date(0), coffee, true).withMark(date(0), coffee, false)

        assertEquals(false, log.markOf(date(0), coffee))
        assertEquals(1, log.marks.size)
        assertNull(log.withMark(date(0), coffee, null).markOf(date(0), coffee))
    }

    @Test
    fun `记录未知习惯是错误`() {
        assertThrows(IllegalArgumentException::class.java) { HabitLog().withMark(date(0), "没有这个习惯", true) }
    }

    @Test
    fun `新增习惯去掉首尾空格，重名不重复添加，空名和过长的名字被拒绝`() {
        val log = HabitLog().withHabit("  早起  ").withHabit("早起")

        assertEquals(1, log.habits.count { it == "早起" })
        assertThrows(IllegalArgumentException::class.java) { HabitLog().withHabit("   ") }
        assertThrows(IllegalArgumentException::class.java) { HabitLog().withHabit("一二三四五六七八九十一二三") }
    }

    @Test
    fun `删除习惯时同时删掉它的记录`() {
        val log = HabitLog().withMark(date(0), coffee, true).withMark(date(0), "运动", true).withoutHabit(coffee)

        assertFalse(coffee in log.habits)
        assertEquals(listOf("运动"), log.marks.map { it.habit })
    }

    private fun night(date: LocalDate, asleep: Long, deep: Long = 60) = Night(date, asleep, asleep + 20, deep, 90, 200, 20)

    /** 标记在 D 日，结果在 D+1 日：做了的 3 天睡 400 分钟、静息心率 64，没做的 3 天睡 460、静息心率 60。 */
    private fun sixDays(): Triple<HabitLog, List<Night>, List<DailyVitals>> {
        var log = HabitLog()
        val nights = mutableListOf<Night>()
        val vitals = mutableListOf<DailyVitals>()
        for (i in 0 until 6) {
            val done = i < 3
            log = log.withMark(date(i), coffee, done)
            nights += night(date(i + 1), if (done) 400 else 460)
            vitals += DailyVitals(date(i + 1), restingHr = if (done) 64 else 60)
        }
        return Triple(log, nights, vitals)
    }

    @Test
    fun `结果取标记日的第二天，分别统计做了和没做的天`() {
        val (log, nights, vitals) = sixDays()

        val c = compareHabit(coffee, log, nights, vitals)

        assertEquals(3, c.doneDays)
        assertEquals(3, c.skippedDays)
        val sleep = c.outcomes.single { it.outcome == Outcome.ASLEEP }
        assertEquals(400.0, sleep.done.mean!!, 0.0)
        assertEquals(460.0, sleep.skipped.mean!!, 0.0)
        assertEquals(-60.0, sleep.difference!!, 0.0)
        assertTrue(sleep.enough)
        assertEquals(4.0, c.outcomes.single { it.outcome == Outcome.RESTING_HR }.difference!!, 0.0)
    }

    @Test
    fun `某组不足 3 天时标为样本不足`() {
        val (log, nights, vitals) = sixDays()
        val trimmed = log.copy(marks = log.marks.filterNot { it.date == date(0) })

        val sleep = compareHabit(coffee, trimmed, nights, vitals).outcomes.single { it.outcome == Outcome.ASLEEP }

        assertEquals(2, sleep.done.days)
        assertFalse(sleep.enough)
    }

    @Test
    fun `第二天没有对应数据的标记不计入该结果的天数`() {
        val (log, nights, vitals) = sixDays()

        val c = compareHabit(coffee, log, nights.filterNot { it.date == date(1) }, vitals)

        assertEquals(2, c.outcomes.single { it.outcome == Outcome.ASLEEP }.done.days)
        assertEquals(3, c.outcomes.single { it.outcome == Outcome.RESTING_HR }.done.days)
        assertEquals(3, c.doneDays)
    }

    @Test
    fun `没有任何记录时各组为空，差值为 null`() {
        val sleep = compareHabit(coffee, HabitLog(), emptyList(), emptyList()).outcomes.first()

        assertEquals(0, sleep.done.days)
        assertNull(sleep.done.mean)
        assertNull(sleep.difference)
        assertFalse(sleep.enough)
    }

    @Test
    fun `存储：保存后读回一致`() {
        val store = HabitStore(File(tmp.root, "habits.json"))
        val log = HabitLog().withHabit("早起").withMark(date(0), "早起", true)

        store.save(log)

        assertEquals(log, store.load())
    }

    @Test
    fun `存储：没有文件、文件损坏或内容不合法时回到默认`() {
        val file = File(tmp.root, "habits.json")
        val store = HabitStore(file)
        assertEquals(HabitLog(), store.load())

        file.writeText("{坏的")
        assertEquals(HabitLog(), store.load())

        file.writeText("""{"habits":["a","a"],"marks":[]}""")
        assertEquals(HabitLog(), store.load())
    }
}
