package com.coolapk.wear.presentation.detail

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.coolapk.wear.presentation.components.WearListContentPadding
import com.coolapk.wear.presentation.components.rotaryEventHandler
import com.coolapk.wear.presentation.theme.LocalAppOnPrimaryColor
import com.coolapk.wear.presentation.theme.LocalAppPrimaryColor
import com.coolapk.wear.presentation.theme.WearSurface
import com.coolapk.wear.presentation.theme.WearSurfaceVariant

@Composable
fun ReplyOverlay(
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = LocalAppPrimaryColor.current
    val onPrimaryColor = LocalAppOnPrimaryColor.current
    val context = LocalContext.current
    val replyListState = rememberScalingLazyListState()

    val quickPhrases = remember {
        listOf("沙发！", "学到了 👍", "牛哇牛哇", "前排围观", "好图已收", "支持支持", "插眼", "太强了")
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
            .background(Color.Black.copy(alpha = 0.96f))
    ) {
        ScalingLazyColumn(
            state = replyListState,
            contentPadding = WearListContentPadding,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            modifier = Modifier
                .fillMaxSize()
                .rotaryEventHandler(replyListState)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "回复评论",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    CompactButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WearSurface)
                            .padding(8.dp)
                    ) {
                        BasicTextField(
                            value = replyText,
                            onValueChange = onReplyTextChange,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(primaryColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            decorationBox = { innerTextField ->
                                if (replyText.isEmpty()) {
                                    Text(
                                        text = "点击输入评论...",
                                        color = Color(0xFF888888),
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CompactButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你想回复的内容...")
                                }
                                try {
                                    speechLauncher.launch(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "未找到语音输入服务", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = WearSurfaceVariant),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "语音输入",
                                tint = primaryColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "快捷短语：",
                    color = Color(0xFFA0A0A5),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            items(quickPhrases) { phrase ->
                CompactChip(
                    onClick = {
                        onReplyTextChange(if (replyText.isBlank()) phrase else "$replyText $phrase")
                    },
                    label = { Text(phrase, fontSize = 11.sp) },
                    colors = ChipDefaults.chipColors(backgroundColor = WearSurfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Chip(
                    onClick = onSubmit,
                    enabled = !isSubmitting && replyText.isNotBlank(),
                    label = {
                        Text(
                            text = if (isSubmitting) "发送中..." else "发送回复",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = onPrimaryColor
                        )
                    },
                    icon = {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                indicatorColor = onPrimaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "发送",
                                tint = onPrimaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = primaryColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
