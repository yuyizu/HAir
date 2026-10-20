package dev.hair.app

import android.app.Application
import dev.hair.app.auth.AuthManager
import dev.hair.app.data.HealthApi
import dev.hair.app.data.HealthRepository
import dev.hair.app.data.SnapshotStore
import dev.hair.app.sync.SyncScheduler
import java.io.File

class HairApp : Application() {
    val auth by lazy { AuthManager(this) }
    val repository by lazy {
        HealthRepository(HealthApi(auth::accessToken), SnapshotStore(File(filesDir, "snapshot.json")))
    }

    override fun onCreate() {
        super.onCreate()
        if (auth.isSignedIn) SyncScheduler.schedule(this)
    }
}
