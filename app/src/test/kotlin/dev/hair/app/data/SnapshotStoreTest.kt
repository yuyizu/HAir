package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class SnapshotStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val night = Night(LocalDate.of(2026, 10, 19), 420, 460, 80, 95, 245, 40)
    private val snapshot = Snapshot(
        overview = Overview(
            lastNight = night,
            restingHr = 58,
            hrvMs = null,
            stepsToday = 1234,
            latestHr = 72,
            cardioFitness = CardioFitness(LocalDate.of(2026, 10, 18), 42.5, CardioLevel.GOOD),
        ),
        nights = listOf(night),
        fetchedAtMillis = 1_760_000_000_000,
    )

    private fun store(): Pair<SnapshotStore, File> {
        val file = File(tmp.root, "snapshot.json")
        return SnapshotStore(file) to file
    }

    @Test
    fun `没有缓存文件时返回 null`() {
        assertNull(store().first.load())
    }

    @Test
    fun `保存后读回的快照与原值完全相等，含 null 字段和日期`() {
        val (store, _) = store()
        store.save(snapshot)
        assertEquals(snapshot, store.load())
    }

    @Test
    fun `再次保存会替换旧快照，且不残留临时文件`() {
        val (store, file) = store()
        store.save(snapshot)
        val newer = snapshot.copy(fetchedAtMillis = snapshot.fetchedAtMillis + 1)
        store.save(newer)
        assertEquals(newer, store.load())
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun `缓存文件损坏时返回 null 并删除该文件`() {
        val (store, file) = store()
        file.writeText("{not json")
        assertNull(store.load())
        assertFalse(file.exists())
    }

    @Test
    fun `缓存结构与当前版本不符时视为无缓存`() {
        val (store, file) = store()
        file.writeText("""{"overview":{"lastNight":null},"nights":[]}""")
        assertNull(store.load())
    }

    @Test
    fun `clear 删除缓存`() {
        val (store, _) = store()
        store.save(snapshot)
        store.clear()
        assertNull(store.load())
    }
}
