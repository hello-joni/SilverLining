package com.joni.silverlining

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "silverlining"
        private const val KEY_NOTES_PATH = "notesFolderPath"
    }

    private var serverProcess: Process? = null
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private fun notesDir(): File? =
        prefs.getString(KEY_NOTES_PATH, null)?.let { File(it) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var folderPromptShown = false

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val path = treeUriToFilesystemPath(uri)
        if (path == null) {
            Toast.makeText(
                this,
                "Couldn't resolve folder path. Only primary internal storage is supported.",
                Toast.LENGTH_LONG
            ).show()
            return@registerForActivityResult
        }
        prefs.edit().putString(KEY_NOTES_PATH, path).apply()
        restartServer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById<WebView>(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        WebView.setWebContentsDebuggingEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Exit ${getString(R.string.app_name)}?")
                        .setPositiveButton("Exit") { _, _ ->
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        })

        val fab = findViewById<FloatingActionButton>(R.id.settings_fab)
        fab.setOnClickListener { folderPickerLauncher.launch(null) }

        if (!hasStoragePermission()) {
            requestStoragePermission()
            return
        }
        setupAndStart()
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) setupAndStart()
    }

    private fun setupAndStart() {
        if (notesDir() == null) {
            if (!folderPromptShown) {
                folderPromptShown = true
                showFolderSetupDialog()
            }
            return
        }
        if (serverProcess == null) {
            startServer(findViewById(R.id.webview))
        }
    }

    private fun showFolderSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Welcome to ${getString(R.string.app_name)}")
            .setMessage("Pick a folder where your notes will live. You can change this later via the settings button.")
            .setPositiveButton("Choose folder") { _, _ -> folderPickerLauncher.launch(null) }
            .setCancelable(false)
            .show()
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
        val notesDir = notesDir() ?: return
        notesDir.mkdirs()
        val binary = File(applicationInfo.nativeLibraryDir, "libsilverbullet.so")

        val pb = ProcessBuilder(binary.absolutePath, notesDir.absolutePath)
            .redirectErrorStream(true)
        pb.environment()["SB_SHELL_BACKEND"] = "disabled"
        pb.environment()["HOME"] = filesDir.absolutePath
        serverProcess = pb.start()

        Thread {
            try {
                serverProcess?.inputStream?.bufferedReader()?.forEachLine {
                    Log.i("silverbullet", it)
                }
            } catch (e: java.io.IOException) {
                // Expected when the subprocess is destroyed (e.g. during a
                // folder switch); log so an unexpected close doesn't go silent.
                Log.i("silverbullet", "Log stream closed: ${e.message}")
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

    private fun restartServer() {
        serverProcess?.destroy()
        serverProcess = null
        val webView = findViewById<WebView>(R.id.webview)
        webView.loadUrl("about:blank")
        startServer(webView)
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

    private fun treeUriToFilesystemPath(uri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2 || parts[0] != "primary") return null
        val rel = parts[1]
        return if (rel.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$rel"
    }
}
