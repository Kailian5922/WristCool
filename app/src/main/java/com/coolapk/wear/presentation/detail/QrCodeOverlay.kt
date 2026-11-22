package com.coolapk.wear.presentation.detail

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.coolapk.wear.data.util.QrCodeGenerator
import com.coolapk.wear.presentation.components.ExpressiveLoadingIndicator
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.WearEdgeActionButton
import com.coolapk.wear.presentation.components.rotaryEventHandler

/**
 * 手机扫码查看二维码浮层（Wear M3 Expressive + 弧形关闭 EdgeButton）。
 */
@Composable
fun QrCodeOverlay(
    feedUrl: String,
    title: String = "手机扫码查看",
    subtitle: String = "微信 / 浏览器扫一扫直达原页面",
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val qrListState = rememberTransformingLazyColumnState()
    val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = feedUrl) {
        value = QrCodeGenerator.generate(feedUrl, 320)
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        HorologistScaffold(
            scrollState = qrListState,
            showTimeText = false,
            edgeButton = {
                WearEdgeActionButton(
                    scrollState = qrListState,
                    buttonSize = EdgeButtonSize.ExtraSmall,
                    onClick = onDismiss
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        ) { contentPadding ->
            TransformingLazyColumn(
                state = qrListState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(qrListState)
            ) {
                item(key = "qr_title") {
                    Text(
                        text = title,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "qr_code_body") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(134.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = qrBitmap
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "二维码",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                ExpressiveLoadingIndicator(size = 30.dp, contained = false)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = subtitle,
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyExtraSmall,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = feedUrl,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
