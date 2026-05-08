package com.joni.silverlining

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private var serverProcess: Process? = null
    private val notesDir = File("/storage/emulated/0/0everything/silverlining")
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById<WebView>(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        WebView.setWebContentsDebuggingEnabled(true)

        if (!hasStoragePermission()) {
            requestStoragePermission()
            return
        }
        startServer(webView)
    }

    override fun onResume() {
        super.onResume()
        if (serverProcess == null && hasStoragePermission()) {
            startServer(findViewById(R.id.webview))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serverProcess?.destroy()
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else true

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:$packageName"))
        )
    }

    private fun startServer(webView: WebView) {
        notesDir.mkdirs()
        val binary = File(applicationInfo.nativeLibraryDir, "libsilverbullet.so")

        val pb = ProcessBuilder(binary.absolutePath, notesDir.absolutePath)
            .redirectErrorStream(true)
        pb.environment()["SB_SHELL_BACKEND"] = "disabled"
        pb.environment()["HOME"] = filesDir.absolutePath
        serverProcess = pb.start()

        Thread {
            serverProcess?.inputStream?.bufferedReader()?.forEachLine {
                Log.i("silverbullet", it)
            }
        }.start()

        Thread {
            for (i in 0 until 60) {
                if (isServerReady()) {
                    mainHandler.post { webView.loadUrl("http://127.0.0.1:3000/") }
                    return@Thread
                }
                Thread.sleep(250)
            }
            Log.e("silverbullet", "Server failed to start within 15 seconds")
        }.start()
    }

    private fun isServerReady(): Boolean = try {
        val conn = URL("http://127.0.0.1:3000/").openConnection() as HttpURLConnection
        conn.connectTimeout = 500
        conn.requestMethod = "HEAD"
        conn.connect()
        conn.disconnect()
        true
    } catch (e: Exception) {
        false
    }
}
