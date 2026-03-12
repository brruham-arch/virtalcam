package com.virtualcam.camera

import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.os.Handler
import android.view.Surface

object FakeCameraDeviceHelper {
    fun interceptSession(
        realDevice: CameraDevice,
        surfaces: List<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?,
        provider: CameraFrameProvider
    ) {
        if (provider.isActive && surfaces.isNotEmpty()) {
            provider.startStreaming(surfaces.first())
            realDevice.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    callback.onConfigured(FakeCameraCaptureSession(session, provider))
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    callback.onConfigureFailed(session)
                }
            }, handler)
        } else {
            realDevice.createCaptureSession(surfaces, callback, handler)
        }
    }
}

class FakeCameraCaptureSession(
    private val real: CameraCaptureSession,
    private val provider: CameraFrameProvider
) : CameraCaptureSession() {

    override fun getDevice(): CameraDevice = real.device
    override fun capture(request: CaptureRequest, listener: CaptureCallback?, handler: Handler?): Int = 0
    override fun captureBurst(requests: List<CaptureRequest>, listener: CaptureCallback?, handler: Handler?): Int = 0
    override fun setRepeatingRequest(request: CaptureRequest, listener: CaptureCallback?, handler: Handler?): Int = 0
    override fun setRepeatingBurst(requests: List<CaptureRequest>, listener: CaptureCallback?, handler: Handler?): Int = 0
    override fun stopRepeating() {}
    override fun abortCaptures() {}
    override fun prepare(target: Surface) {}
    override fun isReprocessable(): Boolean = false
    override fun getInputSurface(): Surface? = null
    override fun finalizeOutputConfigurations(outputConfigs: MutableList<OutputConfiguration>?) {}
    override fun close() {
        provider.stopStreaming()
        try { real.close() } catch (e: Exception) {}
    }
}
