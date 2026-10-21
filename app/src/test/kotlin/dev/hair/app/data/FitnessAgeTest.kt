package dev.hair.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 期望值直接取自 FRIEND 参考表各年龄段的数值，以及相邻两段之间的手算内插。 */
class FitnessAgeTest {
    @Test
    fun `VO2max 等于某年龄段的参考值时，健康年龄就是该段中点`() {
        assertEquals(FitnessAge.Exact(35), fitnessAge(42.4, Sex.MALE))
        assertEquals(FitnessAge.Exact(35), fitnessAge(30.2, Sex.FEMALE))
        assertEquals(FitnessAge.Exact(25), fitnessAge(48.0, Sex.MALE))
        assertEquals(FitnessAge.Exact(75), fitnessAge(24.4, Sex.MALE))
        assertEquals(FitnessAge.Exact(75), fitnessAge(18.3, Sex.FEMALE))
    }

    @Test
    fun `VO2max 落在两段之间时线性内插并四舍五入`() {
        // 男性 35 岁 42.4、45 岁 37.8：40.1 正好在中间
        assertEquals(FitnessAge.Exact(40), fitnessAge(40.1, Sex.MALE))
        // 女性 35 岁 30.2、45 岁 26.7：28.45 正好在中间
        assertEquals(FitnessAge.Exact(40), fitnessAge(28.45, Sex.FEMALE))
        // 男性 55 岁 32.6、65 岁 28.2：55 + (32.6 - 31.0) / 4.4 * 10 = 58.64，四舍五入得 59（截断会是 58）
        assertEquals(FitnessAge.Exact(59), fitnessAge(31.0, Sex.MALE))
    }

    @Test
    fun `同一个 VO2max 对男女给出不同的健康年龄`() {
        assertEquals(FitnessAge.Exact(55), fitnessAge(32.6, Sex.MALE))
        assertEquals(FitnessAge.Exact(25), fitnessAge(37.6, Sex.FEMALE))
    }

    @Test
    fun `VO2max 高于最年轻一段时只给上限，不外推`() {
        assertEquals(FitnessAge.AtMost(25), fitnessAge(55.0, Sex.MALE))
        assertEquals(FitnessAge.AtMost(25), fitnessAge(37.7, Sex.FEMALE))
    }

    @Test
    fun `VO2max 低于最年长一段时只给下限，不外推`() {
        assertEquals(FitnessAge.AtLeast(75), fitnessAge(20.0, Sex.MALE))
        assertEquals(FitnessAge.AtLeast(75), fitnessAge(18.2, Sex.FEMALE))
    }
}
