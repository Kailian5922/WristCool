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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Smartphone
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
import androidx.compose.material3.TextButton
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
    var deviceId by remember {
        mutableStateOf(
            CookieHelper.parseShuzilmDeviceId(prefs.getString("device_id", "").orEmpty())
        )
    }
    var deviceIdInput by remember { mutableStateOf(deviceId) }
    var showDeviceGuide by remember { mutableStateOf(false) }

    // 不自动生成伪造 UUID 作为 ddid，避免酷安服务端在点赞等写接口触发风控拦截
    val ddid = remember {
        prefs.getString("ddid", "").orEmpty().takeUnless {
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(it)
        }.orEmpty()
    }

    val isLoggedIn = remember(cookie) { CookieHelper.isLoggedIn(cookie) }
    val uid = remember(cookie) { CookieHelper.extractValue(cookie, "uid") }
    val parsedDeviceIdInput = remember(deviceIdInput) { CookieHelper.parseShuzilmDeviceId(deviceIdInput) }

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

            // 数盟设备 ID 配置卡片（对齐 coolapk-desktop，解决评论区 err_request_captcha_v2 校验）
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = if (deviceId.isNotBlank()) CoolapkGreen else Color(0xFFD97706)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("数盟设备 ID（推荐配置）", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (deviceId.isNotBlank()) {
                                    val preview = if (deviceId.length > 16) {
                                        "${deviceId.take(8)}…${deviceId.takeLast(6)}"
                                    } else {
                                        deviceId
                                    }
                                    "已生效：$preview"
                                } else {
                                    "未配置（手表评论区可能触发验证码拦截）"
                                },
                                fontSize = 12.sp,
                                color = if (deviceId.isNotBlank()) Color(0xFF15803D) else Color(0xFFB45309)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = deviceIdInput,
                        onValueChange = { deviceIdInput = it },
                        label = { Text("粘贴官方酷安设备日志或 DU... 设备 ID") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    if (parsedDeviceIdInput.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (parsedDeviceIdInput == deviceId) {
                                "已识别有效设备 ID（当前生效中）"
                            } else {
                                "已识别有效设备 ID：${parsedDeviceIdInput.take(10)}…（点击“保存”生效）"
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF15803D)
                        )
                    } else if (deviceIdInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "未能识别有效设备 ID，请确认已复制官方酷安的设备 ID（形如 DU...）",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = if (clipboard != null && clipboard.hasPrimaryClip()) {
                                    clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                } else {
                                    ""
                                }
                                if (clipText.isBlank()) {
                                    Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                } else {
                                    deviceIdInput = clipText
                                    val extracted = CookieHelper.parseShuzilmDeviceId(clipText)
                                    if (extracted.isNotEmpty()) {
                                        prefs.edit().putString("device_id", extracted).apply()
                                        deviceId = extracted
                                        deviceIdInput = extracted
                                        Toast.makeText(context, "已识别并保存数盟设备 ID！", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "已粘贴内容，但未识别出 DU... 设备 ID", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("粘贴", maxLines = 1)
                        }

                        Button(
                            onClick = {
                                val extracted = CookieHelper.parseShuzilmDeviceId(deviceIdInput)
                                if (extracted.isEmpty()) {
                                    Toast.makeText(context, "请填入有效的数盟设备 ID（形如 DU...）", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                prefs.edit().putString("device_id", extracted).apply()
                                deviceId = extracted
                                deviceIdInput = extracted
                                Toast.makeText(context, "数盟设备 ID 已保存，点下方同步给手表即可生效", Toast.LENGTH_SHORT).show()
                            },
                            enabled = parsedDeviceIdInput.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = CoolapkGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("保存", maxLines = 1)
                        }

                        if (deviceId.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    prefs.edit().remove("device_id").apply()
                                    deviceId = ""
                                    deviceIdInput = ""
                                    Toast.makeText(context, "已清除数盟设备 ID", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                            ) {
                                Text("清除", maxLines = 1)
                            }
                        }
                    }

                    TextButton(
                        onClick = { showDeviceGuide = !showDeviceGuide },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (showDeviceGuide) "收起官方酷安获取步骤" else "如何从官方安卓酷安复制设备 ID？",
                            fontSize = 12.sp
                        )
                    }

                    if (showDeviceGuide) {
                        Text(
                            text = "1. 打开官方安卓酷安 App →【我】→ 右上角【设置】→ 最底部【关于】\n" +
                                "2. 点击右上角【三个点】→【检查日志】→ 下滑找到【复制 OAID】\n" +
                                "3. 连续快速点击【复制 OAID】5 次，看到“设备ID 已复制”提示后回到此处点击【粘贴】即可自动识别保存。",
                            fontSize = 12.sp,
                            color = Color(0xFF555555),
                            lineHeight = 18.sp
                        )
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
                        text = if (deviceId.isNotBlank()) {
                            "点击下方按钮将【登录 Cookie + 数盟设备 ID】一并打包复制到剪贴板，在手表【个人中心】点击【同步伴侣登录】即可生效！"
                        } else {
                            "点击下方按钮生成凭据并复制到手机剪贴板。建议先在上方粘贴官方数盟设备 ID，再同步给手表以减少评论区风控。"
                        },
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
                            val payload = CookieHelper.buildCompanionPayload(cookie, ddid, deviceId)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = ClipData.newPlainText("CoolapkWearAuth", payload).apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    description.extras = PersistableBundle().apply {
                                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                    }
                                }
                            }
                            clipboard.setPrimaryClip(clipData)
                            val toastText = if (deviceId.isNotBlank()) {
                                "凭据与数盟设备 ID 已复制！请在手表【个人中心】点击【同步伴侣登录】"
                            } else {
                                "凭据已复制！请在手表【个人中心】点击【导入伴侣登录】"
                            }
                            Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                        },
                        enabled = isLoggedIn,
                        colors = ButtonDefaults.buttonColors(containerColor = CoolapkGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (deviceId.isNotBlank()) "复制登录与设备信息给手表" else "复制登录信息给手表",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
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
