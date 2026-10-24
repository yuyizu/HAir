package dev.hair.app.ui

import java.time.LocalDate

/**
 * 盖在标签页上的整页。[route] 是能存进 rememberSaveable 的字符串（进程被回收后还能回到原页），
 * [parent] 是返回时回到的上一页，null 表示回到标签页。
 */
sealed interface Route {
    val route: String
    val backLabel: String
    val parent: Route? get() = null

    data object Activity : Route {
        override val route = "activity"
        override val backLabel = "今日"
    }

    data object Forecast : Route {
        override val route = "forecast"
        override val backLabel = "今日"
    }

    data class NightView(val date: LocalDate) : Route {
        override val route = "night:$date"
        override val backLabel = "睡眠"
    }

    data object Lab : Route {
        override val route = "lab"
        override val backLabel = "探索"
    }

    data object Pet : Route {
        override val route = "pet"
        override val backLabel = "探索"
    }

    data object Sky : Route {
        override val route = "sky"
        override val backLabel = "探索"
    }

    data object Art : Route {
        override val route = "art"
        override val backLabel = "探索"
    }

    data object Moments : Route {
        override val route = "moments"
        override val backLabel = "探索"
    }

    data object Breath : Route {
        override val route = "breath"
        override val backLabel = "心跳记忆"
        override val parent = Moments
    }

    data class MomentView(val startMillis: Long) : Route {
        override val route = "moment:$startMillis"
        override val backLabel = "心跳记忆"
        override val parent = Moments
    }

    companion object {
        /** 认不出的字符串（比如旧版本存下的）返回 null，调用方回到标签页。 */
        fun parse(route: String): Route? = when {
            route == Activity.route -> Activity
            route == Forecast.route -> Forecast
            route == Lab.route -> Lab
            route == Pet.route -> Pet
            route == Sky.route -> Sky
            route == Art.route -> Art
            route == Moments.route -> Moments
            route == Breath.route -> Breath
            route.startsWith("night:") -> runCatching { NightView(LocalDate.parse(route.removePrefix("night:"))) }.getOrNull()
            route.startsWith("moment:") -> route.removePrefix("moment:").toLongOrNull()?.let(::MomentView)
            else -> null
        }
    }
}
