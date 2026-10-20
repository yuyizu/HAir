package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GoalStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "goals.json")

    @Test
    fun `没有文件时返回默认目标`() {
        assertEquals(Goals(), GoalStore(file).load())
    }

    @Test
    fun `保存后读回的目标与原值相等，重建 store 也一样`() {
        val goals = Goals(steps = 12_500, sleepMin = 465)
        GoalStore(file).save(goals)
        assertEquals(goals, GoalStore(file).load())
    }

    @Test
    fun `再次保存会替换旧目标，且不残留临时文件`() {
        val store = GoalStore(file)
        store.save(Goals(steps = 10_000))
        store.save(Goals(steps = 6_000))
        assertEquals(Goals(steps = 6_000), store.load())
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun `文件损坏时回到默认目标，下一次保存能覆盖它`() {
        file.writeText("{not json")
        val store = GoalStore(file)
        assertEquals(Goals(), store.load())
        store.save(Goals(steps = 9_000))
        assertEquals(Goals(steps = 9_000), store.load())
    }

    @Test
    fun `文件里的目标超出范围时回到默认目标`() {
        file.writeText("""{"steps":0,"sleepMin":480}""")
        assertEquals(Goals(), GoalStore(file).load())
    }
}
