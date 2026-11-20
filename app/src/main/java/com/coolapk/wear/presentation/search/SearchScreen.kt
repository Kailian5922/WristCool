package com.coolapk.wear.presentation.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.UserRowCard
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearLoadingPlaceholder
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppOnPrimaryColor
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
    val onPrimaryColor = LocalAppOnPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current

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

    LoadMoreEffect(
        listState = listState,
        itemCount = if (uiState.hasSearched) uiState.activeResultCount else 0,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoading || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() }
    )

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
                                onClick = { viewModel.updateQuery("") },
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
                                } catch (_: Exception) {
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

            // 搜索类别 Tab
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SearchTab.entries.forEach { tab ->
                        val isSelected = uiState.currentTab == tab
                        CompactChip(
                            onClick = { viewModel.switchTab(tab) },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) onPrimaryColor else Color(0xFFA0A0A5)
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

            // 未搜索时展示热门搜索词
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

            if (uiState.isLoading) {
                item { WearLoadingPlaceholder(height = 80.dp) }
            }

            if (uiState.error != null && !uiState.isLoading) {
                item {
                    WearErrorRetry(
                        message = uiState.error.orEmpty(),
                        onRetry = { viewModel.search() },
                        buttonSize = 32.dp
                    )
                }
            }

            if (uiState.hasSearched && !uiState.isLoading && uiState.error == null) {
                when (uiState.currentTab) {
                    SearchTab.FEEDS -> {
                        if (uiState.feedResults.isEmpty()) {
                            item { WearEmptyState("未搜索到相关动态") }
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
                            item { WearEmptyState("未搜索到相关用户") }
                        } else {
                            itemsIndexed(uiState.userResults, key = { index, user -> "${user.uid}_$index" }) { _, user ->
                                UserRowCard(
                                    user = user,
                                    onClick = { onUserClick(user.uid) }
                                )
                            }
                        }
                    }
                    SearchTab.TOPICS -> {
                        if (uiState.topicResults.isEmpty()) {
                            item { WearEmptyState("未搜索到相关话题") }
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
                                            val infoText = when {
                                                topic.description.isNotBlank() -> topic.description
                                                topic.feedNum > 0 -> "${topic.feedNum}条动态"
                                                else -> "点击查看话题内容"
                                            }
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

                if (uiState.isLoadingMore || (!uiState.hasMore && uiState.activeResultCount > 0)) {
                    item {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = uiState.activeResultCount > 0
                        )
                    }
                }
            }
        }
    }
}
