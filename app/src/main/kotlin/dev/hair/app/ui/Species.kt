package dev.hair.app.ui

import androidx.compose.ui.graphics.Color
import dev.hair.app.data.Climate
import dev.hair.app.data.Habitat
import dev.hair.app.data.WaterType

/** 体型：[height] 是体高与体长之比，[tail] 和 [dorsal] 是尾鳍、背鳍的相对大小。 */
internal enum class BodyShape(val height: Float, val tail: Float, val dorsal: Float) {
    SLIM(0.26f, 0.9f, 0.5f),
    OVAL(0.42f, 1f, 0.7f),
    DEEP(0.72f, 0.8f, 1f),
    LONG(0.17f, 0.5f, 0.2f),
}

internal enum class Pattern { NONE, BANDS, STRIPE, PATCHES, SPOTS }

/** 一种鱼：长什么样、游多快、在水的哪一层活动。速度是每秒游过的水面宽度比例，深度是水面到水底的 0..1。 */
internal class Species(
    val id: String,
    val name: String,
    val habitat: Habitat,
    val lengthDp: Float,
    val shape: BodyShape,
    val body: Color,
    val belly: Color,
    val accent: Color = body,
    val pattern: Pattern = Pattern.NONE,
    /** [Pattern.BANDS] 的条数。 */
    val bands: Int = 3,
    val tailScale: Float = 1f,
    val speed: Float,
    val depth: ClosedFloatingPointRange<Float>,
)

private fun rgb(hex: Int) = Color(0xFF000000.toInt() or hex)

private val FRESH_TEMPERATE = Habitat(WaterType.FRESH, Climate.TEMPERATE)
private val FRESH_TROPICAL = Habitat(WaterType.FRESH, Climate.TROPICAL)
private val FRESH_COLD = Habitat(WaterType.FRESH, Climate.COLD)
private val SEA_TROPICAL = Habitat(WaterType.SEA, Climate.TROPICAL)
private val SEA_TEMPERATE = Habitat(WaterType.SEA, Climate.TEMPERATE)
private val SEA_COLD = Habitat(WaterType.SEA, Climate.COLD)

/** 每个环境四种，同一环境里按这里的顺序排；没选过时取前 [DEFAULT_SPECIES_COUNT] 种。 */
internal val ALL_SPECIES: List<Species> = listOf(
    Species("koi", "锦鲤", FRESH_TEMPERATE, 46f, BodyShape.OVAL, rgb(0xF2EDE4), rgb(0xFFFFFF), rgb(0xE8542A), Pattern.PATCHES, speed = 0.05f, depth = 0.25f..0.6f),
    Species("crucian", "鲫鱼", FRESH_TEMPERATE, 30f, BodyShape.OVAL, rgb(0xB9A37A), rgb(0xE3D5B2), speed = 0.06f, depth = 0.3f..0.7f),
    Species("goldfish", "金鱼", FRESH_TEMPERATE, 26f, BodyShape.OVAL, rgb(0xF08A2B), rgb(0xFFC46B), tailScale = 1.6f, speed = 0.045f, depth = 0.2f..0.55f),
    Species("loach", "泥鳅", FRESH_TEMPERATE, 34f, BodyShape.LONG, rgb(0x8A7458), rgb(0xC0AE8E), rgb(0x5E4C38), Pattern.SPOTS, speed = 0.035f, depth = 0.8f..0.93f),

    Species("guppy", "孔雀鱼", FRESH_TROPICAL, 17f, BodyShape.SLIM, rgb(0x7FB7D6), rgb(0xCFE6F2), rgb(0xF28C38), Pattern.SPOTS, tailScale = 1.7f, speed = 0.07f, depth = 0.15f..0.5f),
    Species("neon", "霓虹灯鱼", FRESH_TROPICAL, 15f, BodyShape.SLIM, rgb(0x9FB4C8), rgb(0xE0452E), rgb(0x35C7F2), Pattern.STRIPE, speed = 0.075f, depth = 0.2f..0.55f),
    Species("angelfish", "神仙鱼", FRESH_TROPICAL, 30f, BodyShape.DEEP, rgb(0xDADFE2), rgb(0xF2F4F5), rgb(0x30363B), Pattern.BANDS, bands = 2, speed = 0.035f, depth = 0.3f..0.65f),
    Species("betta", "斗鱼", FRESH_TROPICAL, 24f, BodyShape.OVAL, rgb(0xC8283C), rgb(0xE86A78), rgb(0x7B1226), tailScale = 1.7f, speed = 0.04f, depth = 0.25f..0.6f),

    Species("char", "红点鲑", FRESH_COLD, 38f, BodyShape.OVAL, rgb(0x5E6B4A), rgb(0xE0653A), rgb(0xF0C0B0), Pattern.SPOTS, speed = 0.06f, depth = 0.4f..0.8f),
    Species("trout", "虹鳟", FRESH_COLD, 42f, BodyShape.SLIM, rgb(0xA9B8BC), rgb(0xE6ECEE), rgb(0xE8A0A8), Pattern.STRIPE, speed = 0.065f, depth = 0.3f..0.75f),
    Species("carp", "鲤鱼", FRESH_COLD, 44f, BodyShape.OVAL, rgb(0xC9A24B), rgb(0xE9D9A6), speed = 0.05f, depth = 0.5f..0.9f),
    Species("perch", "河鲈", FRESH_COLD, 30f, BodyShape.OVAL, rgb(0x7C9A4E), rgb(0xE4D88A), rgb(0x3F5A2A), Pattern.BANDS, bands = 4, speed = 0.055f, depth = 0.35f..0.75f),

    Species("clownfish", "小丑鱼", SEA_TROPICAL, 20f, BodyShape.OVAL, rgb(0xF0742A), rgb(0xF59A55), rgb(0xFFFFFF), Pattern.BANDS, speed = 0.04f, depth = 0.78f..0.92f),
    Species("bluetang", "蓝吊", SEA_TROPICAL, 30f, BodyShape.DEEP, rgb(0x2F6FD8), rgb(0x4F8FE8), rgb(0x101820), Pattern.STRIPE, speed = 0.05f, depth = 0.3f..0.6f),
    Species("butterflyfish", "蝴蝶鱼", SEA_TROPICAL, 24f, BodyShape.DEEP, rgb(0xF6D84A), rgb(0xFBEFA0), rgb(0x2A2F36), Pattern.BANDS, bands = 1, speed = 0.045f, depth = 0.35f..0.7f),
    Species("damselfish", "雀鲷", SEA_TROPICAL, 15f, BodyShape.OVAL, rgb(0x2E8BFF), rgb(0x6AB5FF), speed = 0.08f, depth = 0.2f..0.5f),

    Species("croaker", "黄鱼", SEA_TEMPERATE, 40f, BodyShape.SLIM, rgb(0xE0B140), rgb(0xF3DB8C), speed = 0.055f, depth = 0.45f..0.8f),
    Species("seabass", "鲈鱼", SEA_TEMPERATE, 40f, BodyShape.SLIM, rgb(0xA7B5BC), rgb(0xE5EBEE), rgb(0x5E6E78), Pattern.SPOTS, speed = 0.06f, depth = 0.35f..0.75f),
    Species("blackbream", "黑鲷", SEA_TEMPERATE, 32f, BodyShape.OVAL, rgb(0x59636B), rgb(0x99A3AB), speed = 0.05f, depth = 0.5f..0.85f),
    Species("redbream", "真鲷", SEA_TEMPERATE, 36f, BodyShape.OVAL, rgb(0xE26A63), rgb(0xF2A59F), rgb(0x6FC2D6), Pattern.SPOTS, speed = 0.05f, depth = 0.35f..0.7f),

    Species("cod", "鳕鱼", SEA_COLD, 44f, BodyShape.SLIM, rgb(0x8A8A58), rgb(0xD8D2A8), rgb(0x5E5A38), Pattern.SPOTS, speed = 0.05f, depth = 0.55f..0.9f),
    Species("herring", "鲱鱼", SEA_COLD, 28f, BodyShape.SLIM, rgb(0x8EA9C0), rgb(0xE4ECF2), speed = 0.08f, depth = 0.25f..0.6f),
    Species("salmon", "鲑鱼", SEA_COLD, 42f, BodyShape.SLIM, rgb(0xB7C3C8), rgb(0xF0F2F2), rgb(0x6C7A80), Pattern.SPOTS, speed = 0.07f, depth = 0.3f..0.7f),
    Species("mackerel", "鲭鱼", SEA_COLD, 32f, BodyShape.SLIM, rgb(0x3E8C8A), rgb(0xDCE8E6), rgb(0x1F4A52), Pattern.BANDS, bands = 5, speed = 0.09f, depth = 0.25f..0.6f),
)

private const val DEFAULT_SPECIES_COUNT = 3

internal fun speciesIn(habitat: Habitat): List<Species> = ALL_SPECIES.filter { it.habitat == habitat }

/** 水里养的鱼：用户选的（只认这个环境里有的），没选或选的都不在这个环境时用默认几种。 */
internal fun speciesFor(habitat: Habitat, chosen: List<String>): List<Species> {
    val pool = speciesIn(habitat)
    return pool.filter { it.id in chosen }.ifEmpty { pool.take(DEFAULT_SPECIES_COUNT) }
}

/** 勾选或取消勾选 [id] 之后的选择，按这个环境里的顺序排；至少留一种，所以只剩一种时取消不生效。 */
internal fun toggledSpecies(habitat: Habitat, chosen: List<String>, id: String): List<String> {
    val current = speciesFor(habitat, chosen).map { it.id }
    val next = if (id in current) current - id else current + id
    return if (next.isEmpty()) current else speciesIn(habitat).map { it.id }.filter { it in next }
}
