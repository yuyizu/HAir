package dev.hair.app

import android.app.Application
import dev.hair.app.auth.AuthManager
import dev.hair.app.data.HabitStore
import dev.hair.app.data.HealthApi
import dev.hair.app.data.HealthRepository
import dev.hair.app.data.MomentStore
import dev.hair.app.data.SettingsStore
import dev.hair.app.data.SnapshotStore
import dev.hair.app.sync.SyncScheduler
import java.io.File

class HairApp : Application() {
    val auth by lazy { AuthManager(this) }
    val repository by lazy {
        HealthRepository(HealthApi(auth::accessToken), SnapshotStore(File(filesDir, "snapshot.json")))
    }
    val settingsStore by lazy { SettingsStore(File(filesDir, "settings.json")) }
    val habitStore by lazy { HabitStore(File(filesDir, "habits.json")) }
    val momentStore by lazy { MomentStore(File(filesDir, "moments.json")) }

    override fun onCreate() {
        super.onCreate()
        if (auth.isSignedIn) SyncScheduler.schedule(this)
    }
}
