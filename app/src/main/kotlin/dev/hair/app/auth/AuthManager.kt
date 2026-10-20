package dev.hair.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.hair.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.security.GeneralSecurityException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NotSignedInException : IllegalStateException("尚未登录")

/**
 * Google OAuth（Android 客户端类型 + PKCE，无 client secret）。
 * AuthState 序列化后经 Keystore 加密存入 SharedPreferences，是令牌的唯一来源。
 */
class AuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val service = AuthorizationService(appContext)
    private val prefs = appContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val cipher = TokenCipher()
    private var state: AuthState? = load()

    val isSignedIn: Boolean get() = state?.refreshToken != null

    fun authIntent(): Intent {
        check(BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) {
            "未配置 googleClientId：请在 local.properties 中添加 googleClientId=<Android 客户端 ID>"
        }
        val config = AuthorizationServiceConfiguration(Uri.parse(AUTH_ENDPOINT), Uri.parse(TOKEN_ENDPOINT))
        val request = AuthorizationRequest.Builder(
            config,
            BuildConfig.GOOGLE_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI),
        )
            .setScopes(SCOPES)
            // 确保每次登录都拿到 refresh token（Google 对重复授权默认不再下发）
            .setAdditionalParameters(mapOf("prompt" to "consent"))
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** 处理授权页回传的 Intent，用授权码换取令牌。失败时抛出 AuthorizationException。 */
    suspend fun completeSignIn(data: Intent) {
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (response == null) throw error ?: IllegalStateException("授权未返回结果")

        val newState = AuthState(response, error)
        val tokens = suspendCancellableCoroutine { cont ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                if (tokenResponse != null) cont.resume(tokenResponse)
                else cont.resumeWithException(ex ?: IllegalStateException("令牌交换无返回"))
            }
        }
        newState.update(tokens, null)
        state = newState
        save()
    }

    /** 返回有效 access token，过期时用 refresh token 自动刷新。 */
    suspend fun accessToken(): String {
        val current = state ?: throw NotSignedInException()
        return suspendCancellableCoroutine { cont ->
            current.performActionWithFreshTokens(service) { token, _, ex ->
                when {
                    ex != null -> cont.resumeWithException(ex)
                    token == null -> cont.resumeWithException(IllegalStateException("刷新后仍无 access token"))
                    else -> {
                        save() // 刷新会更新 AuthState
                        cont.resume(token)
                    }
                }
            }
        }
    }

    fun signOut() {
        state = null
        prefs.edit().remove(KEY_STATE).apply()
    }

    private fun save() {
        val json = state?.jsonSerializeString() ?: return
        prefs.edit().putString(KEY_STATE, cipher.encrypt(json)).apply()
    }

    private fun load(): AuthState? {
        val encoded = prefs.getString(KEY_STATE, null) ?: return null
        return try {
            AuthState.jsonDeserialize(cipher.decrypt(encoded))
        } catch (e: GeneralSecurityException) {
            // Keystore 密钥已失效，旧令牌无法恢复，只能要求重新登录
            prefs.edit().remove(KEY_STATE).apply()
            null
        }
    }

    companion object {
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        // scheme 必须与包名一致（见 app/build.gradle.kts 的 appAuthRedirectScheme）
        private const val REDIRECT_URI = "dev.hair.app:/oauth2redirect"
        private const val KEY_STATE = "auth_state"

        private const val SCOPE_PREFIX = "https://www.googleapis.com/auth/googlehealth."
        val SCOPES = listOf(
            "sleep.readonly",
            "health_metrics_and_measurements.readonly",
            "activity_and_fitness.readonly",
        ).map { SCOPE_PREFIX + it }
    }
}
