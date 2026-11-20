package com.coolapk.wear.presentation.me

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.rememberRevealState
import com.coolapk.wear.data.repository.BlacklistRepository
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.RotaryScrollLock
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearScreenHeader
import com.coolapk.wear.presentation.components.WearSectionSubHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler

@Composable
fun BlacklistScreen(
    blacklistRepository: BlacklistRepository,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val state by blacklistRepository.state.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    var showAddKeyword by remember { mutableStateOf(false) }
    var keywordDraft by remember { mutableStateOf("") }

    if (showAddKeyword) {
        BackHandler { showAddKeyword = false }
        RotaryScrollLock()
        HorologistScaffold(showTimeText = false) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Text(
                    text = "添加屏蔽词/话题",
                    color = colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large)
                        .background(colorScheme.surfaceContainer)
                        .padding(10.dp)
                ) {
                    if (keywordDraft.isEmpty()) {
                        Text(
                            text = "输入要过滤的关键词…",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    BasicTextField(
                        value = keywordDraft,
                        onValueChange = { keywordDraft = it },
                        textStyle = TextStyle(color = colorScheme.onSurface, fontSize = 12.sp),
                        cursorBrush = SolidColor(colorScheme.primary),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = { showAddKeyword = false },
                        label = { Text("取消") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (keywordDraft.isNotBlank()) {
                                blacklistRepository.addKeyword(keywordDraft)
                                Toast.makeText(context, "已添加屏蔽词", Toast.LENGTH_SHORT).show()
                                keywordDraft = ""
                                showAddKeyword = false
                            }
                        },
                        enabled = keywordDraft.isNotBlank(),
                        label = { Text("添加") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        return
    }

    HorologistScaffold(
        scrollState = listState,
        edgeButton = {
            WearEdgeActionButton(
                scrollState = listState,
                buttonSize = EdgeButtonSize.ExtraSmall,
                onClick = {
                    keywordDraft = ""
                    showAddKeyword = true
                }
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "添加屏蔽词", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("屏蔽词", style = MaterialTheme.typography.labelMedium)
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .rotaryEventHandler(listState)
        ) {
            item(key = "bl_header") {
                WearScreenHeader(
                    title = "本地屏蔽管理",
                    subtitle = "左滑条目可解除屏蔽",
                    transformationSpec = transformationSpec
                )
            }

            if (state.blockedUsers.isEmpty() && state.blockedKeywords.isEmpty()) {
                item(key = "bl_empty") {
                    WearEmptyState(
                        text = "暂无屏蔽规则\n可在用户主页或动态详情屏蔽用户\n或点击底部添加关键词屏蔽",
                        icon = Icons.Rounded.Block
                    )
                }
            }

            if (state.blockedKeywords.isNotEmpty()) {
                item(key = "bl_kw_header") {
                    WearSectionSubHeader(
                        title = "屏蔽关键词 (${state.blockedKeywords.size})",
                        icon = Icons.Rounded.Tag,
                        transformationSpec = transformationSpec
                    )
                }
                items(state.blockedKeywords, key = { "kw_$it" }) { keyword ->
                    val revealState = rememberRevealState()
                    SwipeToReveal(
                        revealState = revealState,
                        onSwipePrimaryAction = { blacklistRepository.removeKeyword(keyword) },
                        primaryAction = {
                            PrimaryActionButton(
                                onClick = { blacklistRepository.removeKeyword(keyword) },
                                icon = { Icon(Icons.Rounded.Delete, contentDescription = "移除") },
                                text = { Text("移除") }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    ) {
                        FilledTonalButton(
                            onClick = { blacklistRepository.removeKeyword(keyword) },
                            icon = { Icon(Icons.Rounded.Tag, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text(keyword, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("点击或左滑移除", maxLines = 1) },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (state.blockedUsers.isNotEmpty()) {
                item(key = "bl_user_header") {
                    WearSectionSubHeader(
                        title = "已屏蔽用户 (${state.blockedUsers.size})",
                        icon = Icons.Rounded.PersonOff,
                        transformationSpec = transformationSpec
                    )
                }
                items(state.blockedUsers, key = { "u_${it.uid}" }) { user ->
                    val revealState = rememberRevealState()
                    SwipeToReveal(
                        revealState = revealState,
                        onSwipePrimaryAction = { blacklistRepository.unblockUser(user.uid) },
                        primaryAction = {
                            PrimaryActionButton(
                                onClick = { blacklistRepository.unblockUser(user.uid) },
                                icon = { Icon(Icons.Rounded.Delete, contentDescription = "解除屏蔽") },
                                text = { Text("解除") }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    ) {
                        FilledTonalButton(
                            onClick = { blacklistRepository.unblockUser(user.uid) },
                            icon = { Icon(Icons.Rounded.PersonOff, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text(user.username, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("UID: ${user.uid}", maxLines = 1) },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
