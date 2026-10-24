package dev.hair.app.data

import android.util.Log
import dev.hair.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class HealthApiException(val code: Int, val body: String) :
    RuntimeException("Google Health API 返回 HTTP $code: $body") {
    /** 用户尚未在 Google Health app 中关联账号（文档约定的 400 ACCOUNT_NOT_LINKED）。 */
    val isAccountNotLinked: Boolean get() = code == 400 && "ACCOUNT_NOT_LINKED" in body
}

class HealthApi(
    private val accessToken: suspend () -> String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun checkIdentity() {
        execute(Request.Builder().url("$BASE/users/me/identity"))
    }

    /**
     * dataType 为 kebab-case（如 "sleep"），filter 语法见 ListDataPointsRequest.filter。
     * 最多返回 [limit] 条；服务端单页有上限（如 sleep 为 25），不够时按 nextPageToken 继续取。
     */
    suspend fun list(dataType: String, filter: String, limit: Int): List<DataPoint> {
        val points = ArrayList<DataPoint>()
        var pageToken: String? = null
        do {
            val url = "$BASE/users/me/dataTypes/$dataType/dataPoints".toHttpUrl().newBuilder()
                .addQueryParameter("filter", filter)
                .addQueryParameter("pageSize", (limit - points.size).coerceAtMost(MAX_PAGE_SIZE).toString())
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val body = execute(Request.Builder().url(url))
            if (BuildConfig.DEBUG) Log.d(TAG, "$dataType [$filter] -> $body")
            val page = HealthJson.decodeFromString<ListResponse>(body)
            points += page.dataPoints
            pageToken = page.nextPageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null && points.size < limit)
        return points.take(limit)
    }

    /** 按天汇总 [metric]，[from] 含，[toExclusive] 不含。 */
    suspend fun dailyRollUp(metric: Metric, from: LocalDate, toExclusive: LocalDate): List<DailyRollupDataPoint> {
        val payload = DailyRollUpRequest(CivilRange(from.toCivil(), toExclusive.toCivil()))
        val request = Request.Builder()
            .url("$BASE/users/me/dataTypes/${metric.dataType}/dataPoints:dailyRollUp")
            .post(HealthJson.encodeToString(payload).toRequestBody(JSON))
        return HealthJson.decodeFromString<DailyRollUpResponse>(execute(request)).rollupDataPoints
    }

    /** 按 [window] 汇总 [dataType]，[start] 含，[end] 不含。只取第一页（默认 1440 个窗口），调用方自己控制窗口数。 */
    suspend fun rollUp(dataType: String, window: Duration, start: Instant, end: Instant): List<RollupDataPoint> {
        val payload = RollUpRequest("${window.seconds}s", TimeInterval(start.toString(), end.toString()))
        val request = Request.Builder()
            .url("$BASE/users/me/dataTypes/$dataType/dataPoints:rollUp")
            .post(HealthJson.encodeToString(payload).toRequestBody(JSON))
        return HealthJson.decodeFromString<RollUpResponse>(execute(request)).rollupDataPoints
    }

    private suspend fun execute(builder: Request.Builder): String {
        val request = builder.header("Authorization", "Bearer ${accessToken()}").build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw HealthApiException(response.code, text)
                text
            }
        }
    }

    private fun LocalDate.toCivil() = CivilDateTime(Date(year, monthValue, dayOfMonth), TimeOfDay())

    private companion object {
        const val TAG = "HealthApi"
        const val BASE = "https://health.googleapis.com/v4"
        const val MAX_PAGE_SIZE = 10_000
        val JSON = "application/json".toMediaType()
    }
}
