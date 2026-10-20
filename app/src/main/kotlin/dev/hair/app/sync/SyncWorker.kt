package dev.hair.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.hair.app.HairApp
import dev.hair.app.data.HealthApiException
import net.openid.appauth.AuthorizationException
import java.io.IOException

/** 定时刷新缓存。临时性错误交给 WorkManager 退避重试；登录失效等需要用户介入的错误不重试。 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HairApp
        if (!app.auth.isSignedIn) return Result.failure()
        return try {
            app.repository.refresh()
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HealthApiException) {
            if (e.code == 429 || e.code >= 500) Result.retry() else Result.failure()
        } catch (e: AuthorizationException) {
            Result.failure()
        }
    }
}
