package com.coolapk.wear.companion

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CoolapkGreen = Color(0xFF0DAE62)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = CoolapkGreen,
                    surface = Color.White
                )
            ) {
                CompanionHomeScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionHomeScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("coolapk_companion", Context.MODE_PRIVATE) }

    var cookie by remember { mutableStateOf(prefs.getString("cookie", "").orEmpty()) }
    // 不自动生成伪造 UUID 作为 ddid，避免酷安服务端在点赞等写接口触发风控拦截
    val ddid = remember {
        prefs.getString("ddid", "").orEmpty().takeUnless {
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(it)
        }.orEmpty()
    }

    val isLoggedIn = remember(cookie) { CookieHelper.isLoggedIn(cookie) }
    val uid = remember(cookie) { CookieHelper.extractValue(cookie, "uid") }

    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            cookie = prefs.getString("cookie", "").orEmpty()
        }
    }

    var showManualEdit by remember { mutableStateOf(false) }
    var manualCookieText by remember { mutableStateOf(cookie) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "酷安手表伴侣 v${BuildConfig.VERSION_NAME}",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CoolapkGreen)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 登录状态卡片
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLoggedIn) Color(0xFFF0FDF4) else Color(0xFFF9FAFB)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isLoggedIn) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            tint = if (isLoggedIn) CoolapkGreen else Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isLoggedIn) "已登录酷安账号" else "未登录酷安账号",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLoggedIn) Color(0xFF15803D) else Color.Black
                            )
                            Text(
                                text = if (isLoggedIn) "UID: $uid" else "登录后可将账号凭据一键传递至手表",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    if (!isLoggedIn) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                loginLauncher.launch(Intent(context, WebLoginActivity::class.java))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CoolapkGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("网页授权登录酷安")
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                prefs.edit().remove("cookie").apply()
                                cookie = ""
                                // 同步清理 WebView Cookie 与存储，避免下次登录仍自动复用旧账号会话
                                runCatching {
                                    val cm = CookieManager.getInstance()
                                    cm.removeAllCookies(null)
                                    cm.flush()
                                    WebStorage.getInstance().deleteAllData()
                                }
                                Toast.makeText(context, "已退出登录并清理网页会话", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("退出当前账号")
                        }
                    }
                }
            }

            // 手表端凭据互通
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = CoolapkGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("手表端凭据互通", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击下方按钮生成凭据并复制到手机剪贴板。若手表支持跨端剪贴板，直接在手表【我的】中点【导入伴侣登录信息】即可完成登录！",
                        fontSize = 13.sp,
                        color = Color(0xFF555555),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!isLoggedIn) {
                                Toast.makeText(context, "请先登录酷安账号", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val payload = CookieHelper.buildCompanionPayload(cookie, ddid)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = ClipData.newPlainText("CoolapkWearAuth", payload).apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    description.extras = PersistableBundle().apply {
                                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                    }
                                }
                            }
                            clipboard.setPrimaryClip(clipData)
                            Toast.makeText(
                                context,
                                "凭据已复制！请在手表【我的】中点击【导入伴侣登录信息】",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        enabled = isLoggedIn,
                        colors = ButtonDefaults.buttonColors(containerColor = CoolapkGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制登录信息给手表", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 手动编辑 Cookie 辅助选项
            OutlinedButton(
                onClick = {
                    manualCookieText = cookie
                    showManualEdit = !showManualEdit
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showManualEdit) "收起手动输入" else "手动填入/修改 Cookie")
            }

            if (showManualEdit) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualCookieText,
                        onValueChange = { manualCookieText = it },
                        label = { Text("Cookie 原始文本（需包含 uid 与 token）") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    Button(
                        onClick = {
                            val normalized = CookieHelper.mergeCookies(manualCookieText)
                            if (!CookieHelper.isLoggedIn(normalized)) {
                                Toast.makeText(
                                    context,
                                    "Cookie 缺少有效的 uid 或 token 字段，请检查后重试",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            prefs.edit().putString("cookie", normalized).apply()
                            cookie = normalized
                            showManualEdit = false
                            Toast.makeText(context, "Cookie 已更新保存", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoolapkGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存 Cookie")
                    }
                }
            }
        }
    }
}
