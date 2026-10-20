package dev.hair.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hair.app.data.CardioLevel

private val TABS = listOf("今日", "睡眠", "我的")

@Composable
fun HairScreen(state: UiState, onSignIn: () -> Unit, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            UiState.Loading -> Centered { CircularProgressIndicator() }
            UiState.SignedOut -> SignedOutScreen(onSignIn)
            is UiState.Error -> Centered {
                Text(state.message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRefresh) { Text("重试") }
                    OutlinedButton(onClick = onSignOut) { Text("退出登录") }
                }
            }
            is UiState.Ready -> ReadyScreen(state, onRefresh, onSignOut)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun SignedOutScreen(onSignIn: () -> Unit) = Centered {
    Text("HAir", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(12.dp))
    // Google 要求在使用过程中明确说明访问哪些健康数据及用途，隐私政策不能替代
    Text(
        "HAir 会读取你 Fitbit Air 的睡眠、心率、心率变异性和步数数据，" +
            "仅用于在本机展示睡眠与恢复情况。数据不会上传到任何服务器。",
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onSignIn) { Text("使用 Google 账号连接") }
}

@Composable
private fun ReadyScreen(state: UiState.Ready, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).statusBarsPadding()) {
            when (tab) {
                0 -> TodayTab(state, onRefresh)
                1 -> SleepTab(state.snapshot.nights)
                else -> ProfileTab(state, onRefresh, onSignOut)
            }
        }
    }
}

fun cardioLevelLabel(level: CardioLevel): String = when (level) {
    CardioLevel.POOR -> "较差"
    CardioLevel.FAIR -> "一般"
    CardioLevel.AVERAGE -> "平均"
    CardioLevel.GOOD -> "良好"
    CardioLevel.VERY_GOOD -> "很好"
    CardioLevel.EXCELLENT -> "优秀"
}
