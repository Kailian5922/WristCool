package com.coolapk.wear.presentation.user

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.UserAvatar
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearPillBadge
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.inbox.ChatViewModel

@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onFollowClick: (String) -> Unit = {},
    onFansClick: (String) -> Unit = {},
    onTopicClick: (String) -> Unit = {},
    onProductClick: (String) -> Unit = {},
    onOpenChat: (ukey: String, partnerUid: String, partnerName: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    LaunchedEffect(uiState.actionMessage) {
        val msg = uiState.actionMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearActionMessage()
        }
    }

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.activeItemCount,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoadingProfile || uiState.isLoadingFeeds || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadFeeds(refresh = false) }
    )

    if (uiState.isLoadingProfile && uiState.userProfile == null) {
        HorologistScaffold(showTimeText = false) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp))
            }
        }
        return
    }

    HorologistScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .rotaryEventHandler(listState)
        ) {
            if (uiState.error != null) {
                item(key = "user_error") {
                    WearErrorRetry(
                        message = uiState.error.orEmpty(),
                        onRetry = {
                            viewModel.loadProfile()
                            viewModel.loadFeeds(refresh = true)
                        }
                    )
                }
            }

            val profile = uiState.userProfile
            if (profile != null) {
                item(key = "user_hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .clip(MaterialTheme.shapes.large)
                            .background(colorScheme.surfaceContainer)
                            .padding(11.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UserAvatar(
                                avatarUrl = profile.userAvatar,
                                contentDescription = profile.username,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.username,
                                        color = colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (profile.level > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        WearPillBadge(text = "Lv.${profile.level}")
                                    }
                                }
                                val bioText = when {
                                    profile.verifyTitle.isNotBlank() -> profile.verifyTitle
                                    profile.bio.isNotBlank() -> profile.bio
                                    else -> "UID: ${profile.uid}"
                                }
                                Text(
                                    text = bioText,
                                    color = if (profile.verifyTitle.isNotBlank()) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyExtraSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 动态 / 关注 / 粉丝 统计栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorScheme.surfaceContainerHigh)
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatCell("${profile.feedNum}", "动态", Modifier.weight(1f)) {}
                            StatCell("${profile.followNum}", "关注", Modifier.weight(1f)) {
                                onFollowClick(profile.uid)
                            }
                            StatCell("${profile.fansNum}", "粉丝", Modifier.weight(1f)) {
                                onFansClick(profile.uid)
                            }
                        }

                        // M3 Expressive 操作栏：关注 / 特别关注 / 发私信 / 屏蔽
                        if (!uiState.isSelf) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val followInter = remember { MutableInteractionSource() }
                            val specialInter = remember { MutableInteractionSource() }
                            val pmInter = remember { MutableInteractionSource() }
                            val blockInter = remember { MutableInteractionSource() }

                            ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                                IconToggleButton(
                                    checked = profile.isFollow,
                                    enabled = !uiState.isTogglingFollow,
                                    onCheckedChange = { viewModel.toggleFollow() },
                                    interactionSource = followInter,
                                    shapes = IconToggleButtonDefaults.variantAnimatedShapes(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(followInter)
                                ) {
                                    Icon(
                                        imageVector = if (profile.isFollow) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                                        contentDescription = if (profile.isFollow) "已关注" else "关注",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (profile.isFollow) {
                                    IconToggleButton(
                                        checked = profile.isSpecialFollow,
                                        onCheckedChange = { viewModel.toggleSpecialFollow() },
                                        interactionSource = specialInter,
                                        shapes = IconToggleButtonDefaults.variantAnimatedShapes(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .animateWidth(specialInter)
                                    ) {
                                        Icon(
                                            imageVector = if (profile.isSpecialFollow) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            contentDescription = "特别关注",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        val myUid = viewModel.currentUid
                                        if (myUid.isBlank()) {
                                            Toast.makeText(context, "请先登录后再发送私信", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val ukey = ChatViewModel.buildUkey(myUid, profile.uid)
                                            onOpenChat(ukey, profile.uid, profile.username)
                                        }
                                    },
                                    interactionSource = pmInter,
                                    shapes = IconButtonDefaults.animatedShapes(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(pmInter)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MailOutline,
                                        contentDescription = "发私信",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconToggleButton(
                                    checked = uiState.isBlocked,
                                    onCheckedChange = { viewModel.toggleBlockUser() },
                                    interactionSource = blockInter,
                                    shapes = IconToggleButtonDefaults.variantAnimatedShapes(),
                                    colors = IconToggleButtonDefaults.colors(
                                        checkedContainerColor = colorScheme.errorContainer,
                                        checkedContentColor = colorScheme.onErrorContainer
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(blockInter)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Block,
                                        contentDescription = if (uiState.isBlocked) "已屏蔽" else "屏蔽",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 主页内容 Tab（动态 / 回复 / 点赞）
                item(key = "user_tabs") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
                    ) {
                        ProfileTab.entries.forEach { tab ->
                            val selected = uiState.currentTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh
                                    )
                                    .clickable { viewModel.switchTab(tab) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                if (uiState.activeItemCount == 0 && !uiState.isLoadingFeeds && !uiState.isLoadingProfile) {
                    item(key = "user_empty") {
                        WearEmptyState(
                            when (uiState.currentTab) {
                                ProfileTab.FEEDS -> "暂无发布的动态"
                                ProfileTab.REPLIES -> "暂无公开回复"
                                ProfileTab.LIKES -> "暂无公开点赞"
                            }
                        )
                    }
                }

                when (uiState.currentTab) {
                    ProfileTab.FEEDS -> {
                        itemsIndexed(uiState.feeds, key = { idx, feed -> "f_${feed.id}_$idx" }) { _, feed ->
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
                    ProfileTab.REPLIES -> {
                        itemsIndexed(uiState.replies, key = { idx, r -> "r_${r.id}_$idx" }) { _, reply ->
                            Card(
                                onClick = { if (reply.feedId.isNotBlank()) onFeedClick(reply.feedId) },
                                transformation = SurfaceTransformation(transformationSpec),
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                            ) {
                                Text(
                                    text = reply.message,
                                    color = colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (reply.feedTitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "原动态：${reply.feedTitle}",
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyExtraSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    ProfileTab.LIKES -> {
                        itemsIndexed(uiState.likedFeeds, key = { idx, feed -> "l_${feed.id}_$idx" }) { _, feed ->
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

                if (uiState.isLoadingMore || (!uiState.hasMore && uiState.activeItemCount > 0)) {
                    item(key = "user_footer") {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = uiState.activeItemCount > 0,
                            endText = "已经到底啦"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(
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
