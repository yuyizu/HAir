package dev.hair.app

import android.app.Application
import dev.hair.app.auth.AuthManager
import dev.hair.app.data.HealthApi
import dev.hair.app.data.HealthRepository

class HairApp : Application() {
    val auth by lazy { AuthManager(this) }
    val repository by lazy { HealthRepository(HealthApi(auth::accessToken)) }
}
