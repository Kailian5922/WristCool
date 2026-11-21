package com.coolapk.wear.presentation.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.UserRowCard
import com.coolapk.wear.presentation.components.WearCenteredLoadingOverlay
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearSectionSubHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.detail.QrCodeOverlay

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (String) -> Unit = {},
    onProductClick: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val tabScroll = rememberScrollState()
    val sortScroll = rememberScrollState()
    val context = LocalContext.current

    var qrAppUrl by remember { mutableStateOf<Pair<String, String>?>(null) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        HorologistScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(listState, enabled = qrAppUrl == null)
            ) {
                // 搜索输入栏与语音按钮
                item(key = "search_bar") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .clip(CircleShape)
                            .background(colorScheme.surfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "搜索",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = uiState.query,
                            onValueChange = { viewModel.updateQuery(it) },
                            textStyle = TextStyle(color = colorScheme.onSurface, fontSize = 12.sp),
                            cursorBrush = SolidColor(colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (uiState.query.isEmpty()) {
                                    Text(
                                        text = "搜动态/数码/作者/话题",
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyExtraSmall,
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (uiState.query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "清空",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.updateQuery("") }
                                    .padding(2.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你想搜索的内容…")
                                }
                                try {
                                    speechLauncher.launch(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "未找到语音输入服务", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shapes = IconButtonDefaults.animatedShapes(),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "语音搜索",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // 5 分类 Tab（动态 / 作者 / 话题 / 数码 / 应用）
                item(key = "search_tabs") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(tabScroll)
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SearchTab.entries.forEach { tab ->
                            val isSelected = uiState.currentTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) colorScheme.primary else colorScheme.surfaceContainerHigh
                                    )
                                    .clickable { viewModel.switchTab(tab) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 动态搜索排序条
                if (uiState.hasSearched && uiState.currentTab == SearchTab.FEEDS) {
                    item(key = "search_feed_sort") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(sortScroll),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SearchFeedSort.entries.forEach { sort ->
                                val isSelected = uiState.feedSort == sort
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) colorScheme.secondaryContainer else colorScheme.surfaceContainerLow
                                        )
                                        .clickable { viewModel.setFeedSort(sort) }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = sort.label,
                                        color = if (isSelected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyExtraSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // 未搜索时：实时联想词 + 搜索历史 + 热搜词
                if (!uiState.hasSearched) {
                    if (uiState.suggestions.isNotEmpty()) {
                        item(key = "suggest_header") {
                            WearSectionSubHeader(
                                title = "搜索联想",
                                icon = Icons.Rounded.Search,
                                transformationSpec = transformationSpec
                            )
                        }
                        items(uiState.suggestions, key = { "sug_$it" }) { sug ->
                            FilledTonalButton(
                                onClick = {
                                    viewModel.updateQuery(sug)
                                    viewModel.search(sug)
                                },
                                icon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text(sug, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                transformation = SurfaceTransformation(transformationSpec),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                            )
                        }
                    }

                    if (uiState.searchHistory.isNotEmpty()) {
                        item(key = "history_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "最近搜索",
                                        color = colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "清空历史",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.clearHistory() }
                                )
                            }
                        }
                        items(uiState.searchHistory.take(6), key = { "hist_$it" }) { word ->
                            CompactButton(
                                onClick = {
                                    viewModel.updateQuery(word)
                                    viewModel.search(word)
                                },
                                icon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(13.dp)) },
                                label = { Text(word, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item(key = "hot_header") {
                        WearSectionSubHeader(
                            title = "热门搜索",
                            icon = Icons.Rounded.LocalFireDepartment,
                            transformationSpec = transformationSpec
                        )
                    }

                    items(uiState.hotKeywords, key = { "hot_$it" }) { keyword ->
                        FilledTonalButton(
                            onClick = {
                                viewModel.updateQuery(keyword)
                                viewModel.search(keyword)
                            },
                            icon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("# $keyword", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        )
                    }
                }

                if (uiState.error != null && !uiState.isLoading) {
                    item(key = "search_error") {
                        WearErrorRetry(message = uiState.error.orEmpty(), onRetry = { viewModel.search() })
                    }
                }

                if (uiState.hasSearched && !uiState.isLoading && uiState.error == null) {
                    when (uiState.currentTab) {
                        SearchTab.FEEDS -> {
                            if (uiState.feedResults.isEmpty()) {
                                item(key = "empty_feeds") { WearEmptyState("未搜索到相关动态") }
                            } else {
                                itemsIndexed(uiState.feedResults, key = { idx, f -> "${f.id}_$idx" }) { _, feed ->
                                    FeedCard(
                                        feed = feed,
                                        onClick = { onFeedClick(feed.id) },
                                        onUserClick = onUserClick,
                                        onTopicClick = onTopicClick,
                                        onProductClick = onProductClick,
                                        transformation = SurfaceTransformation(transformationSpec),
                                        modifier = Modifier
                                            .transformedHeight(this, transformationSpec)
                                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                                    )
                                }
                            }
                        }
                        SearchTab.USERS -> {
                            if (uiState.userResults.isEmpty()) {
                                item(key = "empty_users") { WearEmptyState("未搜索到相关用户") }
                            } else {
                                itemsIndexed(uiState.userResults, key = { idx, u -> "${u.uid}_$idx" }) { _, user ->
                                    UserRowCard(
                                        user = user,
                                        onClick = { onUserClick(user.uid) },
                                        transformation = SurfaceTransformation(transformationSpec),
                                        modifier = Modifier
                                            .transformedHeight(this, transformationSpec)
                                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                                    )
                                }
                            }
                        }
                        SearchTab.TOPICS -> {
                            if (uiState.topicResults.isEmpty()) {
                                item(key = "empty_topics") { WearEmptyState("未搜索到相关话题") }
                            } else {
                                itemsIndexed(uiState.topicResults, key = { idx, t -> "${t.id}_$idx" }) { _, topic ->
                                    Card(
                                        onClick = { onTopicClick(topic.title) },
                                        transformation = SurfaceTransformation(transformationSpec),
                                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .transformedHeight(this, transformationSpec)
                                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Tag,
                                                    contentDescription = topic.title,
                                                    tint = colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "# ${topic.title} #",
                                                    color = colorScheme.onSurface,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val infoText = when {
                                                    topic.description.isNotBlank() -> topic.description
                                                    topic.feedNum > 0 -> "${topic.feedNum}条动态 · ${topic.followNum}关注"
                                                    else -> "查看话题讨论"
                                                }
                                                Text(
                                                    text = infoText,
                                                    color = colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyExtraSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SearchTab.PRODUCTS -> {
                            if (uiState.productResults.isEmpty()) {
                                item(key = "empty_products") { WearEmptyState("未搜索到相关数码产品") }
                            } else {
                                itemsIndexed(uiState.productResults, key = { idx, p -> "${p.id}_$idx" }) { _, product ->
                                    Card(
                                        onClick = { onProductClick(product.id) },
                                        transformation = SurfaceTransformation(transformationSpec),
                                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .transformedHeight(this, transformationSpec)
                                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (product.logo.isNotBlank()) {
                                                AsyncImage(
                                                    model = product.logo,
                                                    contentDescription = product.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.Smartphone,
                                                    contentDescription = null,
                                                    tint = colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = product.title,
                                                    color = colorScheme.onSurface,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (product.score > 0f) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Star,
                                                            contentDescription = null,
                                                            tint = colorScheme.tertiary,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = "%.1f · ".format(product.score),
                                                            color = colorScheme.tertiary,
                                                            style = MaterialTheme.typography.bodyExtraSmall
                                                        )
                                                    }
                                                    Text(
                                                        text = "${product.followNum}关注 · ${product.feedNum}讨论",
                                                        color = colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodyExtraSmall,
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
                        SearchTab.APPS -> {
                            if (uiState.appResults.isEmpty()) {
                                item(key = "empty_apps") { WearEmptyState("未搜索到相关应用") }
                            } else {
                                itemsIndexed(uiState.appResults, key = { idx, a -> "${a.id}_$idx" }) { _, app ->
                                    Card(
                                        onClick = {
                                            val pkg = app.packageName.ifBlank { app.id }
                                            qrAppUrl = app.title to "https://www.coolapk.com/apk/$pkg"
                                        },
                                        transformation = SurfaceTransformation(transformationSpec),
                                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .transformedHeight(this, transformationSpec)
                                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (app.logo.isNotBlank()) {
                                                AsyncImage(
                                                    model = app.logo,
                                                    contentDescription = app.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.Apps,
                                                    contentDescription = null,
                                                    tint = colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.title,
                                                    color = colorScheme.onSurface,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val sub = buildString {
                                                    if (app.score > 0f) append("%.1f★ ".format(app.score))
                                                    if (app.versionName.isNotBlank()) append("v${app.versionName} ")
                                                    if (app.description.isNotBlank()) append(app.description)
                                                }.ifBlank { "点击生成手机扫码页" }
                                                Text(
                                                    text = sub,
                                                    color = colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyExtraSmall,
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
                        item(key = "search_footer") {
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

        WearCenteredLoadingOverlay(visible = uiState.isLoading)

        qrAppUrl?.let { (title, url) ->
            QrCodeOverlay(
                feedUrl = url,
                title = title,
                subtitle = "手机扫码查看/下载该应用",
                onDismiss = { qrAppUrl = null }
            )
        }
    }
}
