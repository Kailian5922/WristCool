package com.coolapk.wear.presentation.me

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.coolapk.wear.data.repository.BlacklistRepository
import com.coolapk.wear.data.repository.SettingsRepository
import com.coolapk.wear.data.repository.ThemePreset
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.RotaryDispatcher
import com.coolapk.wear.presentation.components.WearScreenHeader
import com.coolapk.wear.presentation.components.WearSectionSubHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.detail.CommentSort
import com.coolapk.wear.presentation.feeds.FeedTab
import com.coolapk.wear.presentation.theme.ThemeManager

/**
 * Wear OS Material 3 Expressive 设置中心。
 * 借鉴 coolapk-desktop 与 c001apk-Revived 设置体系并针对手表端精选：
 * 动态莫奈取色、4 套主题色、无图省流、表圈震动开关、默认频道/排序、屏蔽管理与缓存清理。
 */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    blacklistRepository: BlacklistRepository,
    onOpenBlacklist: () -> Unit,
    onClearImageCache: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsState()
    val blacklist by blacklistRepository.state.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val currentTab = FeedTab.fromName(settings.defaultFeedTab)
    val currentSort = CommentSort.fromKey(settings.defaultCommentSort)
    val blockedTotal = blacklist.blockedUsers.size + blacklist.blockedKeywords.size

    HorologistScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .rotaryEventHandler(listState)
        ) {
            item(key = "settings_header") {
                WearScreenHeader(title = "手表设置", transformationSpec = transformationSpec)
            }

            // 外观与 Material 3 Expressive 主题
            item(key = "sec_theme") {
                WearSectionSubHeader(
                    title = "外观与主题",
                    icon = Icons.Rounded.Palette,
                    transformationSpec = transformationSpec
                )
            }

            item(key = "set_dynamic_color") {
                SwitchButton(
                    checked = settings.useDynamicColor,
                    onCheckedChange = { enabled ->
                        ThemeManager.setUseMonet(context, enabled)
                        Toast.makeText(
                            context,
                            if (enabled) "已开启表盘莫奈动态取色" else "已切换为自定义预设色",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    label = { Text("表盘动态取色", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text("跟随系统/表盘主色调", maxLines = 1) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }

            items(ThemePreset.entries, key = { "preset_${it.key}" }) { preset ->
                RadioButton(
                    selected = !settings.useDynamicColor && settings.themePreset == preset,
                    onSelect = {
                        if (settings.useDynamicColor) {
                            ThemeManager.setUseMonet(context, false)
                        }
                        ThemeManager.setPreset(preset)
                    },
                    label = { Text(preset.label, maxLines = 1) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(preset.seedArgb))
                        )
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }

            // 省流与浏览体验
            item(key = "sec_browsing") {
                WearSectionSubHeader(
                    title = "省流与交互",
                    icon = Icons.Rounded.DataSaverOn,
                    transformationSpec = transformationSpec
                )
            }

            item(key = "set_no_image") {
                SwitchButton(
                    checked = settings.noImageMode,
                    onCheckedChange = { settingsRepository.setNoImageMode(it) },
                    label = { Text("无图省流模式", maxLines = 1) },
                    secondaryLabel = { Text("列表与评论不载预览图，点击查看原图", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }

            item(key = "set_haptics") {
                SwitchButton(
                    checked = settings.hapticEnabled,
                    onCheckedChange = { enabled ->
                        settingsRepository.setHapticEnabled(enabled)
                        RotaryDispatcher.hapticEnabled = enabled
                    },
                    label = { Text("表圈震动反馈", maxLines = 1) },
                    secondaryLabel = { Text("旋转表冠与下拉刻度震动", maxLines = 1) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Vibration,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }

            item(key = "set_record_history") {
                SwitchButton(
                    checked = settings.recordHistory,
                    onCheckedChange = { settingsRepository.setRecordHistory(it) },
                    label = { Text("记录本地历史", maxLines = 1) },
                    secondaryLabel = { Text("浏览动态时离线保存记录", maxLines = 1) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }

            // 默认行为与内容过滤
            item(key = "sec_defaults") {
                WearSectionSubHeader(
                    title = "默认与屏蔽",
                    icon = Icons.Rounded.Block,
                    transformationSpec = transformationSpec
                )
            }

            item(key = "set_default_tab") {
                FilledTonalButton(
                    onClick = {
                        val all = FeedTab.entries
                        val next = all[(all.indexOf(currentTab) + 1) % all.size]
                        settingsRepository.setDefaultFeedTab(next.name)
                    },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("默认频道", maxLines = 1) },
                    secondaryLabel = { Text("启动进入：${currentTab.title}", maxLines = 1) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }

            item(key = "set_default_sort") {
                FilledTonalButton(
                    onClick = {
                        val all = CommentSort.entries
                        val next = all[(all.indexOf(currentSort) + 1) % all.size]
                        settingsRepository.setDefaultCommentSort(next.key)
                    },
                    icon = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("默认评论排序", maxLines = 1) },
                    secondaryLabel = { Text("当前：${currentSort.label}", maxLines = 1) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }

            item(key = "set_blacklist") {
                FilledTonalButton(
                    onClick = onOpenBlacklist,
                    icon = {
                        Icon(
                            Icons.Rounded.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("屏蔽用户与关键词", maxLines = 1) },
                    secondaryLabel = { Text("已配置 $blockedTotal 条过滤规则", maxLines = 1) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }

            item(key = "set_clear_cache") {
                FilledTonalButton(
                    onClick = onClearImageCache,
                    icon = {
                        Icon(
                            Icons.Rounded.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("清理图片缓存", maxLines = 1) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }
        }
    }
}
