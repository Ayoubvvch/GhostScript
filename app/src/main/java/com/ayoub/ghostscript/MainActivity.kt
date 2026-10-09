package com.ayoub.ghostscript

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "GhostMainActivity"
    }

    private lateinit var webView: WebView
    private lateinit var taskManager: TaskManager
    private val scope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set modern dark status bar & navigation bar
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.parseColor("#0b0f19")
            navigationBarColor = Color.parseColor("#0b0f19")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            }
        }

        taskManager = TaskManager.getInstance(this)

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0b0f19"))
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    syncStateToJs()
                }
            }

            addJavascriptInterface(GhostBridge(), "GhostNative")
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.handleAndroidBack ? window.handleAndroidBack() : false") { result ->
                    if (result != "true") {
                        moveTaskToBack(true)
                    }
                }
            }
        })

        webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onResume() {
        super.onResume()
        syncStateToJs()
    }

    private fun syncStateToJs() {
        CoroutineScope(Dispatchers.IO).launch {
            val rootOk = RootShell.isRootAvailable()
            val tasksJson = taskManager.getTasksJson()
            val presetsJson = taskManager.getPresetsJson()

            val stateJson = JSONObject().apply {
                put("isRoot", rootOk)
                put("tasks", org.json.JSONArray(tasksJson))
                put("presets", org.json.JSONArray(presetsJson))
            }.toString()

            withContext(Dispatchers.Main) {
                val escaped = stateJson.replace("\\", "\\\\").replace("'", "\\'")
                webView.evaluateJavascript("if(window.onGhostStateReceived) window.onGhostStateReceived('$escaped');", null)
            }
        }
    }

    inner class GhostBridge {

        @JavascriptInterface
        fun getInitialState(): String {
            return try {
                val rootOk = RootShell.isRootAvailable()
                val tasksJson = taskManager.getTasksJson()
                val presetsJson = taskManager.getPresetsJson()
                JSONObject().apply {
                    put("isRoot", rootOk)
                    put("tasks", org.json.JSONArray(tasksJson))
                    put("presets", org.json.JSONArray(presetsJson))
                }.toString()
            } catch (e: Exception) {
                "{}"
            }
        }

        @JavascriptInterface
        fun requestRefresh() {
            syncStateToJs()
        }

        @JavascriptInterface
        fun startSleepTimer(minutes: Int, optionsJson: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val opts = JSONObject(if (optionsJson.isBlank()) "{}" else optionsJson)
                    val pauseMedia = opts.optBoolean("pauseMedia", true)
                    val muteAudio = opts.optBoolean("muteAudio", false)
                    val turnOffWifi = opts.optBoolean("turnOffWifi", false)
                    val turnOffBluetooth = opts.optBoolean("turnOffBluetooth", false)

                    taskManager.startSleepTimer(
                        minutes = minutes,
                        pauseMedia = pauseMedia,
                        muteAudio = muteAudio,
                        turnOffWifi = turnOffWifi,
                        turnOffBluetooth = turnOffBluetooth
                    )
                    syncStateToJs()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting sleep timer", e)
                }
            }
        }

        @JavascriptInterface
        fun startBuiltin(type: String, delaySeconds: Long) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    taskManager.startBuiltinTask(type, delaySeconds)
                    syncStateToJs()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting builtin task", e)
                }
            }
        }

        @JavascriptInterface
        fun startCustom(name: String, commands: String, delaySeconds: Long) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    taskManager.startCustomTask(name, commands, delaySeconds)
                    syncStateToJs()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting custom task", e)
                }
            }
        }

        @JavascriptInterface
        fun stopTask(taskId: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    taskManager.stopTask(taskId)
                    syncStateToJs()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping task", e)
                }
            }
        }

        @JavascriptInterface
        fun deleteTask(taskId: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    taskManager.deleteTask(taskId)
                    syncStateToJs()
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting task", e)
                }
            }
        }

        @JavascriptInterface
        fun clearHistory() {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    taskManager.clearHistory()
                    syncStateToJs()
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing history", e)
                }
            }
        }

        @JavascriptInterface
        fun getTaskLog(taskId: String): String {
            return try {
                taskManager.getTaskLog(taskId)
            } catch (e: Exception) {
                "تعذر قراءة السجل"
            }
        }

        @JavascriptInterface
        fun savePreset(name: String, desc: String, commands: String, delaySeconds: Long) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    taskManager.savePreset(name, desc, commands, delaySeconds)
                    syncStateToJs()
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving preset", e)
                }
            }
        }

        @JavascriptInterface
        fun deletePreset(presetId: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    taskManager.deletePreset(presetId)
                    syncStateToJs()
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting preset", e)
                }
            }
        }
    }
}
