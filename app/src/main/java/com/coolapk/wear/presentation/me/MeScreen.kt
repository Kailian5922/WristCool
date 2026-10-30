package com.coolapk.wear.presentation.me

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.ui.graphics.vector.ImageVector
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant
import com.coolapk.wear.presentation.theme.ThemeManager

@Composable
fun MeScreen(
    viewModel: MeViewModel,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onUserClick: (String) -> Unit,
    onFollowClick: (String) -> Unit = {},
    onFansClick: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val primaryColor = LocalAppPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0, initialCenterItemScrollOffset = 0)
    val context = LocalContext.current
    var isManualInputOpen by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }

    if (isManualInputOpen) {
        HorologistScaffold(showTimeText = false) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "粘贴或输入凭据",
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(WearSurface)
                        .padding(8.dp)
                ) {
                    if (manualInputText.isEmpty()) {
                        Text(
                            text = "点击此处唤起输入法粘贴",
                            color = Color(0xFF666666),
                            fontSize = 11.sp
                        )
                    }
                    BasicTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                        cursorBrush = SolidColor(primaryColor),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactChip(
                        onClick = { isManualInputOpen = false },
                        label = { Text("取消", fontSize = 11.sp) },
                        colors = ChipDefaults.chipColors(backgroundColor = WearSurfaceVariant),
                        modifier = Modifier.weight(1f)
                    )
                    CompactChip(
                        onClick = {
                            viewModel.importFromClipboard(manualInputText)
                            isManualInputOpen = false
                        },
                        label = { Text("保存", fontSize = 11.sp) },
                        colors = ChipDefaults.chipColors(backgroundColor = primaryColor),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        return
    }

    HorologistScaffold(listState = listState) {
        ScalingLazyColumn(
            state = listState,
            contentPadding = WearListContentPadding,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            modifier = Modifier
                .fillMaxSize()
                .rotaryEventHandler(listState)
        ) {
            // 顶部个人信息卡片（对齐酷安手机端）
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(WearSurface)
                        .padding(10.dp)
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
                            uiState.credentials.uid.isNotBlank() -> "UID: ${uiState.credentials.uid}"
                            else -> "已登录"
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (uiState.credentials.uid.isNotBlank()) {
                                            onUserClick(uiState.credentials.uid)
                                        }
                                    }
                            ) {
                                if (avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "头像",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(primaryColor)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = displayName,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (profile != null && profile.level > 0) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(primaryColor.copy(alpha = 0.25f))
                                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "Lv.${profile.level}",
                                                    color = primaryColor,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = bioText,
                                        color = Color(0xFFA0A0A5),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // 统计数据：动态、关注、粉丝（三列等宽精致排版，均支持直接点击跳转）
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WearSurfaceVariant.copy(alpha = 0.5f))
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (uiState.credentials.uid.isNotBlank()) {
                                                onUserClick(uiState.credentials.uid)
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${profile?.feedNum ?: 0}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(text = "动态", color = Color(0xFFA0A0A5), fontSize = 9.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(18.dp)
                                        .background(Color(0xFF3A3A3C))
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (uiState.credentials.uid.isNotBlank()) {
                                                onFollowClick(uiState.credentials.uid)
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${profile?.followNum ?: 0}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(text = "关注", color = Color(0xFFA0A0A5), fontSize = 9.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(18.dp)
                                        .background(Color(0xFF3A3A3C))
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (uiState.credentials.uid.isNotBlank()) {
                                                onFansClick(uiState.credentials.uid)
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${profile?.fansNum ?: 0}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(text = "粉丝", color = Color(0xFFA0A0A5), fontSize = 9.sp)
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "未登录",
                                tint = Color(0xFFA0A0A5),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "游客模式 (未登录)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "可通过手机伴侣端导入登录",
                                    color = Color(0xFFA0A0A5),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // 功能选项区（统一圆角容器，极致紧凑，消除空隙）
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(WearSurface)
                ) {
                    // 我的收藏
                    if (uiState.credentials.isLoggedIn) {
                        MeListRow(
                            icon = Icons.Rounded.Bookmark,
                            iconTint = Color(0xFFFFB300),
                            title = "我的收藏",
                            onClick = onFavoritesClick
                        )
                        MeDivider()
                    }

                    // 浏览历史
                    MeListRow(
                        icon = Icons.Rounded.History,
                        iconTint = Color(0xFF64B5F6),
                        title = "浏览历史",
                        trailingText = "${uiState.historyCount} 条",
                        onClick = onHistoryClick
                    )
                    MeDivider()

                    // 凭据导入 / 同步
                    MeListRow(
                        icon = Icons.Rounded.ContentPaste,
                        iconTint = primaryColor,
                        title = if (uiState.isImporting) "导入中…" else if (uiState.credentials.isLoggedIn) "同步/刷新伴侣登录" else "导入伴侣登录信息",
                        trailingText = if (uiState.credentials.isLoggedIn) "已登录" else "未导入",
                        trailingBadge = if (uiState.isImporting) {
                            {
                                CircularProgressIndicator(
                                    indicatorColor = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            if (clipboard != null && clipboard.hasPrimaryClip()) {
                                val clipData = clipboard.primaryClip
                                val text = clipData?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                                viewModel.importFromClipboard(text)
                            } else {
                                viewModel.importFromClipboard("")
                            }
                        }
                    )
                    MeDivider()

                    // 键盘手动输入
                    MeListRow(
                        icon = Icons.Rounded.Edit,
                        iconTint = Color.White,
                        title = "键盘手动输入凭据",
                        onClick = {
                            manualInputText = ""
                            isManualInputOpen = true
                        }
                    )
                    MeDivider()

                    // 主题配色切换（酷安绿 / 动态莫奈取色）
                    val useMonet by ThemeManager.useMonet.collectAsState()
                    MeListRow(
                        icon = Icons.Rounded.Palette,
                        iconTint = primaryColor,
                        title = "动态莫奈取色",
                        trailingBadge = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (useMonet) primaryColor else WearSurfaceVariant)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (useMonet) "开启" else "默认绿",
                                    color = if (useMonet) Color.Black else Color(0xFFA0A0A5),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        onClick = {
                            ThemeManager.toggleMonet(context)
                            Toast.makeText(context, if (!useMonet) "已开启系统莫奈取色" else "已切换为默认酷安绿", Toast.LENGTH_SHORT).show()
                        }
                    )
                    MeDivider()

                    // 清理缓存
                    MeListRow(
                        icon = Icons.Rounded.CleaningServices,
                        iconTint = Color(0xFF81C784),
                        title = "清理图片缓存",
                        onClick = { viewModel.clearCache(context) }
                    )

                    // 退出登录
                    if (uiState.credentials.isLoggedIn) {
                        MeDivider()
                        MeListRow(
                            icon = Icons.AutoMirrored.Rounded.ExitToApp,
                            iconTint = Color(0xFFFF5252),
                            title = "退出当前登录",
                            onClick = { viewModel.logout() }
                        )
                    }
                }
            }

            // 关于卡片
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(WearSurface)
                        .padding(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "酷安 Wear v1.0.0",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Wear OS 原生极简社区体验",
                            color = Color(0xFF888888),
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeListRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    trailingBadge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color(0xFFA0A0A5),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                color = Color(0xFFA0A0A5),
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        if (trailingBadge != null) {
            trailingBadge()
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF6E6E73),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun MeDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(0.5.dp)
            .background(Color(0xFF2C2C2E))
    )
}
