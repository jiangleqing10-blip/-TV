package com.muqitv.tvapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 单 Activity 承载全部流程：
 * 1) 首次启动要求输入激活码（存本地，以后不再问）
 * 2) 用内置密码自动登录网站（后台请求 /api/auth 拿到 Cookie，注入 WebView，
 *    这样电视用户不需要自己再手动输入网站密码）
 * 3) WebView 全屏展示 muqitv 网站
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var errorOverlay: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var activationOverlay: LinearLayout
    private lateinit var codeInput: EditText
    private lateinit var activationErrorText: TextView
    private lateinit var activateButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient()

    // 支持 HTML5 <video> 原生全屏（webkit 全屏 API），电视上看视频体验更好
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        errorOverlay = findViewById(R.id.errorOverlay)
        errorText = findViewById(R.id.errorText)
        retryButton = findViewById(R.id.retryButton)
        activationOverlay = findViewById(R.id.activationOverlay)
        codeInput = findViewById(R.id.codeInput)
        activationErrorText = findViewById(R.id.activationErrorText)
        activateButton = findViewById(R.id.activateButton)

        retryButton.setOnClickListener { loginAndLoadSite() }
        activateButton.setOnClickListener { attemptActivation() }
        codeInput.setOnEditorActionListener { _, _, _ ->
            attemptActivation()
            true
        }

        if (PrefsHelper.isActivated(this)) {
            loginAndLoadSite()
        } else {
            showOnly(activationOverlay)
            activateButton.requestFocus()
        }
    }

    private fun attemptActivation() {
        val input = codeInput.text?.toString()?.trim().orEmpty()
        if (input == Constants.ACTIVATION_CODE) {
            PrefsHelper.setActivated(this)
            activationErrorText.visibility = View.INVISIBLE
            loginAndLoadSite()
        } else {
            activationErrorText.visibility = View.VISIBLE
        }
    }

    /** 后台用内置密码登录网站拿到会话 Cookie，再加载首页；电视用户全程不需要输入网站密码 */
    private fun loginAndLoadSite() {
        showOnly(loadingOverlay)

        val body = """{"password":"${Constants.SITE_PASSWORD}"}"""
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("${Constants.SERVER_URL}/api/auth")
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                mainHandler.post { showError(getString(R.string.network_error)) }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                // 把登录接口下发的 Set-Cookie 转交给 WebView 的 CookieManager，
                // 这样 WebView 加载首页时就已经是登录状态，不会再弹密码框
                val cookies = response.headers("Set-Cookie")
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                for (cookie in cookies) {
                    cookieManager.setCookie(Constants.SERVER_URL, cookie)
                }
                cookieManager.flush()
                response.close()
                mainHandler.post { setupWebViewIfNeeded(); webView.loadUrl(Constants.SERVER_URL) }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewIfNeeded() {
        if (webView.webViewClient is SiteWebViewClient) return

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        webView.webViewClient = SiteWebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                (webView.parent as? android.view.ViewGroup)?.addView(
                    view,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                val view = customView ?: return
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
            }
        }
    }

    private inner class SiteWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            showOnly(webView)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            // 只有主框架加载失败才整页报错，避免某个小资源 404 就整页跳错误页
            if (request.isForMainFrame) {
                showError(getString(R.string.network_error))
            }
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        showOnly(errorOverlay)
    }

    /** 四个遮罩/内容层互斥显示，保证任何时刻只有一个可见 */
    private fun showOnly(target: View) {
        loadingOverlay.visibility = if (target === loadingOverlay) View.VISIBLE else View.GONE
        errorOverlay.visibility = if (target === errorOverlay) View.VISIBLE else View.GONE
        activationOverlay.visibility = if (target === activationOverlay) View.VISIBLE else View.GONE
        webView.visibility = if (target === webView) View.VISIBLE else View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 遥控器返回键：WebView 内有历史记录就后退，否则按系统默认行为（退出/切后台）
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.visibility == View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
