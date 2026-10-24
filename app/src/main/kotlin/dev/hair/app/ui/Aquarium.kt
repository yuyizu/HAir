package dev.hair.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hair.app.data.Climate
import dev.hair.app.data.Habitat
import dev.hair.app.data.WaterType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/*
 * 水里的生灵。坐标是归一化的：x 是水面宽度的 0..1，y 是水面到水底的 0..1，
 * 画的时候再换成像素，所以水位涨落、屏幕大小变化都不用改模拟。
 */

private const val X_MIN = 0.05f
private const val X_MAX = 0.95f

/** 水深与水宽之比的粗略值：算游向目标的方向时，竖直方向的 1 要折成这么多水平距离。 */
private const val ASPECT = 0.6f
private const val ARRIVED = 0.04f
private const val SLOW_ZONE = 0.1f
private const val BOOST_SECONDS = 0.9f
private const val BOOST_FACTOR = 3.4f
private const val CURIOUS_SECONDS = 2.2f
private const val CURIOUS_FACTOR = 1.4f
private const val CURIOUS_RADIUS_DP = 150f
private const val MIN_HIT_DP = 26f
private const val MAX_BUBBLES = 30
private const val SHRIMP_MIN = 0.34f
private const val SHRIMP_MAX = 0.92f
private const val SHRIMP_Y = 0.955f
private const val SCATTER_DECAY_PER_SECOND = 0.8f
private const val PLANT_SETTLE_PER_SECOND = 0.7f
private const val PLANT_HIT_HALF_WIDTH_DP = 22f

/** 在水里有位置的东西，提示牌跟着它走。 */
internal interface Located {
    val x: Float
    val y: Float
}

internal open class Swimmer(override var x: Float, override var y: Float) : Located {
    var vx = 0f
    var vy = 0f
    var tx = x
    var ty = y
    var retargetIn = 0f
    /** -1..1：朝右为 1，转身时从一侧渐变到另一侧，画出来是身体先收窄再翻过去。 */
    var facing = 1f
    var phase = 0f
    /** 受惊后冲刺的剩余秒数。 */
    var boost = 0f
    /** 被水里的点击吸引、游过去看看的剩余秒数。 */
    var curious = 0f
}

internal class Fish(val species: Species, x: Float, y: Float) : Swimmer(x, y) {
    var bubbleIn = 0f
}

/** 小鱼群：[smalls] 围着群心排开，点一下炸开再慢慢聚拢。 */
internal class School(x: Float, y: Float) : Swimmer(x, y) {
    var scatter = 0f
}

internal class SmallFish(val dx: Float, val dy: Float, val phase: Float)

internal class Shrimp(override var x: Float, var dir: Float) : Located {
    override val y: Float get() = SHRIMP_Y
    var pause = 0f
    var flick = 0f
    var phase = 0f
}

internal class Bubble(var x: Float, var y: Float, val radiusDp: Float, val rise: Float, val phase: Float)

internal enum class PlantKind(val label: String, val cue: Cue) {
    BLADES("苦草", Cue(SceneSound.RUSTLE)),
    KELP("海带", Cue(SceneSound.RUSTLE, 0.8f)),
    CORAL("珊瑚", Cue(SceneSound.CHIME, 1.25f)),
    ANEMONE("海葵", Cue(SceneSound.SQUISH)),
}

internal class Plant(val x: Float, val kind: PlantKind, val heightDp: Float, val phase: Float) {
    /** 被点后摇得更厉害：1 → 0 渐渐平息。 */
    var shake = 0f
}

/** 点中了谁：名字（显示在名字牌上）和该响的声音。 */
internal class Touch(val name: String, val cue: Cue)

/** 点中的动物：名字显示在它身边，[bornAt] 是点击时的场景时间。 */
internal class Toast(val name: String, val who: Located, val bornAt: Float)

internal class Aquarium(species: List<Species>, val habitat: Habitat, private val look: SeasonLook, seed: Int = species.joinToString { it.id }.hashCode()) {
    private val rng = Random(seed)

    val fish: List<Fish> = species.map { s ->
        val range = depthRange(s)
        Fish(s, between(X_MIN, X_MAX), between(range.start, range.endInclusive)).also {
            it.retargetIn = between(1f, 6f)
            it.bubbleIn = between(2f, 10f)
            retarget(it, range)
        }
    }

    val school = School(between(0.2f, 0.8f), between(0.2f, 0.5f)).also { it.retargetIn = between(1f, 4f) }
    val smalls: List<SmallFish> = List(look.schoolSize) { SmallFish(between(-0.07f, 0.07f), between(-0.06f, 0.06f), between(0f, 6.28f)) }
    val shrimps: List<Shrimp> = List(if (look.fishSpeed < 0.7f) 1 else 2) { Shrimp(between(SHRIMP_MIN, SHRIMP_MAX), if (rng.nextBoolean()) 1f else -1f) }
    val plants: List<Plant> = plantsFor(habitat)
    val bubbles = ArrayList<Bubble>()
    val shrimpName = if (habitat.water == WaterType.FRESH) "米虾" else "小虾"

    /** 快照状态：设置了就触发重绘，所以关闭动画、时钟停着的时候提示牌也能出现。 */
    var toast: Toast? by mutableStateOf(null)

    private fun between(a: Float, b: Float) = a + (b - a) * rng.nextFloat()

    /** 这种鱼在当前季节活动的水层：冬天整体沉下去，夏天浮上来。 */
    private fun depthRange(s: Species): ClosedFloatingPointRange<Float> {
        val low = (s.depth.start + look.fishDepthShift).coerceIn(0.1f, 0.88f)
        val high = (s.depth.endInclusive + look.fishDepthShift).coerceIn(low + 0.05f, 0.94f)
        return low..high
    }

    private fun plantsFor(habitat: Habitat): List<Plant> {
        val kinds = when {
            habitat.water == WaterType.FRESH -> List(6) { PlantKind.BLADES }
            habitat.climate == Climate.TROPICAL -> listOf(PlantKind.CORAL, PlantKind.ANEMONE, PlantKind.CORAL, PlantKind.CORAL)
            else -> List(4) { PlantKind.KELP }
        }
        // 水草长在左侧文字区之外
        return kinds.mapIndexed { i, kind ->
            val x = 0.36f + 0.58f * (i + 0.5f) / kinds.size + between(-0.025f, 0.025f)
            val height = when (kind) {
                PlantKind.BLADES -> between(40f, 80f)
                PlantKind.KELP -> between(60f, 100f)
                PlantKind.CORAL -> between(20f, 38f)
                PlantKind.ANEMONE -> 24f
            }
            Plant(x, kind, height * look.plantHeight, between(0f, 6.28f))
        }
    }

    fun step(dt: Float) {
        fish.forEach { f ->
            val range = depthRange(f.species)
            swim(f, f.species.speed * look.fishSpeed, range, dt)
            f.bubbleIn -= dt
            if (f.bubbleIn <= 0f) {
                f.bubbleIn = between(7f, 15f)
                emitBubble(f.x + 0.03f * f.facing, f.y)
            }
        }
        swim(school, SCHOOL_SPEED * look.fishSpeed, SCHOOL_DEPTH, dt)
        plants.forEach { it.shake = max(0f, it.shake - dt * PLANT_SETTLE_PER_SECOND) }
        school.scatter = max(0f, school.scatter - dt * SCATTER_DECAY_PER_SECOND)
        shrimps.forEach { step(it, dt) }
        bubbles.forEach { b ->
            b.y -= b.rise * dt
            b.x += sin(b.y * 40f + b.phase) * 0.012f * dt
        }
        bubbles.removeAll { it.y <= 0.01f }
    }

    private fun swim(s: Swimmer, speed: Float, range: ClosedFloatingPointRange<Float>, dt: Float) {
        s.retargetIn -= dt
        s.boost = max(0f, s.boost - dt)
        s.curious = max(0f, s.curious - dt)
        val dx = s.tx - s.x
        val dy = s.ty - s.y
        val distance = hypot(dx, dy * ASPECT)
        if (s.boost == 0f && s.curious == 0f && (distance < ARRIVED || s.retargetIn <= 0f)) retarget(s, range)

        val factor = if (s.boost > 0f) BOOST_FACTOR else if (s.curious > 0f) CURIOUS_FACTOR else 1f
        // 快到目标时减速，不会冲过头再折回来
        val pace = speed * factor * min(1f, distance / SLOW_ZONE)
        val wantX = if (distance > 1e-4f) dx / distance * pace else 0f
        val wantY = if (distance > 1e-4f) dy / distance * pace else 0f
        val steer = min(1f, dt * if (s.boost > 0f) 8f else 2.5f)
        s.vx += (wantX - s.vx) * steer
        s.vy += (wantY - s.vy) * steer
        s.x = (s.x + s.vx * dt).coerceIn(X_MIN, X_MAX)
        s.y = (s.y + s.vy * dt).coerceIn(range.start, range.endInclusive)

        val heading = if (abs(s.vx) > 0.004f) sign(s.vx) else s.facing
        s.facing += (heading - s.facing) * min(1f, dt * 6f)
        s.phase += dt * min(60f, 5f + 380f * hypot(s.vx, s.vy * ASPECT))
    }

    private fun retarget(s: Swimmer, range: ClosedFloatingPointRange<Float>) {
        var nx = between(X_MIN, X_MAX)
        // 太近的目标不算换地方：推到至少 0.2 以外
        if (abs(nx - s.x) < 0.2f) nx = if (s.x < 0.5f) s.x + between(0.25f, 0.6f) else s.x - between(0.25f, 0.6f)
        s.tx = nx.coerceIn(X_MIN, X_MAX)
        s.ty = between(range.start, range.endInclusive)
        s.retargetIn = between(4f, 9f)
    }

    private fun step(s: Shrimp, dt: Float) {
        s.phase += dt
        if (s.flick > 0f) {
            // 受惊后向后弹开
            s.flick -= dt
            s.x = (s.x - s.dir * 0.12f * dt).coerceIn(SHRIMP_MIN, SHRIMP_MAX)
            return
        }
        if (s.pause > 0f) {
            s.pause -= dt
            return
        }
        s.x += s.dir * 0.014f * look.fishSpeed * dt
        if (s.x >= SHRIMP_MAX) s.dir = -1f else if (s.x <= SHRIMP_MIN) s.dir = 1f
        if (rng.nextFloat() < dt * 0.25f) s.pause = between(1.5f, 4.5f)
    }

    private fun emitBubble(x: Float, y: Float) {
        if (bubbles.size >= MAX_BUBBLES) return
        bubbles += Bubble(x + between(-0.01f, 0.01f), y, between(1.4f, 2.6f), between(0.18f, 0.3f), between(0f, 6.28f))
    }

    /** 小鱼 [fish] 现在的位置：群心加上自己的偏移，被点后偏移放大、再缩回。 */
    fun smallPosition(fish: SmallFish, t: Float): Pair<Float, Float> {
        val spread = 1f + 3f * school.scatter
        return Pair(
            school.x + fish.dx * spread + sin(t * 1.7f + fish.phase) * 0.008f,
            school.y + fish.dy * spread + sin(t * 2.3f + fish.phase * 1.3f) * 0.012f,
        )
    }

    /**
     * 点在水里的 ([nx], [ny])（归一化），[widthDp]×[depthDp] 是水体大小，[now] 是场景时间。
     * 点中鱼、小鱼群、虾或水草：做出反应并显示名字，返回名字和声音；点在空处：附近的鱼游过去看看，返回 null。
     */
    fun touch(nx: Float, ny: Float, widthDp: Float, depthDp: Float, now: Float): Touch? {
        fun distanceDp(x: Float, y: Float) = hypot((x - nx) * widthDp, (y - ny) * depthDp)

        val nearest = fish.minByOrNull { distanceDp(it.x, it.y) }
        if (nearest != null && distanceDp(nearest.x, nearest.y) <= max(nearest.species.lengthDp * 0.6f, MIN_HIT_DP)) {
            startle(nearest, nx)
            repeat(5) { emitBubble(nearest.x, nearest.y) }
            // 鱼越大，水声越低沉
            return show(nearest.species.name, nearest, now, Cue(SceneSound.FISH, (32f / nearest.species.lengthDp).coerceIn(0.75f, 1.5f)))
        }
        if (smalls.any { val (x, y) = smallPosition(it, now); distanceDp(x, y) <= MIN_HIT_DP }) {
            school.scatter = 1f
            return show("小鱼群", school, now, Cue(SceneSound.SCHOOL))
        }
        shrimps.firstOrNull { distanceDp(it.x, it.y) <= MIN_HIT_DP }?.let { shrimp ->
            shrimp.flick = 0.4f
            return show(shrimpName, shrimp, now, Cue(SceneSound.SHRIMP))
        }
        plants.firstOrNull { abs(it.x - nx) * widthDp <= PLANT_HIT_HALF_WIDTH_DP && (1f - ny) * depthDp <= it.heightDp + 8f }?.let { plant ->
            plant.shake = 1f
            val top = (1f - plant.heightDp / depthDp).coerceIn(0.3f, 0.9f)
            return show(plant.kind.label, object : Located { override val x = plant.x; override val y = top }, now, plant.kind.cue)
        }
        fish.filter { distanceDp(it.x, it.y) <= CURIOUS_RADIUS_DP && it.boost == 0f }.forEach { f ->
            val range = depthRange(f.species)
            f.curious = CURIOUS_SECONDS
            f.tx = nx.coerceIn(X_MIN, X_MAX)
            f.ty = ny.coerceIn(range.start, range.endInclusive)
        }
        return null
    }

    private fun startle(f: Fish, fromX: Float) {
        val range = depthRange(f.species)
        f.boost = BOOST_SECONDS
        f.curious = 0f
        f.tx = (if (f.x >= fromX) f.x + 0.4f else f.x - 0.4f).coerceIn(X_MIN, X_MAX)
        f.ty = between(range.start, range.endInclusive)
    }

    private fun show(name: String, who: Located, now: Float, cue: Cue): Touch {
        toast = Toast(name, who, now)
        return Touch(name, cue)
    }

    private companion object {
        const val SCHOOL_SPEED = 0.07f
        val SCHOOL_DEPTH = 0.18f..0.55f
    }
}
