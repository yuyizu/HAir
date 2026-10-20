package dev.hair.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.hair.app.ui.theme.HairTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hair.app.ui.HairScreen
import dev.hair.app.ui.HairViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HairViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HairTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    val data = it.data
                    if (data != null) viewModel.onAuthResult(data) else viewModel.onAuthCancelled()
                }
                HairScreen(
                    state = state,
                    onSignIn = { viewModel.signInIntent()?.let(launcher::launch) },
                    onRefresh = viewModel::refresh,
                    onSignOut = viewModel::signOut,
                    loadSeries = viewModel::loadSeries,
                )
            }
        }
    }
}
