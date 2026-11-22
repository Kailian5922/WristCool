package com.coolapk.wear.presentation.me

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.coolapk.wear.BuildConfig
import com.coolapk.wear.presentation.components.ExpressiveLoadingIndicator
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.RotaryScrollLock
import com.coolapk.wear.presentation.components.UserAvatar
import com.coolapk.wear.presentation.components.WearConfirmDialog
import com.coolapk.wear.presentation.components.WearPillBadge
import com.coolapk.wear.presentation.components.WearScreenHeader
import com.coolapk.wear.presentation.components.WearSectionSubHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler

@Composable
fun MeScreen(
    viewModel: MeViewModel,
    isActivePage: Boolean = true,
    onFavoritesClick: () -> Unit = {},
    onCollectionsClick: (uid: String) -> Unit = {},
    onMyLikesClick: (uid: String) -> Unit = {},
    onMyRepliesClick: (uid: String) -> Unit = {},
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onUserClick: (String) -> Unit,
    onFollowClick: (String) -> Unit = {},
    onFansClick: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    var isManualInputOpen by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    if (isManualInputOpen) {
        BackHandler { isManualInputOpen = false }
        RotaryScrollLock()
        HorologistScaffold(showTimeText = false) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Text(
                    text = "粘贴或输入凭据",
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
                    if (manualInputText.isEmpty()) {
                        Text(
                            text = "点击粘贴伴侣凭据、Cookie 或数盟设备 ID (DU...)",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyExtraSmall
                        )
                    }
                    BasicTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        textStyle = TextStyle(color = colorScheme.onSurface, fontSize = 11.sp),
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
                        onClick = { isManualInputOpen = false },
                        label = { Text("取消") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            viewModel.importFromClipboard(manualInputText)
                            isManualInputOpen = false
                        },
                        label = { Text("保存") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        return
    }

    val effectiveUid = uiState.userProfile?.uid?.takeIf { it.isNotBlank() } ?: uiState.credentials.uid

    Box(modifier = Modifier.fillMaxSize()) {
        HorologistScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(listState, enabled = isActivePage && !showLogoutDialog)
            ) {
                item(key = "me_header") {
                    WearScreenHeader(title = "个人中心", transformationSpec = transformationSpec)
                }

                // 个人信息卡片
                item(key = "me_profile_card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .clip(MaterialTheme.shapes.large)
                            .background(colorScheme.surfaceContainer)
                            .padding(11.dp)
                    ) {
                        if (uiState.credentials.isLoggedIn) {
                            val profile = uiState.userProfile
                            val displayName = when {
                                !profile?.username.isNullOrBlank() -> profile!!.username
                                uiState.credentials.username.isNotBlank() -> uiState.credentials.username
                                else -> "酷友"
                            }
                            val avatarUrl = when {
                                !profile?.userAvatar.isNullOrBlank() -> profile!!.userAvatar
                                uiState.credentials.avatar.isNotBlank() -> uiState.credentials.avatar
                                else -> ""
                            }
                            val bioText = when {
                                !profile?.bio.isNullOrBlank() -> profile!!.bio
                                effectiveUid.isNotBlank() -> "UID: $effectiveUid"
                                else -> "已登录"
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (effectiveUid.isNotBlank()) onUserClick(effectiveUid)
                                        }
                                ) {
                                    UserAvatar(
                                        avatarUrl = avatarUrl,
                                        contentDescription = displayName,
                                        size = 34.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = displayName,
                                                color = colorScheme.onSurface,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (profile != null && profile.level > 0) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                WearPillBadge(text = "Lv.${profile.level}")
                                            }
                                        }
                                        Text(
                                            text = bioText,
                                            color = colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyExtraSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colorScheme.surfaceContainerHigh)
                                        .padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ProfileStatCell("${profile?.feedNum ?: 0}", "动态", Modifier.weight(1f)) {
                                        if (effectiveUid.isNotBlank()) onUserClick(effectiveUid)
                                    }
                                    ProfileStatCell("${profile?.followNum ?: 0}", "关注", Modifier.weight(1f)) {
                                        if (effectiveUid.isNotBlank()) onFollowClick(effectiveUid)
                                    }
                                    ProfileStatCell("${profile?.fansNum ?: 0}", "粉丝", Modifier.weight(1f)) {
                                        if (effectiveUid.isNotBlank()) onFansClick(effectiveUid)
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "未登录",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "游客模式 (未登录)",
                                        color = colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "支持手机伴侣端一键导入凭据",
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyExtraSmall
                                    )
                                }
                            }
                        }
                    }
                }

                // 我的内容分区
                item(key = "me_sec_content") {
                    WearSectionSubHeader(title = "我的内容", transformationSpec = transformationSpec)
                }

                if (uiState.credentials.isLoggedIn) {
                    item(key = "me_collections") {
                        FilledTonalButton(
                            onClick = { onCollectionsClick(effectiveUid) },
                            icon = { Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(16.dp)) },
                            label = { Text("我的收藏", maxLines = 1) },
                            secondaryLabel = { Text("默认收藏夹与全部分类", maxLines = 1) },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        )
                    }

                    item(key = "me_likes") {
                        FilledTonalButton(
                            onClick = { onMyLikesClick(effectiveUid) },
                            icon = { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = colorScheme.error, modifier = Modifier.size(16.dp)) },
                            label = { Text("我的点赞", maxLines = 1) },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        )
                    }

                    item(key = "me_replies") {
                        FilledTonalButton(
                            onClick = { onMyRepliesClick(effectiveUid) },
                            icon = { Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(16.dp)) },
                            label = { Text("我的回复", maxLines = 1) },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        )
                    }
                }

                item(key = "me_history") {
                    FilledTonalButton(
                        onClick = onHistoryClick,
                        icon = { Icon(Icons.Rounded.History, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(16.dp)) },
                        label = { Text("浏览历史", maxLines = 1) },
                        secondaryLabel = { Text("云端优先 · 本地 ${uiState.historyCount} 条", maxLines = 1) },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    )
                }

                // 账号与设置分区
                item(key = "me_sec_account") {
                    WearSectionSubHeader(title = "账号与手表设置", transformationSpec = transformationSpec)
                }

                item(key = "me_settings") {
                    FilledTonalButton(
                        onClick = onSettingsClick,
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(16.dp)) },
                        label = { Text("手表设置", maxLines = 1) },
                        secondaryLabel = { Text("主题色 / 省流无图 / 屏蔽管理", maxLines = 1) },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    )
                }

                item(key = "me_import_clip") {
                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val text = if (clipboard != null && clipboard.hasPrimaryClip()) {
                                clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            } else {
                                ""
                            }
                            viewModel.importFromClipboard(text)
                        },
                        icon = {
                            if (uiState.isImporting) {
                                ExpressiveLoadingIndicator(size = 16.dp, contained = false)
                            } else {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        },
                        label = {
                            Text(
                                text = if (uiState.isImporting) "导入中…" else if (uiState.credentials.isLoggedIn) "同步伴侣登录" else "导入伴侣登录",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        secondaryLabel = {
                            val hasDeviceId = uiState.credentials.deviceId.isNotBlank()
                            val subtitle = when {
                                uiState.credentials.isLoggedIn && hasDeviceId -> "已登录 · 设备ID已配置"
                                uiState.credentials.isLoggedIn -> "已登录 · 未配置设备ID"
                                hasDeviceId -> "设备ID已配置 · 待导入登录"
                                else -> "读取剪贴板凭据或设备ID"
                            }
                            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    )
                }

                item(key = "me_manual_input") {
                    FilledTonalButton(
                        onClick = {
                            manualInputText = ""
                            isManualInputOpen = true
                        },
                        icon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("手动输入凭据", maxLines = 1) },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    )
                }

                if (uiState.credentials.isLoggedIn) {
                    item(key = "me_logout") {
                        FilledTonalButton(
                            onClick = { showLogoutDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = colorScheme.errorContainer,
                                contentColor = colorScheme.onErrorContainer
                            ),
                            icon = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("退出当前登录", maxLines = 1) },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        )
                    }
                }

                item(key = "me_about") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "腕酷 WristCool v${BuildConfig.VERSION_NAME}",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Material 3 Expressive · Wear OS",
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodyExtraSmall
                        )
                    }
                }
            }
        }

        WearConfirmDialog(
            visible = showLogoutDialog,
            title = "退出登录",
            message = "确认退出当前酷安账号？",
            confirmIcon = Icons.AutoMirrored.Rounded.ExitToApp,
            confirmContentDescription = "确认退出",
            isDestructive = true,
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

@Composable
private fun ProfileStatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = value,
            color = colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyExtraSmall
        )
    }
}
