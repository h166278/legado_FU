package io.legado.app.ui.design.catalog

import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgCard
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.view.NgSettingsItemView
import io.legado.app.ui.design.components.view.NgSurfaceLayout
import io.legado.app.ui.design.components.NgSurfaceVariant

class NgComponentCatalogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NgComponentCatalog()
        }
    }
}

internal data class NgCatalogTheme(
    val id: String,
    val name: String,
    val snapshot: NgThemeSnapshot
)

internal val ngCatalogThemes: List<NgCatalogTheme> = listOf(
    catalogTheme(
        id = "default",
        name = "默认",
        primary = 0xFF795548.toInt(),
        accent = 0xFFE53935.toInt(),
        background = 0xFFF5F5F5.toInt(),
        surface = 0xFFEEEEEE.toInt()
    ),
    catalogTheme(
        id = "warm",
        name = "暖色",
        primary = 0xFFFFF1E8.toInt(),
        accent = 0xFFF78E66.toInt(),
        background = 0xFFFFF9F5.toInt(),
        surface = 0xFFFFF1E8.toInt()
    ),
    catalogTheme(
        id = "bamboo",
        name = "竹影",
        primary = 0xFFEFF7EA.toInt(),
        accent = 0xFF7F9554.toInt(),
        background = 0xFFFAFCF7.toInt(),
        surface = 0xFFEFF7EA.toInt()
    ),
    catalogTheme(
        id = "mist",
        name = "雾霭",
        primary = 0xFFECF1F5.toInt(),
        accent = 0xFF758DB4.toInt(),
        background = 0xFFF7F9FB.toInt(),
        surface = 0xFFECF1F5.toInt()
    ),
    catalogTheme(
        id = "dark",
        name = "暗色",
        primary = 0xFF202020.toInt(),
        accent = 0xFFF78E66.toInt(),
        background = 0xFF121212.toInt(),
        surface = 0xFF202020.toInt(),
        isDark = true
    ),
    catalogTheme(
        id = "eink",
        name = "墨水屏",
        primary = 0xFFFFFFFF.toInt(),
        accent = 0xFF000000.toInt(),
        background = 0xFFFFFFFF.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        isEInk = true
    )
)

private fun catalogTheme(
    id: String,
    name: String,
    primary: Int,
    accent: Int,
    background: Int,
    surface: Int,
    isDark: Boolean = false,
    isEInk: Boolean = false
): NgCatalogTheme {
    return NgCatalogTheme(
        id = id,
        name = name,
        snapshot = NgThemeResolver.resolve(
            NgLegacyThemeInput(
                primaryColor = primary,
                accentColor = accent,
                backgroundColor = background,
                bottomBackground = surface,
                errorColor = 0xFFB3261E.toInt(),
                isDark = isDark,
                isEInk = isEInk
            )
        )
    )
}

@Composable
internal fun NgComponentCatalog(
    initialThemeId: String = "warm",
    showThemePicker: Boolean = true,
    fontScale: Float = 1f
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale)
    ) {
        NgComponentCatalogContent(initialThemeId, showThemePicker)
    }
}

@Composable
private fun NgComponentCatalogContent(
    initialThemeId: String,
    showThemePicker: Boolean
) {
    var selectedId by rememberSaveable(initialThemeId) { mutableStateOf(initialThemeId) }
    val selected = ngCatalogThemes.firstOrNull { it.id == selectedId }
        ?: ngCatalogThemes.first()
    NgAppTheme(snapshot = selected.snapshot) {
        NgCatalogContent(
            selected = selected,
            showThemePicker = showThemePicker,
            onThemeSelected = { selectedId = it }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NgCatalogContent(
    selected: NgCatalogTheme,
    showThemePicker: Boolean,
    onThemeSelected: (String) -> Unit
) {
    var interactionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val showInteraction: (String) -> Unit = { interactionMessage = it }
    Scaffold(
        modifier = Modifier.testTag("ng_catalog"),
        topBar = {
            TopAppBar(title = { Text("NG Component Catalog") })
        },
        snackbarHost = {
            interactionMessage?.let { message ->
                Snackbar(
                    action = {
                        TextButton(onClick = { interactionMessage = null }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(message)
                }
            }
        },
        containerColor = Color(NgTheme.colors.background)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showThemePicker) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ngCatalogThemes, key = { it.id }) { theme ->
                            FilterChip(
                                selected = theme.id == selected.id,
                                onClick = {
                                    onThemeSelected(theme.id)
                                    showInteraction("已切换到${theme.name}主题")
                                },
                                label = { Text(theme.name) }
                            )
                        }
                    }
                }
            }
            item { CatalogSectionTitle("语义色") }
            item {
                ColorTokenGrid { name, color ->
                    showInteraction("$name  ${String.format("#%08X", color)}")
                }
            }
            item { CatalogSectionTitle("Compose 基础状态") }
            item { ComposeStateSamples(onInteraction = showInteraction) }
            item { CatalogSectionTitle("View 桥接") }
            item {
                ViewThemeSample(
                    snapshot = selected.snapshot,
                    onInteraction = showInteraction
                )
            }
            item { CatalogSectionTitle("长文案与大字号检查") }
            item {
                NgCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "这是一个可能换行的设置项标题，用来检查常见手机宽度",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "摘要应该保持清楚、克制，并在字体放大后继续完整显示。",
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSectionTitle(text: String) {
    Text(
        text = text,
        color = Color(NgTheme.colors.primary),
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun ColorTokenGrid(onTokenClick: (String, Int) -> Unit) {
    val colors = NgTheme.colors
    val tokens = listOf(
        "background" to colors.background,
        "surface" to colors.surface,
        "card" to colors.cardContainer,
        "primary" to colors.primary,
        "selected" to colors.selectedContainer,
        "error" to colors.error
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tokens.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (name, color) ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                Color(colors.surfaceContainer),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onTokenClick(name, color) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(color), RoundedCornerShape(8.dp))
                        )
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeStateSamples(onInteraction: (String) -> Unit) {
    var checked by rememberSaveable { mutableStateOf(true) }
    val updateChecked: (Boolean) -> Unit = { newValue ->
        checked = newValue
        onInteraction(if (newValue) "Compose 开关已开启" else "Compose 开关已关闭")
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NgButton(onClick = { onInteraction("已触发主操作") }) { Text("主操作") }
            NgButton(
                onClick = { onInteraction("已触发次操作") },
                variant = NgButtonVariant.OUTLINE
            ) { Text("次操作") }
            NgButton(onClick = {}, enabled = false, variant = NgButtonVariant.OUTLINE) {
                Text("禁用")
            }
        }
        NgButton(
            onClick = { onInteraction("已触发危险操作演示") },
            variant = NgButtonVariant.DANGER
        ) { Text("危险操作") }
        NgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            NgSettingsItem(
                title = "开关设置",
                modifier = Modifier.testTag("ng_catalog_compose_switch"),
                summary = "组件封装标题、摘要与尾部状态",
                trailing = NgSettingsTrailing.SWITCH,
                checked = checked,
                onCheckedChange = updateChecked,
                onClick = { updateChecked(!checked) }
            )
        }
    }
}

@Composable
private fun ViewThemeSample(
    snapshot: NgThemeSnapshot,
    onInteraction: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        factory = { context ->
            NgSurfaceLayout(context).apply {
                surfaceVariant = NgSurfaceVariant.CARD
                applyNgTheme(snapshot)
                addView(
                    NgSettingsItemView(context).apply {
                        setTitle("View 设置项")
                        setSummary("与 Compose 读取同一个主题快照")
                        applyNgTheme(snapshot)
                        setOnClickListener { onInteraction("已触发 View 设置项") }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        },
        update = { surface ->
            surface.applyNgTheme(snapshot)
            (surface.getChildAt(0) as NgSettingsItemView).apply {
                applyNgTheme(snapshot)
                setOnClickListener { onInteraction("已触发 View 设置项") }
            }
        }
    )
}
