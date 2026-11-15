package com.afshin.pcbtrace

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import java.io.File

class CameraActivity : ComponentActivity() {
    private var cap: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pv = PreviewView(this)
        val zl = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f }
        val sb = SeekBar(this).apply { max = 100 }
        val shot = Button(this).apply { text = "عکس" }
        val tor = Button(this).apply { text = "🔦"; visibility = View.GONE }
        val close = Button(this).apply { text = "✕" }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(close); addView(tor); addView(shot)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xCC000000.toInt()); setPadding(24, 16, 24, 16)
            addView(zl); addView(sb); addView(row)
        }
        val root = FrameLayout(this).apply {
            fitsSystemWindows = true; setBackgroundColor(Color.BLACK); addView(pv)
            addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        setContentView(root)

        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            val p = fut.get()
            val prev = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
            val c0 = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            p.unbindAll()
            val c = p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, prev, c0)
            cap = c0
            val z = c.cameraInfo.zoomState.value
            val mn = z?.minZoomRatio ?: 1f
            val mx = z?.maxZoomRatio ?: 1f
            val start = 3f.coerceIn(mn, mx)
            fun setZ(r: Float) { c.cameraControl.setZoomRatio(r.coerceIn(mn, mx)) }
            setZ(start)
            if (mx > mn) sb.progress = ((start - mn) / (mx - mn) * 100).toInt()
            c.cameraInfo.zoomState.observe(this, Observer<ZoomState> { zl.text = "%.1f×".format(it.zoomRatio) })
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { if (u) setZ(mn + (mx - mn) * v / 100f) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            pv.postDelayed({ setZ(start) }, 600)
            if (c.cameraInfo.hasFlashUnit()) {
                tor.visibility = View.VISIBLE
                var on = false
                tor.setOnClickListener { on = !on; c.cameraControl.enableTorch(on) }
            }
        }, ContextCompat.getMainExecutor(this))

        close.setOnClickListener { finish() }
        shot.setOnClickListener {
            val f = File(cacheDir, "cap.jpg")
            cap?.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                        setResult(RESULT_OK, Intent().putExtra("path", f.absolutePath)); finish()
                    }
                    override fun onError(e: ImageCaptureException) { finish() }
                })
        }
    }
}
