package dev.hair.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hair.app.HairApp
import dev.hair.app.data.DetailRange
import dev.hair.app.data.HabitLog
import dev.hair.app.data.HealthApiException
import dev.hair.app.data.HeartBucket
import dev.hair.app.data.Metric
import dev.hair.app.data.Moment
import dev.hair.app.data.MomentLog
import dev.hair.app.data.Night
import dev.hair.app.data.NightDetail
import dev.hair.app.data.Series
import dev.hair.app.data.Snapshot
import dev.hair.app.data.UserSettings
import dev.hair.app.data.momentWindow
import dev.hair.app.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import java.io.IOException
import java.time.Instant

sealed interface UiState {
    data object Loading : UiState
    data object SignedOut : UiState

    /** [refreshing] 为 true 时正在后台更新；[refreshError] 非空表示这次更新失败，展示的是旧缓存。 */
    data class Ready(
        val snapshot: Snapshot,
        val refreshing: Boolean = false,
        val refreshError: String? = null,
    ) : UiState

    data class Error(val message: String) : UiState
}

/** 详情页一次现取数据的结果；失败的 [message] 可直接展示给用户。 */
sealed interface LoadResult<out T> {
    data class Loaded<T>(val value: T) : LoadResult<T>
    data class Failed(val message: String) : LoadResult<Nothing>
}

class HairViewModel(app: Application) : AndroidViewModel(app) {
    private val hair = app as HairApp
    private val auth = hair.auth
    private val repository = hair.repository

    private val _state = MutableStateFlow<UiState>(if (auth.isSignedIn) UiState.Loading else UiState.SignedOut)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val settingsStore = hair.settingsStore
    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val habitStore = hair.habitStore
    private val _habits = MutableStateFlow(habitStore.load())
    val habits: StateFlow<HabitLog> = _habits.asStateFlow()

    private val momentStore = hair.momentStore
    private val _moments = MutableStateFlow(momentStore.load())
    val moments: StateFlow<MomentLog> = _moments.asStateFlow()

    init {
        if (auth.isSignedIn) refresh()
    }

    fun signInIntent(): Intent? = try {
        auth.authIntent()
    } catch (e: IllegalStateException) {
        _state.value = UiState.Error(e.message.orEmpty())
        null
    }

    fun onAuthResult(data: Intent) = load {
        auth.completeSignIn(data)
        repository.verifyAccountLinked()
        SyncScheduler.schedule(hair)
    }

    fun onAuthCancelled() {
        _state.value = UiState.SignedOut
    }

    /** 退出时必须清掉本机缓存的健康数据并停止后台同步。 */
    fun signOut() {
        SyncScheduler.cancel(hair)
        auth.signOut()
        repository.clearCache()
        _state.value = UiState.SignedOut
    }

    fun refresh() = load {}

    /** 设置、习惯、片段记录都很小，直接在主线程同步落盘，保证连点时写入顺序与界面一致。 */
    fun setSettings(settings: UserSettings) {
        settingsStore.save(settings)
        _settings.value = settings
    }

    fun setHabits(log: HabitLog) {
        habitStore.save(log)
        _habits.value = log
    }

    fun setMoments(log: MomentLog) {
        momentStore.save(log)
        _moments.value = log
    }

    /** 详情页的时间序列不入缓存，每次现取；由调用方的协程负责取消。 */
    suspend fun loadSeries(metric: Metric, range: DetailRange): LoadResult<Series> =
        fetch { repository.series(metric, range) }

    /** 一晚的心率和血氧细节；睡眠没有分期、无法确定起止时结果为 null。 */
    suspend fun loadNightDetail(night: Night): LoadResult<NightDetail?> = fetch { repository.nightDetail(night) }

    /** 一段已结束的记录期间的心率。 */
    suspend fun loadHeart(moment: Moment): LoadResult<List<HeartBucket>> = fetch {
        val end = checkNotNull(moment.endMillis) { "片段还没结束" }
        repository.heartSeries(Instant.ofEpochMilli(moment.startMillis), Instant.ofEpochMilli(end), momentWindow(end - moment.startMillis))
    }

    private suspend fun <T> fetch(block: suspend () -> T): LoadResult<T> = try {
        LoadResult.Loaded(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LoadResult.Failed(describe(e) ?: throw e)
    }

    /** 有缓存时先展示缓存并在其上刷新；刷新失败保留缓存并带上错误，没有缓存才进入整页错误。 */
    private fun load(beforeRefresh: suspend () -> Unit) {
        val cached = repository.cached()
        _state.value = cached?.let { UiState.Ready(it, refreshing = true) } ?: UiState.Loading
        viewModelScope.launch {
            try {
                beforeRefresh()
                _state.value = UiState.Ready(repository.refresh())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(cached, describe(e) ?: throw e)
            }
        }
    }

    private fun fail(cached: Snapshot?, message: String) {
        _state.value = cached?.let { UiState.Ready(it, refreshError = message) } ?: UiState.Error(message)
    }

    /** 可预期的失败（接口、授权、网络）转成用户可读的文案；其他异常是程序错误，返回 null 由调用方继续抛出。 */
    private fun describe(e: Exception): String? = when (e) {
        is HealthApiException -> if (e.isAccountNotLinked) ACCOUNT_NOT_LINKED else e.message.orEmpty()
        is AuthorizationException -> "登录失败或已失效（${e.errorDescription ?: e.error}），请退出后重新登录"
        is IOException -> "网络错误：${e.message}"
        else -> null
    }

    private companion object {
        const val ACCOUNT_NOT_LINKED = "账号尚未关联：请先在 Google Health app 中登录并关联该 Google 账号"
    }
}
