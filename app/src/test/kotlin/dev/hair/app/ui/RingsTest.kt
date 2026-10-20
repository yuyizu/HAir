package dev.hair.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingsTest {
    @Test
    fun `没有数据时返回 null，用来画空圈`() {
        assertNull(ringProgress(null, 8_000))
    }

    @Test
    fun `按目标计算完成度`() {
        assertEquals(0.25f, ringProgress(2_000, 8_000)!!, 0.0001f)
        assertEquals(0f, ringProgress(0, 8_000)!!, 0f)
    }

    @Test
    fun `超过目标按满环处理`() {
        assertEquals(1f, ringProgress(12_000, 8_000)!!, 0f)
    }
}
