package dev.hair.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    /** dataType 为 kebab-case（如 "sleep"），filter 语法见 ListDataPointsRequest.filter。 */
    suspend fun list(dataType: String, filter: String, pageSize: Int): List<DataPoint> {
        val url = "$BASE/users/me/dataTypes/$dataType/dataPoints".toHttpUrl().newBuilder()
            .addQueryParameter("filter", filter)
            .addQueryParameter("pageSize", pageSize.toString())
            .build()
        val body = execute(Request.Builder().url(url))
        return HealthJson.decodeFromString<ListResponse>(body).dataPoints
    }

    /** 按天汇总步数，[from] 含，[toExclusive] 不含。 */
    suspend fun dailySteps(from: LocalDate, toExclusive: LocalDate): List<DailyRollupDataPoint> {
        val payload = DailyRollUpRequest(CivilRange(from.toCivil(), toExclusive.toCivil()))
        val request = Request.Builder()
            .url("$BASE/users/me/dataTypes/steps/dataPoints:dailyRollUp")
            .post(HealthJson.encodeToString(payload).toRequestBody(JSON))
        return HealthJson.decodeFromString<DailyRollUpResponse>(execute(request)).rollupDataPoints
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
        const val BASE = "https://health.googleapis.com/v4"
        val JSON = "application/json".toMediaType()
    }
}
