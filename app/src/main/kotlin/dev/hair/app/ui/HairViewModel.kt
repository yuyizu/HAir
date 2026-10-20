package dev.hair.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hair.app.HairApp
import dev.hair.app.data.HealthApiException
import dev.hair.app.data.Night
import dev.hair.app.data.Overview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException

sealed interface UiState {
    data object Loading : UiState
    data object SignedOut : UiState
    data class Ready(val overview: Overview, val nights: List<Night>) : UiState
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
    }

    fun onAuthCancelled() {
        _state.value = UiState.SignedOut
    }

    fun signOut() {
        auth.signOut()
        _state.value = UiState.SignedOut
    }

    fun refresh() = load {}

    private fun load(beforeLoad: suspend () -> Unit) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                beforeLoad()
                UiState.Ready(repository.overview(), repository.nights(days = 14))
            } catch (e: CancellationException) {
                throw e
            } catch (e: HealthApiException) {
                UiState.Error(
                    if (e.isAccountNotLinked) "账号尚未关联：请先在 Google Health app 中登录并关联该 Google 账号"
                    else e.message.orEmpty(),
                )
            } catch (e: AuthorizationException) {
                UiState.Error("登录失败或已失效（${e.errorDescription ?: e.error}），请退出后重新登录")
            } catch (e: java.io.IOException) {
                UiState.Error("网络错误：${e.message}")
            }
        }
    }
}
