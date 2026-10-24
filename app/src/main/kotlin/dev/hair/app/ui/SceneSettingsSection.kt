package dev.hair.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hair.app.data.Climate
import dev.hair.app.data.SceneSettings
import dev.hair.app.data.Season
import dev.hair.app.data.WaterType
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SEASON_OPTIONS = listOf("自动", "春", "夏", "秋", "冬")
private val WATER_OPTIONS = listOf("淡水", "海水")
private val CLIMATE_OPTIONS = listOf("自动", "热带", "温带", "寒带")

/** 「我的」里水景的全部设置：季节、水质、气候、位置、鱼种和音效。 */
@Composable
fun SceneSettingsSection(scene: SceneSettings, onChange: (SceneSettings) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var locationNote by remember { mutableStateOf<String?>(null) }
    SectionLabel("水景")
    SegmentRow("季节", SEASON_OPTIONS, scene.season?.let { it.ordinal + 1 } ?: 0, 220.dp) {
        onChange(scene.copy(season = if (it == 0) null else Season.entries[it - 1]))
    }
    SegmentRow("水质", WATER_OPTIONS, scene.water.ordinal, 140.dp) { onChange(scene.copy(water = WaterType.entries[it])) }
    SegmentRow("气候", CLIMATE_OPTIONS, scene.climate?.let { it.ordinal + 1 } ?: 0, 220.dp) {
        onChange(scene.copy(climate = if (it == 0) null else Climate.entries[it - 1]))
    }
    LocationRow(scene, onChange) { locationNote = it }
    SoundRow(scene.sound) { onChange(scene.copy(sound = it)) }
    Footnote(locationNote ?: habitatSummary(scene))

    SectionLabel("鱼种")
    val current = speciesFor(scene.habitat, scene.species).map { it.id }
    speciesIn(scene.habitat).forEach { species ->
        val checked = species.id in current
        RowFrame(
            Modifier.toggleable(value = checked, enabled = !(checked && current.size == 1), role = Role.Checkbox) {
                onChange(scene.copy(species = toggledSpecies(scene.habitat, scene.species, species.id)))
            },
            verticalPadding = 4.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(species.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.secondary))
            }
        }
    }
    Footnote("勾选哪几种就养哪几种，至少留一种。换水质或气候后，鱼种按新环境重新列出。")
}

@Composable
private fun SegmentRow(label: String, options: List<String>, selected: Int, width: Dp, onSelect: (Int) -> Unit) {
    RowFrame(verticalPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Segmented(options = options, selected = selected, onSelect = onSelect, modifier = Modifier.width(width))
        }
    }
}

@Composable
private fun SoundRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    RowFrame(verticalPadding = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("点击音效", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedThumbColor = colors.onSecondary, checkedTrackColor = colors.secondary),
            )
        }
    }
}

/** 位置只用来判断气候带和南北半球：先要权限，再读一次，只存取整后的纬度。结果和失败原因通过 [onNote] 显示在下方说明里。 */
@Composable
private fun LocationRow(scene: SceneSettings, onChange: (SceneSettings) -> Unit, onNote: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(scene)
    fun locate() {
        scope.launch {
            onNote("正在定位…")
            when (val result = readLatitude(context)) {
                is LatitudeResult.Found -> {
                    onChange(latest.copy(latitude = result.degrees))
                    onNote(null)
                }
                is LatitudeResult.Failed -> onNote(result.reason)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else onNote("没有位置权限，可以在系统设置里打开，或手动选气候")
    }
    RowFrame(verticalPadding = 0.dp) {
        // 文字按钮自带 12dp 内边距：整行右移 12dp，最后一个按钮的文字才贴齐页面边距，标签再左移回来
        Row(Modifier.offset(x = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("位置", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).offset(x = (-12).dp))
            Text(latitudeLabel(scene.latitude), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextAction(if (scene.latitude == null) "获取" else "更新") {
                if (hasCoarseLocationPermission(context)) locate() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (scene.latitude != null) {
                TextAction("清除") { onChange(scene.copy(latitude = null)); onNote(null) }
            }
        }
    }
}

private fun latitudeLabel(latitude: Int?): String = when {
    latitude == null -> "未设置"
    latitude >= 0 -> "北纬 ${latitude}°"
    else -> "南纬 ${abs(latitude)}°"
}

private fun waterLabel(water: WaterType) = if (water == WaterType.FRESH) "淡水" else "海水"

private fun climateLabel(climate: Climate) = when (climate) {
    Climate.TROPICAL -> "热带"
    Climate.TEMPERATE -> "温带"
    Climate.COLD -> "寒带"
}

/** 现在养的是哪种环境、依据什么：手动选的气候、位置的纬度，还是没有任何依据时的默认。 */
private fun habitatSummary(scene: SceneSettings): String {
    val habitat = scene.habitat
    val basis = when {
        scene.climate != null -> "手动选的气候"
        scene.latitude != null -> "按${latitudeLabel(scene.latitude)}"
        else -> "没有位置，按温带"
    }
    return "现在是${waterLabel(habitat.water)}·${climateLabel(habitat.climate)}（$basis）。" +
        "位置只在点「获取」时读一次，只存取整后的纬度，不上传。点击音效走媒体音量，静音模式下也会响。"
}
