package com.coolapk.wear.presentation.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val primaryColor = LocalAppPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current

    // 语音输入
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.updateQuery(spoken)
                viewModel.search(spoken)
            }
        }
    }

    // 触底加载下一页
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val currentSize = when (uiState.currentTab) {
                    SearchTab.FEEDS -> uiState.feedResults.size
                    SearchTab.USERS -> uiState.userResults.size
                    SearchTab.TOPICS -> uiState.topicResults.size
                }
                if (lastVisibleIndex != null && lastVisibleIndex >= currentSize - 2) {
                    viewModel.loadMore()
                }
            }
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
            // 搜索输入栏与语音搜索
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(WearSurface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "搜索",
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        BasicTextField(
                            value = uiState.query,
                            onValueChange = { viewModel.updateQuery(it) },
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(primaryColor),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (uiState.query.isEmpty()) {
                                    Text(
                                        text = "搜索动态/作者/话题",
                                        color = Color(0xFF777777),
                                        fontSize = 11.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (uiState.query.isNotEmpty()) {
                            CompactButton(
                                onClick = {
                                    viewModel.updateQuery("")
                                },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "清空",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // 语音输入
                        CompactButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你想搜索的内容...")
                                }
                                try {
                                    speechLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "未找到语音输入服务", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "语音输入",
                                tint = primaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 搜索类别切换 Tab（动态、作者、话题）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SearchTab.values().forEach { tab ->
                        val isSelected = uiState.currentTab == tab
                        CompactChip(
                            onClick = { viewModel.switchTab(tab) },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFFA0A0A5)
                                )
                            },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (isSelected) primaryColor else WearSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 初始未搜索状态：展示真实酷安热门搜索
            if (!uiState.hasSearched) {
                item {
                    Text(
                        text = "🔥 热门搜索",
                        color = primaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp, start = 4.dp)
                    )
                }

                items(uiState.hotKeywords) { keyword ->
                    CompactChip(
                        onClick = {
                            viewModel.updateQuery(keyword)
                            viewModel.search(keyword)
                        },
                        label = { Text("# $keyword", fontSize = 11.sp) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = keyword,
                                tint = primaryColor,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }

            // 加载中
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 错误重试
            if (uiState.error != null && !uiState.isLoading) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            color = Color(0xFFA0A0A5),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.search() },
                            colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "重试",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // 搜索结果列表展示
            if (uiState.hasSearched && !uiState.isLoading && uiState.error == null) {
                when (uiState.currentTab) {
                    SearchTab.FEEDS -> {
                        if (uiState.feedResults.isEmpty()) {
                            item {
                                Text(
                                    text = "未搜索到相关动态",
                                    color = Color(0xFF777777),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }
                        } else {
                            itemsIndexed(uiState.feedResults, key = { index, feed -> "${feed.id}_$index" }) { _, feed ->
                                FeedCard(
                                    feed = feed,
                                    onClick = { onFeedClick(feed.id) },
                                    onUserClick = onUserClick,
                                    onTopicClick = onTopicClick
                                )
                            }
                        }
                    }
                    SearchTab.USERS -> {
                        if (uiState.userResults.isEmpty()) {
                            item {
                                Text(
                                    text = "未搜索到相关用户",
                                    color = Color(0xFF777777),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }
                        } else {
                            itemsIndexed(uiState.userResults, key = { index, user -> "${user.uid}_$index" }) { _, user ->
                                Card(
                                    onClick = { onUserClick(user.uid) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                                        startBackgroundColor = WearSurface,
                                        endBackgroundColor = WearSurface
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        if (user.userAvatar.isNotBlank()) {
                                            AsyncImage(
                                                model = user.userAvatar,
                                                contentDescription = user.username,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(primaryColor)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = user.username,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (user.bio.isNotBlank()) {
                                                Text(
                                                    text = user.bio,
                                                    color = Color(0xFFA0A0A5),
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SearchTab.TOPICS -> {
                        if (uiState.topicResults.isEmpty()) {
                            item {
                                Text(
                                    text = "未搜索到相关话题",
                                    color = Color(0xFF777777),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }
                        } else {
                            itemsIndexed(uiState.topicResults, key = { index, topic -> "${topic.id}_$index" }) { _, topic ->
                                Card(
                                    onClick = { onTopicClick(topic.title) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                                        startBackgroundColor = WearSurface,
                                        endBackgroundColor = WearSurface
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(primaryColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Tag,
                                                contentDescription = topic.title,
                                                tint = primaryColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "# ${topic.title} #",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val infoText = if (topic.description.isNotBlank()) topic.description else if (topic.feedNum > 0) "${topic.feedNum}条动态" else "点击查看话题内容"
                                            Text(
                                                text = infoText,
                                                color = Color(0xFFA0A0A5),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 加载更多进度指示器
            if (uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
