package com.virtualcam.camera

import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.view.Surface
import java.util.concurrent.Executor

/**
 * Fake CameraDevice yang membungkus kamera asli.
 *
 * Ketika target app panggil createCaptureSession(), kita:
 * 1. Ambil Surface yang dia minta
 * 2. Feed surface itu ke CameraFrameProvider (foto/video)
 * 3. Return fake CameraCaptureSession yang tidak bicara ke hardware
 *
 * Hasilnya: target app pikir kamera jalan normal,
 * tapi yang keluar adalah foto/video kita.
 */
class FakeCameraDevice(
    private val real: CameraDevice,
    private val provider: CameraFrameProvider
) : CameraDevice() {

    override fun getId(): String = real.id

    override fun createCaptureSession(
        outputs: List<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        // Ambil surface pertama (biasanya preview surface)
        val previewSurface = outputs.firstOrNull()
        if (previewSurface != null && provider.isActive) {
            // Feed frames ke surface target app
            provider.startStreaming(previewSurface)
            // Notify target app bahwa session siap
            val fakeSession = FakeCameraCaptureSession(this, previewSurface, provider)
            handler?.post { callback.onConfigured(fakeSession) }
                ?: callback.onConfigured(fakeSession)
        } else {
            // Mode real camera — pakai hardware asli
            real.createCaptureSession(outputs, callback, handler)
        }
    }

    override fun createCaptureSessionByOutputConfigurations(
        outputConfigurations: List<OutputConfiguration>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        val surfaces = outputConfigurations.mapNotNull {
            try { it.surface } catch (e: Exception) { null }
        }
        createCaptureSession(surfaces, callback, handler)
    }

    override fun createConstrainedHighSpeedCaptureSession(
        outputs: List<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) = createCaptureSession(outputs, callback, handler)

    override fun createCaptureRequest(templateType: Int): CaptureRequest.Builder =
        real.createCaptureRequest(templateType)

    override fun createReprocessCaptureRequest(inputResult: TotalCaptureResult): CaptureRequest.Builder =
        real.createReprocessCaptureRequest(inputResult)

    override fun close() {
        provider.stopStreaming()
        real.close()
    }

    override fun isSessionConfigurationSupported(sessionConfiguration: SessionConfiguration): Boolean = true

    override fun createCaptureSession(config: SessionConfiguration) {
        val surfaces = config.outputConfigurations.mapNotNull {
            try { it.surface } catch (e: Exception) { null }
        }
        if (surfaces.isNotEmpty() && provider.isActive) {
            provider.startStreaming(surfaces.first())
            val fakeSession = FakeCameraCaptureSession(this, surfaces.first(), provider)
            config.executor.execute { config.stateCallback.onConfigured(fakeSession) }
        } else {
            real.createCaptureSession(config)
        }
    }
}

/**
 * Fake CameraCaptureSession — menggantikan session kamera asli.
 * Target app tetap bisa setRepeatingRequest() dll tanpa error.
 */
class FakeCameraCaptureSession(
    private val device: CameraDevice,
    private val surface: Surface,
    private val provider: CameraFrameProvider
) : CameraCaptureSession() {

    override fun getDevice(): CameraDevice = device

    override fun capture(
        request: CaptureRequest,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int = 0

    override fun captureBurst(
        requests: List<CaptureRequest>,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int = 0

    override fun setRepeatingRequest(
        request: CaptureRequest,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int {
        // Provider sudah streaming, tidak perlu apa-apa lagi
        return 0
    }

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
    }

    override fun isReprocessable(): Boolean = false

    override fun getInputSurface(): Surface? = null
}
