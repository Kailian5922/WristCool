package com.coolapk.wear.presentation.user

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.coolapk.wear.presentation.components.FeedCard
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.UserAvatar
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppOnPrimaryColor
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant

@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onFollowClick: (String) -> Unit = {},
    onFansClick: (String) -> Unit = {},
    onTopicClick: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val primaryColor = LocalAppPrimaryColor.current
    val onPrimaryColor = LocalAppOnPrimaryColor.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0, initialCenterItemScrollOffset = 0)
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
        itemCount = uiState.feeds.size,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoadingProfile || uiState.isLoadingFeeds || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadFeeds(refresh = false) }
    )

    if (uiState.isLoadingProfile && uiState.userProfile == null) {
        HorologistScaffold(showTimeText = false) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    indicatorColor = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
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
            if (uiState.error != null) {
                item {
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
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(WearSurface)
                            .padding(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (profile.level > 0) {
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
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }

                                    val bioText = when {
                                        profile.verifyTitle.isNotBlank() -> profile.verifyTitle
                                        profile.bio.isNotBlank() -> profile.bio
                                        else -> "UID: ${profile.uid}"
                                    }
                                    Text(
                                        text = bioText,
                                        color = if (profile.verifyTitle.isNotBlank()) Color(0xFFFFB300) else Color(0xFFA0A0A5),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

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
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${profile.feedNum}",
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
                                        .clickable { onFollowClick(profile.uid) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${profile.followNum}",
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
                                        .clickable { onFansClick(profile.uid) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${profile.fansNum}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(text = "粉丝", color = Color(0xFFA0A0A5), fontSize = 9.sp)
                                }
                            }

                            if (!uiState.isSelf) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val buttonContentColor = if (profile.isFollow) Color.White else onPrimaryColor
                                CompactChip(
                                    enabled = !uiState.isTogglingFollow,
                                    onClick = { viewModel.toggleFollow() },
                                    label = {
                                        Text(
                                            text = if (profile.isFollow) "已关注" else "+ 关注",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = buttonContentColor
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (profile.isFollow) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                                            contentDescription = "关注状态",
                                            tint = buttonContentColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    },
                                    colors = ChipDefaults.chipColors(
                                        backgroundColor = if (profile.isFollow) WearSurfaceVariant else primaryColor
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "动态列表 (${uiState.feeds.size})",
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
                    )
                }

                if (uiState.feeds.isEmpty() && !uiState.isLoadingFeeds && !uiState.isLoadingProfile) {
                    item { WearEmptyState("暂无发布的动态") }
                }

                itemsIndexed(uiState.feeds, key = { index, feed -> "${feed.id}_$index" }) { _, feed ->
                    FeedCard(
                        feed = feed,
                        onClick = { onFeedClick(feed.id) },
                        onUserClick = onUserClick,
                        onTopicClick = onTopicClick
                    )
                }

                if (uiState.isLoadingMore || (!uiState.hasMore && uiState.feeds.isNotEmpty())) {
                    item {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = uiState.feeds.isNotEmpty(),
                            endText = "已经到底啦"
                        )
                    }
                }
            }
        }
    }
}
