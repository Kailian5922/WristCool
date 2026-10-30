package com.coolapk.wear.presentation.settings

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onUserClick: ((String) -> Unit)? = null
) {
    val primaryColor = LocalAppPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
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
            // 标题
            item {
                Text(
                    text = "设置与账号",
                    color = primaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
            }

            // 当前账号状态卡片
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(WearSurface)
                        .padding(10.dp)
                ) {
                    if (uiState.credentials.isLoggedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.credentials.avatar.isNotBlank()) {
                                AsyncImage(
                                    model = uiState.credentials.avatar,
                                    contentDescription = "头像",
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "已登录",
                                    tint = primaryColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Text(
                                    text = uiState.credentials.username.ifBlank { "已登录酷友" },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (uiState.credentials.uid.isNotBlank()) "UID: ${uiState.credentials.uid}" else "凭据生效中",
                                    color = primaryColor,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "游客模式",
                                tint = Color(0xFFA0A0A5),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "游客模式 (未登录)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "可通过手机伴侣端导入",
                                    color = Color(0xFFA0A0A5),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // 查看我的主页
            if (uiState.credentials.isLoggedIn && uiState.credentials.uid.isNotBlank() && onUserClick != null) {
                item {
                    CompactChip(
                        onClick = { onUserClick(uiState.credentials.uid) },
                        label = { Text("查看我的个人主页", fontSize = 11.sp) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "个人主页",
                                tint = primaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }

            // 导入伴侣端登录凭据按钮
            item {
                Chip(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard != null && clipboard.hasPrimaryClip()) {
                            val clipData = clipboard.primaryClip
                            val text = clipData?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                            viewModel.importFromClipboard(text)
                        } else {
                            viewModel.importFromClipboard("")
                        }
                    },
                    label = {
                        Text(
                            text = if (uiState.isImporting) "导入中…" else "导入手机伴侣登录信息",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    icon = {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(
                                indicatorColor = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "粘贴导入",
                                tint = primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = WearSurfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            // 手动键盘输入/粘贴选项
            item {
                CompactChip(
                    onClick = {
                        manualInputText = ""
                        isManualInputOpen = true
                    },
                    label = { Text("键盘输入/手动粘贴", fontSize = 11.sp) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "手动输入",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
            }

            // 提示消息
            if (uiState.message != null) {
                item {
                    Text(
                        text = uiState.message ?: "",
                        color = if (uiState.isError) Color(0xFFFF5252) else primaryColor,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }

            // 退出登录按钮
            if (uiState.credentials.isLoggedIn) {
                item {
                    Chip(
                        onClick = { viewModel.logout() },
                        label = { Text("退出当前登录", fontSize = 12.sp) },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                contentDescription = "退出",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }

            // 关于卡片
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WearSurface)
                        .padding(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "酷安 Wear v1.0.0",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Horologist 风格极简手表客户端",
                            color = Color(0xFF888888),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
