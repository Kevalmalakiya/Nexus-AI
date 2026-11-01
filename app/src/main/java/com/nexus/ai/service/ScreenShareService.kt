package com.nexus.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream

/** Captures the screen ~1 fps while the user keeps sharing. Visible notification with STOP. */
class ScreenShareService : Service() {
    companion object {
        @Volatile var sink: ((ByteArray) -> Unit)? = null
        val state = MutableStateFlow(false)
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var last = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notif(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("nexus_screen", "Screen sharing", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ScreenShareService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "nexus_screen")
            .setContentTitle("NEXUS is sharing your screen")
            .setContentText("Screenshots go to Gemini about once per second.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "STOP", stop).build())
            .setOngoing(true)
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        val n = notif()
        if (Build.VERSION.SDK_INT >= 29) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(2, n)
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data)
        if (p == null) { stopSelf(); return START_NOT_STICKY }
        projection = p
        val t = HandlerThread("nexus-share").also { it.start() }
        thread = t
        val handler = Handler(t.looper)
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)
        val m = resources.displayMetrics
        val scale = minOf(1f, 1024f / maxOf(m.widthPixels, m.heightPixels))
        val w = (m.widthPixels * scale).toInt() / 2 * 2
        val h = (m.heightPixels * scale).toInt() / 2 * 2
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        display = p.createVirtualDisplay("nexus-share", w, h, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
        r.setOnImageAvailableListener({ ir -> onFrame(ir) }, handler)
        state.value = true
        return START_NOT_STICKY
    }

    private fun onFrame(r: ImageReader) {
        val img = r.acquireLatestImage() ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            val cb = sink
            if (cb == null || now - last < 1000) return
            last = now
            val plane = img.planes[0]
            val pad = (plane.rowStride - plane.pixelStride * img.width) / plane.pixelStride
            val bmp = Bitmap.createBitmap(img.width + pad, img.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 55, out)
            if (cropped !== bmp) cropped.recycle()
            bmp.recycle()
            cb(out.toByteArray())
        } catch (_: Exception) {
        } finally {
            img.close()
        }
    }

    override fun onDestroy() {
        state.value = false
        sink = null
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        runCatching { thread?.quitSafely() }
        super.onDestroy()
    }
}
