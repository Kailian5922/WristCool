package com.coolapk.wear.presentation.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.coolapk.wear.data.api.CaptchaChallenge

/**
 * 酷安官方网易易盾交互式人机验证浮层（供用户本人直接在手表上滑动完成验证）。
 * 验证成功后回调官方标准格式 Token：`NEC:{captchaId前8位}:{validate}`。
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun CaptchaVerifyOverlay(
    challenge: CaptchaChallenge,
    onVerified: (postToken: String, captchaField: String) -> Unit,
    onDismiss: () -> Unit,
    onOpenQrFallback: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val safeCaptchaId = remember(challenge.captchaId) {
        challenge.captchaId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { CaptchaChallenge.DEFAULT_COOLAPK_CAPTCHA_ID }
    }
    val safeField = remember(challenge.captchaField) {
        CaptchaChallenge.sanitizeField(challenge.captchaField)
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    BackHandler(onBack = onDismiss)
    RotaryScrollLock()

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.apply {
                    stopLoading()
                    removeJavascriptInterface("WearCaptchaBridge")
                    destroy()
                }
            }
            webViewRef = null
        }
    }

    val htmlContent = remember(safeCaptchaId, reloadTick) {
        buildYidunCaptchaHtml(safeCaptchaId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (errorMessage == null) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 34.dp, bottom = 14.dp, start = 6.dp, end = 6.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(AndroidColor.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        // 防止手表右滑返回手势劫持滑块拖动
                        setOnTouchListener { view, _ ->
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onReady() {
                                    mainHandler.post { isLoading = false }
                                }

                                @JavascriptInterface
                                fun onVerified(validate: String) {
                                    val cleanValidate = validate.trim()
                                    if (cleanValidate.isEmpty()) return
                                    val prefix = safeCaptchaId.take(8)
                                    val token = "NEC:$prefix:$cleanValidate"
                                    mainHandler.post {
                                        onVerified(token, safeField)
                                    }
                                }

                                @JavascriptInterface
                                fun onError(msg: String) {
                                    mainHandler.post {
                                        isLoading = false
                                        errorMessage = msg.ifBlank { "人机验证组件加载失败" }
                                    }
                                }
                            },
                            "WearCaptchaBridge"
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    mainHandler.post {
                                        isLoading = false
                                        errorMessage = "无法加载验证页面，请检查网络"
                                    }
                                }
                            }
                        }
                        webViewRef = this
                        loadDataWithBaseURL(
                            "https://www.coolapk.com/",
                            htmlContent,
                            "text/html",
                            "utf-8",
                            null
                        )
                    }
                },
                update = { view ->
                    if (view.tag != reloadTick) {
                        view.tag = reloadTick
                        if (reloadTick > 0) {
                            isLoading = true
                            view.loadDataWithBaseURL(
                                "https://www.coolapk.com/",
                                htmlContent,
                                "text/html",
                                "utf-8",
                                null
                            )
                        }
                    }
                }
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "人机验证暂不可用",
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            reloadTick++
                        },
                        shapes = IconButtonDefaults.animatedShapes(),
                        modifier = Modifier.size(IconButtonDefaults.SmallButtonSize)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "重试验证", modifier = Modifier.size(16.dp))
                    }
                    if (onOpenQrFallback != null) {
                        FilledTonalButton(
                            onClick = onOpenQrFallback,
                            icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text("手机扫码", maxLines = 1) }
                        )
                    }
                }
            }
        }

        if (isLoading && errorMessage == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveLoadingIndicator(size = 38.dp, contained = false)
            }
        }

        // 顶部精简标题与关闭按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        ) {
            Text(
                text = "安全验证",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            FilledTonalIconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.16f),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭验证",
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

private fun buildYidunCaptchaHtml(captchaId: String): String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no" />
<style>
  html, body {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
    background: #000000;
    color: #ffffff;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
  }
  #captcha {
    width: 94%;
    max-width: 260px;
  }
</style>
</head>
<body>
<div id="captcha"></div>
<script src="https://cstaticdun.126.net/load.min.js" charset="utf-8" onerror="window.WearCaptchaBridge && WearCaptchaBridge.onError('验证码脚本加载失败')"></script>
<script>
  (function() {
    function start() {
      if (typeof window.initNECaptcha !== 'function') {
        if (window.WearCaptchaBridge) WearCaptchaBridge.onError('验证码组件未就绪');
        return;
      }
      window.initNECaptcha({
        captchaId: '$captchaId',
        element: '#captcha',
        mode: 'embed',
        apiVersion: 2,
        width: '100%',
        lang: 'zh-CN',
        onReady: function() {
          if (window.WearCaptchaBridge) WearCaptchaBridge.onReady();
        },
        onVerify: function(err, data) {
          if (!err && data && data.validate) {
            if (window.WearCaptchaBridge) WearCaptchaBridge.onVerified(data.validate);
          }
        }
      }, function() {
        if (window.WearCaptchaBridge) WearCaptchaBridge.onReady();
      }, function(err) {
        var msg = (typeof err === 'string') ? err : (err && err.message ? err.message : '初始化验证失败');
        if (window.WearCaptchaBridge) WearCaptchaBridge.onError(msg);
      });
    }
    if (document.readyState === 'complete') {
      start();
    } else {
      window.addEventListener('load', start);
    }
  })();
</script>
</body>
</html>
""".trimIndent()
