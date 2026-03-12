package com.virtualcam.camera

import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import kotlinx.coroutines.*
import java.io.IOException

/**
 * Provides camera frames from a local photo or video.
 * Feeds frames into a [Surface] which is then used by the virtual camera.
 */
class CameraFrameProvider(private val context: Context) {

    enum class SourceType { NONE, PHOTO, VIDEO }

    private var sourceType = SourceType.NONE
    private var photoUri: Uri? = null
    private var videoUri: Uri? = null

    private var photoBitmap: Bitmap? = null
    private var mediaPlayer: MediaPlayer? = null

    private var outputSurface: Surface? = null
    private var frameJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Configure source ─────────────────────────────────────

    fun setPhotoSource(uri: Uri) {
        photoUri = uri
        videoUri = null
        sourceType = SourceType.PHOTO
    }

    fun setVideoSource(uri: Uri) {
        videoUri = uri
        photoUri = null
        sourceType = SourceType.VIDEO
    }

    fun clearSource() {
        sourceType = SourceType.NONE
        photoUri = null
        videoUri = null
    }

    // ── Start / Stop ─────────────────────────────────────────

    /**
     * Start streaming frames into [surface].
     * For photo: renders the bitmap at ~30 fps.
     * For video: uses MediaPlayer directly on the surface.
     */
    fun startStreaming(surface: Surface) {
        stopStreaming()
        outputSurface = surface

        when (sourceType) {
            SourceType.PHOTO -> startPhotoStream(surface)
            SourceType.VIDEO -> startVideoStream(surface)
            SourceType.NONE  -> {} // nothing to stream
        }
    }

    fun stopStreaming() {
        frameJob?.cancel()
        frameJob = null

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        photoBitmap?.recycle()
        photoBitmap = null

        outputSurface = null
    }

    // ── Photo streaming ───────────────────────────────────────

    private fun startPhotoStream(surface: Surface) {
        val uri = photoUri ?: return

        frameJob = scope.launch {
            // Decode bitmap once
            val bmp = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } ?: return@launch

            photoBitmap = bmp

            while (isActive) {
                try {
                    val canvas = surface.lockCanvas(null)
                    canvas?.let {
                        drawBitmapCentered(it, bmp)
                        surface.unlockCanvasAndPost(it)
                    }
                } catch (e: Exception) {
                    break
                }
                delay(33L) // ~30 fps
            }
        }
    }

    private fun drawBitmapCentered(canvas: Canvas, bmp: Bitmap) {
        canvas.drawColor(Color.BLACK)
        val matrix = Matrix()
        val scaleX = canvas.width.toFloat() / bmp.width
        val scaleY = canvas.height.toFloat() / bmp.height
        val scale = minOf(scaleX, scaleY)
        matrix.postScale(scale, scale)
        matrix.postTranslate(
            (canvas.width  - bmp.width  * scale) / 2f,
            (canvas.height - bmp.height * scale) / 2f
        )
        canvas.drawBitmap(bmp, matrix, null)
    }

    // ── Video streaming ───────────────────────────────────────

    private fun startVideoStream(surface: Surface) {
        val uri = videoUri ?: return

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(context, uri)
                setSurface(surface)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // ── Info ──────────────────────────────────────────────────

    val activeSourceType: SourceType get() = sourceType
    val isActive: Boolean get() = sourceType != SourceType.NONE
}
