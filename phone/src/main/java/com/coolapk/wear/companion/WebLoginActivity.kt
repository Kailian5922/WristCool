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
import androidx.activity.OnBackPressedCallback

class WebLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var hasCompleted = false

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
                if (tryCaptureAndFinish(autoDetected = false)) {
                    return@setOnClickListener
                }
                Toast.makeText(
                    this@WebLoginActivity,
                    "尚未检测到完整登录 Cookie（需包含 uid 与 token），请先完成登录",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        toolbar.addView(titleView)
        toolbar.addView(doneButton)
        rootLayout.addView(toolbar)

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
                    tryCaptureAndFinish(autoDetected = true)
                }
            }
        }

        rootLayout.addView(webView)
        setContentView(rootLayout)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        webView.loadUrl("https://account.coolapk.com/auth/login?type=coolapk")
    }

    private fun tryCaptureAndFinish(autoDetected: Boolean): Boolean {
        if (hasCompleted) return true
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()

        val accountCookie = cookieManager.getCookie("https://account.coolapk.com").orEmpty()
        val mainCookie = cookieManager.getCookie("https://www.coolapk.com").orEmpty()
        val apiCookie = cookieManager.getCookie("https://api.coolapk.com").orEmpty()
        val merged = CookieHelper.mergeCookies(accountCookie, mainCookie, apiCookie)

        if (CookieHelper.isLoggedIn(merged)) {
            hasCompleted = true
            val prefs = getSharedPreferences("coolapk_companion", Context.MODE_PRIVATE)
            prefs.edit().putString("cookie", merged).apply()
            val toastMsg = if (autoDetected) "检测到登录成功，已自动保存！" else "已成功保存登录凭据！"
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
            return true
        }
        return false
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }
}
