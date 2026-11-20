package com.coolapk.wear.presentation.detail

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.rotaryEventHandler

enum class ComposerMode {
    REPLY_FEED,
    REPLY_COMMENT,
    FORWARD_FEED,
    NEW_FEED,
    PRIVATE_MESSAGE
}

/**
 * 统一的表端输入浮层（支持回复动态、回复评论/楼中楼、转发动态、发表新动态、发私信）。
 * 遵循 Wear M3 Expressive：TransformingLazyColumn + 底部弧形 EdgeButton 提交 + 语音听写 + 酷安表情标签 + 快捷短语。
 */
@Composable
fun ReplyOverlay(
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    mode: ComposerMode = ComposerMode.REPLY_FEED,
    targetLabel: String = "",
    isPrivate: Boolean = false,
    onPrivateChange: ((Boolean) -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val emojiScrollState = rememberScrollState()

    val titleText = when (mode) {
        ComposerMode.REPLY_FEED -> "回复动态"
        ComposerMode.REPLY_COMMENT -> if (targetLabel.isNotBlank()) "回复 @$targetLabel" else "回复评论"
        ComposerMode.FORWARD_FEED -> "转发动态"
        ComposerMode.NEW_FEED -> "发表新动态"
        ComposerMode.PRIVATE_MESSAGE -> if (targetLabel.isNotBlank()) "私信 $targetLabel" else "发送私信"
    }

    val submitLabel = when (mode) {
        ComposerMode.NEW_FEED -> "发布"
        ComposerMode.FORWARD_FEED -> "转发"
        ComposerMode.PRIVATE_MESSAGE -> "发送"
        else -> "发送回复"
    }

    val quickPhrases = remember(mode) {
        when (mode) {
            ComposerMode.NEW_FEED -> listOf(
                "#酷安手表# 抬腕刷酷安！",
                "今日份玩机打卡",
                "分享一下近况～",
                "大家觉得怎么样？"
            )
            ComposerMode.FORWARD_FEED -> listOf(
                "转发动态",
                "马克一下，太强了！",
                "前排围观 👀",
                "好内容分享给大家"
            )
            ComposerMode.PRIVATE_MESSAGE -> listOf(
                "你好！",
                "收到，谢谢！",
                "稍后手机回复你～",
                "在手表上看到啦 👍"
            )
            else -> listOf(
                "沙发！",
                "学到了 👍",
                "牛哇牛哇",
                "前排围观",
                "好图已收",
                "支持支持",
                "插眼",
                "太强了"
            )
        }
    }

    // 酷安原生文本表情标签（参考 c001apk-Revived 与 coolapk-desktop）
    val coolapkEmojis = remember {
        listOf("[滑稽]", "[doge]", "[赞]", "[酷币]", "[捂脸]", "[哈哈]", "[吃瓜]", "[流汗滑稽]")
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onReplyTextChange(if (replyText.isBlank()) spoken else "$replyText $spoken")
            }
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        HorologistScaffold(
            scrollState = listState,
            showTimeText = false,
            edgeButton = {
                WearEdgeActionButton(
                    scrollState = listState,
                    enabled = !isSubmitting && (replyText.isNotBlank() || mode == ComposerMode.FORWARD_FEED),
                    buttonSize = EdgeButtonSize.Small,
                    onClick = onSubmit
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = submitLabel,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = submitLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(listState)
            ) {
                item(key = "composer_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = titleText,
                            color = colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = onDismiss,
                            shapes = IconButtonDefaults.animatedShapes(),
                            modifier = Modifier.size(IconButtonDefaults.ExtraSmallButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                item(key = "composer_input") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .background(colorScheme.surfaceContainer)
                                .padding(10.dp)
                        ) {
                            BasicTextField(
                                value = replyText,
                                onValueChange = onReplyTextChange,
                                textStyle = TextStyle(color = colorScheme.onSurface, fontSize = 12.sp),
                                cursorBrush = SolidColor(colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                decorationBox = { innerTextField ->
                                    if (replyText.isEmpty()) {
                                        Text(
                                            text = "点击输入或使用下方语音…",
                                            color = colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 酷安表情标签快捷栏
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(emojiScrollState),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                coolapkEmojis.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colorScheme.surfaceContainerHigh)
                                            .clickable { onReplyTextChange("$replyText$tag") }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            color = colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyExtraSmall
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            FilledTonalIconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "说出内容…")
                                    }
                                    try {
                                        speechLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "未找到语音输入服务", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shapes = IconButtonDefaults.animatedShapes(),
                                modifier = Modifier.size(IconButtonDefaults.SmallButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "语音输入",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 新动态可选“仅自己可见”（借鉴 c001apk-Revived & coolapk-desktop）
                if (mode == ComposerMode.NEW_FEED && onPrivateChange != null) {
                    item(key = "composer_private_switch") {
                        SwitchButton(
                            checked = isPrivate,
                            onCheckedChange = onPrivateChange,
                            label = { Text("仅自己可见", maxLines = 1) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        )
                    }
                }

                item(key = "composer_phrases_label") {
                    Text(
                        text = "快捷短语",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                items(quickPhrases, key = { it }) { phrase ->
                    FilledTonalButton(
                        onClick = {
                            onReplyTextChange(if (replyText.isBlank()) phrase else "$replyText $phrase")
                        },
                        label = {
                            Text(
                                text = phrase,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    )
                }
            }
        }
    }
}
