package com.coolapk.wear.presentation.inbox

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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.MailOutline
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.WearCenteredLoadingOverlay
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearScreenHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.detail.ComposerMode
import com.coolapk.wear.presentation.detail.FullscreenImageViewer
import com.coolapk.wear.presentation.detail.ReplyOverlay

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onUserClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    var showSendOverlay by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }
    var previewImage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.actionMessage) {
        val msg = uiState.actionMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearActionMessage()
        }
    }

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.messages.size,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoading || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HorologistScaffold(
            scrollState = listState,
            edgeButton = {
                WearEdgeActionButton(
                    scrollState = listState,
                    buttonSize = EdgeButtonSize.ExtraSmall,
                    onClick = {
                        draftText = ""
                        showSendOverlay = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "发私信",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("发私信", style = MaterialTheme.typography.labelMedium)
                }
            }
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(listState, enabled = !showSendOverlay && previewImage == null)
            ) {
                item(key = "chat_header") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (uiState.partnerUid.isNotBlank()) {
                                    Modifier.clickable { onUserClick(uiState.partnerUid) }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        WearScreenHeader(
                            title = uiState.partnerName.ifBlank { "私信对话" },
                            subtitle = "最新消息在前",
                            transformationSpec = transformationSpec
                        )
                    }
                }

                if (uiState.error != null && uiState.messages.isEmpty()) {
                    item(key = "chat_error") {
                        WearErrorRetry(
                            message = uiState.error.orEmpty(),
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }

                if (uiState.messages.isEmpty() && !uiState.isLoading && uiState.error == null) {
                    item(key = "chat_empty") {
                        WearEmptyState("暂无消息，点击底部“发私信”开始聊天", icon = Icons.Rounded.MailOutline)
                    }
                }

                itemsIndexed(uiState.messages, key = { idx, m -> "${m.id}_$idx" }) { _, msg ->
                    if (msg.isSystemNotice) {
                        Text(
                            text = msg.message,
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyExtraSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        val bubbleContainer = if (msg.isSelf) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
                        val bubbleText = if (msg.isSelf) colorScheme.onPrimaryContainer else colorScheme.onSurface
                        val bubbleShape = if (msg.isSelf) {
                            RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp)
                        } else {
                            RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(vertical = 2.dp),
                            horizontalArrangement = if (msg.isSelf) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                horizontalAlignment = if (msg.isSelf) Alignment.End else Alignment.Start,
                                modifier = Modifier
                                    .fillMaxWidth(0.86f)
                                    .clip(bubbleShape)
                                    .background(bubbleContainer)
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                if (msg.message.isNotBlank()) {
                                    Text(
                                        text = msg.message,
                                        color = bubbleText,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (msg.messagePic.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AsyncImage(
                                        model = msg.messagePic,
                                        contentDescription = "私信图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(88.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { previewImage = msg.messagePic }
                                    )
                                }
                                if (msg.dateString.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = msg.dateString,
                                        color = bubbleText.copy(alpha = 0.65f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoadingMore || (!uiState.hasMore && uiState.messages.isNotEmpty())) {
                    item(key = "chat_footer") {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = uiState.messages.isNotEmpty(),
                            endText = "已加载全部消息"
                        )
                    }
                }
            }
        }

        WearCenteredLoadingOverlay(visible = uiState.isLoading && uiState.messages.isEmpty())

        previewImage?.let { url ->
            FullscreenImageViewer(
                imageUrls = listOf(url),
                onDismiss = { previewImage = null },
                onRequestSave = { Toast.makeText(context, "私信图片预览", Toast.LENGTH_SHORT).show() }
            )
        }

        if (showSendOverlay) {
            ReplyOverlay(
                mode = ComposerMode.PRIVATE_MESSAGE,
                targetLabel = uiState.partnerName,
                replyText = draftText,
                onReplyTextChange = { draftText = it },
                isSubmitting = uiState.isSending,
                onSubmit = {
                    viewModel.sendMessage(draftText) { ok, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (ok) {
                            showSendOverlay = false
                            draftText = ""
                        }
                    }
                },
                onDismiss = { showSendOverlay = false }
            )
        }
    }
}
