package com.afshin.pcbtrace

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import java.io.File

class CameraActivity : ComponentActivity() {
    private var provider: ProcessCameraProvider? = null
    private var cap: ImageCapture? = null
    private var cam: Camera? = null
    private var torchOn = false
    private lateinit var pv: PreviewView
    private lateinit var zl: TextView
    private lateinit var sb: SeekBar
    private lateinit var tor: Button

    private fun lenses(): List<Pair<String, Float>> {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val out = ArrayList<Pair<String, Float>>()
        for (id in cm.cameraIdList) try {
            val ch = cm.getCameraCharacteristics(id)
            if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
            val f = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: continue
            val ss = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: continue
            val eq = f * 43.27f / Math.hypot(ss.width.toDouble(), ss.height.toDouble()).toFloat()
            if (out.none { Math.abs(it.second - eq) < 1f }) out.add(id to eq)
        } catch (_: Exception) {}
        return out.sortedBy { it.second }
    }

    private fun bind(id: String?) {
        val p = provider ?: return
        val rs = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build()
        val prev = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
        val c0 = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setResolutionSelector(rs).build()
        val sel = if (id == null) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.Builder()
            .addCameraFilter { l -> l.filter { Camera2CameraInfo.from(it).cameraId == id } }.build()
        p.unbindAll()
        val c = try { p.bindToLifecycle(this, sel, prev, c0) } catch (e: Exception) {
            Toast.makeText(this, "Lens not exposed to apps; using default", Toast.LENGTH_LONG).show()
            p.unbindAll(); p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, prev, c0)
        }
        cam = c; cap = c0; torchOn = false
        val z = c.cameraInfo.zoomState.value
        val mn = z?.minZoomRatio ?: 1f
        val mx = z?.maxZoomRatio ?: 1f
        val start = (if (id == null) 3f else mn).coerceIn(mn, mx)
        fun setZ(r: Float) { c.cameraControl.setZoomRatio(r.coerceIn(mn, mx)) }
        setZ(start)
        sb.progress = if (mx > mn) ((start - mn) / (mx - mn) * 100).toInt() else 0
        c.cameraInfo.zoomState.observe(this, Observer<ZoomState> { zl.text = "%.1f× (max %.1f×)".format(it.zoomRatio, it.maxZoomRatio) })
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { if (u) setZ(mn + (mx - mn) * v / 100f) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        pv.postDelayed({ setZ(start) }, 600)
        tor.visibility = if (c.cameraInfo.hasFlashUnit()) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pv = PreviewView(this)
        zl = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f }
        sb = SeekBar(this).apply { max = 100 }
        val shot = Button(this).apply { text = "عکس" }
        tor = Button(this).apply { text = "🔦"; visibility = View.GONE }
        val close = Button(this).apply { text = "✕" }
        val lensRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val ls = lenses()
        if (ls.size > 1) {
            lensRow.addView(Button(this).apply { text = "Auto 3×"; setOnClickListener { bind(null) } })
            for ((id, eq) in ls) lensRow.addView(Button(this).apply { text = "%.0fmm".format(eq); setOnClickListener { bind(id) } })
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(close); addView(tor); addView(shot)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xCC000000.toInt()); setPadding(24, 16, 24, 16)
            addView(zl); addView(sb); addView(lensRow); addView(row)
        }
        val root = FrameLayout(this).apply {
            fitsSystemWindows = true; setBackgroundColor(Color.BLACK); addView(pv)
            addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        setContentView(root)

        pv.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val pt = pv.meteringPointFactory.createPoint(e.x, e.y)
                cam?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
                v.performClick()
            }
            true
        }
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({ provider = fut.get(); bind(null) }, ContextCompat.getMainExecutor(this))

        tor.setOnClickListener { torchOn = !torchOn; cam?.cameraControl?.enableTorch(torchOn) }
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
