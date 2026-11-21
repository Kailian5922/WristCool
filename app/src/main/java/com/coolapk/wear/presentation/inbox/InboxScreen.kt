package com.coolapk.wear.presentation.inbox

import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.rememberRevealState
import com.coolapk.wear.data.model.MessageSession
import com.coolapk.wear.data.model.NotificationItem
import com.coolapk.wear.presentation.components.CaptchaVerifyOverlay
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.UserAvatar
import com.coolapk.wear.presentation.components.WearCenteredLoadingOverlay
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearPillBadge
import com.coolapk.wear.presentation.components.WearScreenHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler

@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    isActivePage: Boolean = true,
    onFeedClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onOpenChat: (ukey: String, partnerUid: String, partnerName: String) -> Unit,
    onGoLogin: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val tabScroll = rememberScrollState()
    val context = LocalContext.current

    LaunchedEffect(uiState.actionMessage) {
        val msg = uiState.actionMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearActionMessage()
        }
    }

    val itemCount = if (uiState.currentTab == InboxTab.MESSAGES) uiState.sessions.size else uiState.notifications.size

    LoadMoreEffect(
        listState = listState,
        itemCount = itemCount,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoading || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HorologistScaffold(
            scrollState = listState,
            edgeButton = {
                if (uiState.isLoggedIn) {
                    val showClear = uiState.currentTab != InboxTab.MESSAGES
                    WearEdgeActionButton(
                        scrollState = listState,
                        buttonSize = EdgeButtonSize.ExtraSmall,
                        onClick = {
                            if (showClear) viewModel.clearCurrentTabUnread() else viewModel.refresh()
                        }
                    ) {
                        Icon(
                            imageVector = if (showClear) Icons.Rounded.DoneAll else Icons.Rounded.Refresh,
                            contentDescription = if (showClear) "清除未读" else "刷新",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showClear) "标已读" else "刷新",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(listState, enabled = isActivePage && uiState.pendingCaptcha == null)
            ) {
                item(key = "inbox_header") {
                    val subtitle = if (uiState.counts.totalUnread > 0) "未读 ${uiState.counts.totalUnread} 条" else null
                    WearScreenHeader(
                        title = "消息中心",
                        subtitle = subtitle,
                        transformationSpec = transformationSpec
                    )
                }

                if (!uiState.isLoggedIn) {
                    item(key = "inbox_need_login") {
                        WearEmptyState(
                            text = "登录后即可在手表端\n查看通知提醒与回复私信",
                            icon = Icons.Rounded.MailOutline,
                            actionLabel = "前往导入登录",
                            onAction = onGoLogin
                        )
                    }
                    return@TransformingLazyColumn
                }

                // 6 分类切换条（私信 / 评论 / @我 / 评论@ / 赞我 / 新粉丝）
                item(key = "inbox_tabs") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(tabScroll)
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InboxTab.entries.forEach { tab ->
                            val isSelected = uiState.currentTab == tab
                            val count = when (tab) {
                                InboxTab.MESSAGES -> uiState.counts.message
                                InboxTab.COMMENT -> uiState.counts.comment
                                InboxTab.AT_ME -> uiState.counts.atMe
                                InboxTab.AT_COMMENT -> uiState.counts.atCommentMe
                                InboxTab.LIKES -> uiState.counts.feedLike
                                InboxTab.FOLLOW -> uiState.counts.follow
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) colorScheme.primary else colorScheme.surfaceContainerHigh
                                    )
                                    .clickable { viewModel.switchTab(tab) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (count > 0) "${tab.label} $count" else tab.label,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (uiState.error != null && itemCount == 0) {
                    item(key = "inbox_error") {
                        WearErrorRetry(
                            message = uiState.error.orEmpty(),
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }

                if (itemCount == 0 && !uiState.isLoading && uiState.error == null) {
                    item(key = "inbox_empty") {
                        WearEmptyState(
                            text = if (uiState.currentTab == InboxTab.MESSAGES) "暂无私信会话" else "暂无新通知",
                            icon = if (uiState.currentTab == InboxTab.MESSAGES) Icons.Rounded.MailOutline else Icons.Rounded.NotificationsNone
                        )
                    }
                }

                if (uiState.currentTab == InboxTab.MESSAGES) {
                    itemsIndexed(uiState.sessions, key = { idx, s -> "sess_${s.ukey}_$idx" }) { _, session ->
                        val revealState = rememberRevealState()
                        SwipeToReveal(
                            revealState = revealState,
                            onSwipePrimaryAction = { viewModel.deleteSession(session) },
                            primaryAction = {
                                PrimaryActionButton(
                                    onClick = { viewModel.deleteSession(session) },
                                    icon = { Icon(Icons.Rounded.Delete, contentDescription = "删除会话") },
                                    text = { Text("删除") }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                        ) {
                            SessionCard(
                                session = session,
                                transformation = SurfaceTransformation(transformationSpec),
                                onClick = {
                                    onOpenChat(session.ukey, session.partnerUid, session.partnerUsername)
                                }
                            )
                        }
                    }
                } else {
                    itemsIndexed(uiState.notifications, key = { idx, n -> "notif_${n.id}_$idx" }) { _, item ->
                        val revealState = rememberRevealState()
                        SwipeToReveal(
                            revealState = revealState,
                            onSwipePrimaryAction = { viewModel.deleteNotification(item) },
                            primaryAction = {
                                PrimaryActionButton(
                                    onClick = { viewModel.deleteNotification(item) },
                                    icon = { Icon(Icons.Rounded.Delete, contentDescription = "删除通知") },
                                    text = { Text("删除") }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                        ) {
                            NotificationCard(
                                item = item,
                                transformation = SurfaceTransformation(transformationSpec),
                                onClick = {
                                    when {
                                        item.feedId.isNotBlank() -> onFeedClick(item.feedId)
                                        item.replyId.isNotBlank() -> viewModel.resolveNotificationFeed(item) { resolvedId ->
                                            if (!resolvedId.isNullOrBlank()) {
                                                onFeedClick(resolvedId)
                                            } else if (item.fromUid.isNotBlank()) {
                                                onUserClick(item.fromUid)
                                            }
                                        }
                                        item.fromUid.isNotBlank() -> onUserClick(item.fromUid)
                                    }
                                }
                            )
                        }
                    }
                }

                if (uiState.isLoadingMore || (!uiState.hasMore && itemCount > 0)) {
                    item(key = "inbox_footer") {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = itemCount > 0
                        )
                    }
                }
            }
        }

        WearCenteredLoadingOverlay(visible = uiState.isLoggedIn && uiState.isLoading && itemCount == 0)

        uiState.pendingCaptcha?.let { challenge ->
            CaptchaVerifyOverlay(
                challenge = challenge,
                onVerified = { token, field -> viewModel.submitCaptchaVerification(token, field) },
                onDismiss = { viewModel.dismissCaptcha() }
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: MessageSession,
    transformation: SurfaceTransformation,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        transformation = transformation,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (session.unreadNum > 0) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            UserAvatar(
                avatarUrl = session.partnerAvatar,
                contentDescription = session.partnerUsername,
                size = 28.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = session.partnerUsername,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (session.dateString.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = session.dateString,
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyExtraSmall,
                            maxLines = 1
                        )
                    }
                }
                if (session.lastMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = session.lastMessage,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (session.unreadNum > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                WearPillBadge(
                    text = if (session.unreadNum > 99) "99+" else "${session.unreadNum}",
                    containerColor = colorScheme.errorContainer,
                    contentColor = colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    transformation: SurfaceTransformation,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        transformation = transformation,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isNew) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            UserAvatar(
                avatarUrl = item.fromAvatar,
                contentDescription = item.fromUsername,
                size = 22.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item.fromUsername,
                color = colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (item.dateString.isNotBlank()) {
                Text(
                    text = item.dateString,
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyExtraSmall
                )
            }
        }
        if (item.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.note,
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.message.isNotBlank() && item.message != item.note) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.message,
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyExtraSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.targetTitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "原动态：${item.targetTitle}",
                color = colorScheme.tertiary,
                style = MaterialTheme.typography.bodyExtraSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
