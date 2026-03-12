package com.virtualcam.camera

import android.hardware.camera2.*
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.view.Surface

/**
 * FakeCameraDevice - wrap CameraDevice asli tapi redirect output ke CameraFrameProvider.
 * Tidak extend CameraDevice langsung untuk menghindari abstract method issues.
 * Sebagai gantinya, kita hook di level createCaptureSession via reflection.
 */
object FakeCameraDeviceHelper {

    /**
     * Intercept createCaptureSession dari CameraDevice.
     * Dipanggil setelah onOpened() — kita ambil surface target app
     * dan feed provider ke sana.
     */
    fun interceptSession(
        realDevice: CameraDevice,
        surfaces: List<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?,
        provider: CameraFrameProvider
    ) {
        if (provider.isActive && surfaces.isNotEmpty()) {
            // Start streaming ke surface target app
            provider.startStreaming(surfaces.first())

            // Buat session via real device tapi dengan surface kita tetap
            realDevice.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    // Wrap session dengan fake yang block hardware capture
                    callback.onConfigured(FakeCameraCaptureSession(session, provider))
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    callback.onConfigureFailed(session)
                }
            }, handler)
        } else {
            // Real camera mode
            realDevice.createCaptureSession(surfaces, callback, handler)
        }
    }
}

/**
 * Fake session yang block setRepeatingRequest ke hardware
 * (karena kita sudah inject frame via provider).
 */
class FakeCameraCaptureSession(
    private val real: CameraCaptureSession,
    private val provider: CameraFrameProvider
) : CameraCaptureSession() {

    override fun getDevice(): CameraDevice = real.device

    override fun capture(
        request: CaptureRequest,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int = 0 // no-op, provider yang handle frame

    override fun captureBurst(
        requests: List<CaptureRequest>,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int = 0

    override fun setRepeatingRequest(
        request: CaptureRequest,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int = 0 // block — provider sudah streaming

    override fun setRepeatingBurst(
        requests: List<CaptureRequest>,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int = 0

    override fun stopRepeating() {}
    override fun abortCaptures() {}
    override fun prepare(target: Surface) {}

    override fun close() {
        provider.stopStreaming()
        try { real.close() } catch (e: Exception) {}
    }

    override fun isReprocessable(): Boolean = false
    override fun getInputSurface(): Surface? = null
}
