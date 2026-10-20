package dev.hair.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hair.app.HairApp
import dev.hair.app.data.HealthApiException
import dev.hair.app.data.Snapshot
import dev.hair.app.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import java.io.IOException

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

class HairViewModel(app: Application) : AndroidViewModel(app) {
    private val hair = app as HairApp
    private val auth = hair.auth
    private val repository = hair.repository

    private val _state = MutableStateFlow<UiState>(if (auth.isSignedIn) UiState.Loading else UiState.SignedOut)
    val state: StateFlow<UiState> = _state.asStateFlow()

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
            } catch (e: HealthApiException) {
                fail(cached, if (e.isAccountNotLinked) ACCOUNT_NOT_LINKED else e.message.orEmpty())
            } catch (e: AuthorizationException) {
                fail(cached, "登录失败或已失效（${e.errorDescription ?: e.error}），请退出后重新登录")
            } catch (e: IOException) {
                fail(cached, "网络错误：${e.message}")
            }
        }
    }

    private fun fail(cached: Snapshot?, message: String) {
        _state.value = cached?.let { UiState.Ready(it, refreshError = message) } ?: UiState.Error(message)
    }

    private companion object {
        const val ACCOUNT_NOT_LINKED = "账号尚未关联：请先在 Google Health app 中登录并关联该 Google 账号"
    }
}
