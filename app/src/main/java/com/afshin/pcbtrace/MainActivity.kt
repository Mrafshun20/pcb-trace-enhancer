package com.afshin.pcbtrace

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.*
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var wv: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)); fileCb = null
    }
    private val camResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val p = r.data?.getStringExtra("path") ?: return@registerForActivityResult
        Thread {
            val s = Base64.encodeToString(prep(p), Base64.NO_WRAP)
            var i = 0
            while (i < s.length) {
                val part = s.substring(i, minOf(i + 400000, s.length)); i += 400000
                runOnUiThread { wv.evaluateJavascript("nativeChunk('$part')", null) }
            }
            runOnUiThread { wv.evaluateJavascript("nativeDone()", null) }
        }.start()
    }
    private val permReq = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) camResult.launch(Intent(this, CameraActivity::class.java))
        else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
    }

    private fun prep(path: String): ByteArray {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var k = 1
        while (maxOf(o.outWidth, o.outHeight) / k > 3200) k *= 2
        val bm = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = k })
        val rot = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        val b2 = if (rot != 0f) Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, Matrix().apply { postRotate(rot) }, true) else bm
        val bo = ByteArrayOutputStream(); b2.compress(Bitmap.CompressFormat.JPEG, 93, bo)
        return bo.toByteArray()
    }

    inner class Bridge {
        @JavascriptInterface
        fun openCamera() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    camResult.launch(Intent(this@MainActivity, CameraActivity::class.java))
                else permReq.launch(Manifest.permission.CAMERA)
            }
        }
        @JavascriptInterface
        fun saveImage(b64: String) {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "pcb-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PCBTrace")
            }
            val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
            if (u != null) contentResolver.openOutputStream(u)?.use { it.write(Base64.decode(b64, Base64.DEFAULT)) }
            runOnUiThread { Toast.makeText(this@MainActivity, "Saved: Pictures/PCBTrace", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wv = WebView(this).apply { fitsSystemWindows = true }
        setContentView(wv)
        wv.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = true }
        wv.addJavascriptInterface(Bridge(), "Android")
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null); fileCb = cb
                picker.launch(p.createIntent()); return true
            }
        }
        wv.loadUrl("file:///android_asset/index.html")
    }
}
