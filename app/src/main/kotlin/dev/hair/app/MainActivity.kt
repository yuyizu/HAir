package dev.hair.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.hair.app.ui.theme.HairTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hair.app.ui.HairScreen
import dev.hair.app.ui.HairActions
import dev.hair.app.ui.HairViewModel
import dev.hair.app.ui.UserData

class MainActivity : ComponentActivity() {
    private val viewModel: HairViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HairTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val habits by viewModel.habits.collectAsStateWithLifecycle()
                val moments by viewModel.moments.collectAsStateWithLifecycle()
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    val data = it.data
                    if (data != null) viewModel.onAuthResult(data) else viewModel.onAuthCancelled()
                }
                HairScreen(
                    state = state,
                    data = UserData(settings, habits, moments),
                    actions = HairActions(
                        signIn = { viewModel.signInIntent()?.let(launcher::launch) },
                        refresh = viewModel::refresh,
                        signOut = viewModel::signOut,
                        setSettings = viewModel::setSettings,
                        setHabits = viewModel::setHabits,
                        setMoments = viewModel::setMoments,
                        loadSeries = viewModel::loadSeries,
                        loadNightDetail = viewModel::loadNightDetail,
                        loadHeart = viewModel::loadHeart,
                    ),
                )
            }
        }
    }
}
