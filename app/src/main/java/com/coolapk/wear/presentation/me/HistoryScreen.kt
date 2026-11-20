package com.coolapk.wear.presentation.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.coolapk.wear.data.repository.BrowsingHistoryRepository
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.RotaryScrollLock
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurfaceVariant

@Composable
fun HistoryScreen(
    browsingHistoryRepository: BrowsingHistoryRepository,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val primaryColor = LocalAppPrimaryColor.current
    val historyList by browsingHistoryRepository.history.collectAsState()
    val listState = rememberScalingLazyListState()
    var showClearDialog by remember { mutableStateOf(false) }

    HorologistScaffold(listState = listState) {
        ScalingLazyColumn(
            state = listState,
            contentPadding = WearListContentPadding,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            modifier = Modifier
                .fillMaxSize()
                .rotaryEventHandler(listState)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "历史",
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "浏览历史 (${historyList.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (historyList.isNotEmpty()) {
                        CompactButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "清空",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (historyList.isEmpty()) {
                item { WearEmptyState("暂无浏览记录\n在首页浏览动态后将自动记录在此") }
            }

            itemsIndexed(historyList, key = { index, feed -> "${feed.id}_$index" }) { _, feed ->
                FeedCard(
                    feed = feed,
                    onClick = { onFeedClick(feed.id) },
                    onUserClick = onUserClick
                )
            }
        }

        if (showClearDialog) {
            RotaryScrollLock()
            Dialog(
                showDialog = true,
                onDismissRequest = { showClearDialog = false }
            ) {
                Alert(
                    title = {
                        Text(
                            text = "清空浏览历史",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    },
                    content = {
                        Text(
                            text = "确认清空全部 ${historyList.size} 条本地记录？",
                            color = Color(0xFFA0A0A5),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    },
                    positiveButton = {
                        Button(
                            onClick = {
                                showClearDialog = false
                                browsingHistoryRepository.clearHistory()
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFC62828))
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "确认清空", tint = Color.White)
                        }
                    },
                    negativeButton = {
                        Button(
                            onClick = { showClearDialog = false },
                            colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "取消", tint = Color.White)
                        }
                    }
                )
            }
        }
    }
}
