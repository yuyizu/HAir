package dev.hair.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal sealed interface LatitudeResult {
    /** 取整到 1° 的纬度，北正南负。 */
    class Found(val degrees: Int) : LatitudeResult

    class Failed(val reason: String) : LatitudeResult
}

private const val FIX_TIMEOUT_MS = 10_000L

internal fun hasCoarseLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * 读一次粗略位置，只取纬度并取整到 1°：用来判断气候带和南北半球，精度远比这个粗。
 * 先用系统缓存的最近位置，没有再向网络定位要一次（最多等 [FIX_TIMEOUT_MS]）。不上传、不持续定位。
 * 调用前要已有 ACCESS_COARSE_LOCATION 权限。
 */
internal suspend fun readLatitude(context: Context): LatitudeResult {
    val manager = context.getSystemService(LocationManager::class.java) ?: return LatitudeResult.Failed("这台设备没有定位服务")
    if (!LocationManagerCompat.isLocationEnabled(manager)) return LatitudeResult.Failed("系统的定位开关是关的，打开后再试，或手动选气候")
    return try {
        val location = lastKnown(manager) ?: freshFix(context, manager)
        if (location == null) LatitudeResult.Failed("没能读到位置，稍后再试，或手动选气候") else LatitudeResult.Found(location.latitude.roundToInt())
    } catch (e: SecurityException) {
        LatitudeResult.Failed("没有位置权限，可以在系统设置里打开，或手动选气候")
    }
}

private fun lastKnown(manager: LocationManager): Location? =
    listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { manager.getLastKnownLocation(it) }
        .maxByOrNull { it.time }

private suspend fun freshFix(context: Context, manager: LocationManager): Location? {
    if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return null
    return withTimeoutOrNull(FIX_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, LocationManager.NETWORK_PROVIDER, signal, ContextCompat.getMainExecutor(context)) {
                continuation.resume(it)
            }
        }
    }
}
