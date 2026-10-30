package com.coolapk.wear.companion

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class WebLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 顶部工具栏
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0DAE62.toInt())
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(this).apply {
            text = "酷安官方安全登录"
            setTextColor(Color.WHITE)
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val doneButton = Button(this).apply {
            text = "完成并保存"
            setBackgroundColor(Color.WHITE)
            setTextColor(0xFF0DAE62.toInt())
            setOnClickListener {
                if (checkAndSaveCookies()) {
                    Toast.makeText(this@WebLoginActivity, "已成功保存登录凭据！", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@WebLoginActivity, "尚未检测到登录 Cookie，请先在下方网页完成登录", Toast.LENGTH_SHORT).show()
                }
            }
        }

        toolbar.addView(titleView)
        toolbar.addView(doneButton)
        rootLayout.addView(toolbar)

        // WebView 配置
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 CoolMarket/16.2.0"

            cookieManager.setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (checkAndSaveCookies()) {
                        Toast.makeText(this@WebLoginActivity, "检测到登录成功，已自动保存！", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }
        }

        rootLayout.addView(webView)
        setContentView(rootLayout)

        // 加载酷安官方登录页
        webView.loadUrl("https://account.coolapk.com/auth/login?type=coolapk")
    }

    private fun checkAndSaveCookies(): Boolean {
        val cookieManager = CookieManager.getInstance()
        val coolapkCookie = cookieManager.getCookie("https://account.coolapk.com") ?: ""
        val mainCookie = cookieManager.getCookie("https://www.coolapk.com") ?: ""
        val combined = listOf(coolapkCookie, mainCookie).filter { it.isNotBlank() }.joinToString("; ")

        if (combined.contains("uid=") || combined.contains("token=")) {
            val prefs = getSharedPreferences("coolapk_companion", Context.MODE_PRIVATE)
            prefs.edit().putString("cookie", combined).apply()
            return true
        }
        return false
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
