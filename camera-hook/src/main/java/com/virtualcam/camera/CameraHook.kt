package com.virtualcam.camera

import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.os.Handler
import android.view.Surface
import java.util.concurrent.Executor

class FakeCameraDevice(
    private val real: CameraDevice,
    private val provider: CameraFrameProvider
) : CameraDevice() {

    override fun getId(): String {
        return real.id
    }

    override fun close() {
        try {
            provider.stopStreaming()
        } catch (e: Exception) {
        }
        try {
            real.close()
        } catch (e: Exception) {
        }
    }

    override fun createCaptureRequest(templateType: Int): CaptureRequest.Builder {
        return real.createCaptureRequest(templateType)
    }

    override fun createReprocessCaptureRequest(inputResult: TotalCaptureResult): CaptureRequest.Builder {
        return real.createReprocessCaptureRequest(inputResult)
    }

    override fun createCaptureSession(
        outputs: MutableList<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        FakeCameraDeviceHelper.interceptSession(
            real,
            outputs,
            callback,
            handler,
            provider
        )
    }

    override fun createCaptureSessionByOutputConfigurations(
        outputConfigs: MutableList<OutputConfiguration>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        val surfaces = outputConfigs.mapNotNull { it.surface }
        FakeCameraDeviceHelper.interceptSession(
            real,
            surfaces,
            callback,
            handler,
            provider
        )
    }

    override fun createReprocessableCaptureSession(
        inputConfig: InputConfiguration,
        outputs: MutableList<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        FakeCameraDeviceHelper.interceptSession(
            real,
            outputs,
            callback,
            handler,
            provider
        )
    }

    override fun createConstrainedHighSpeedCaptureSession(
        outputs: MutableList<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        FakeCameraDeviceHelper.interceptSession(
            real,
            outputs,
            callback,
            handler,
            provider
        )
    }

    override fun createCaptureSession(
        sessionConfiguration: SessionConfiguration
    ) {
        val surfaces = sessionConfiguration.outputConfigurations.mapNotNull { it.surface }

        FakeCameraDeviceHelper.interceptSession(
            real,
            surfaces,
            sessionConfiguration.stateCallback,
            null,
            provider
        )
    }

    override fun isSessionConfigurationSupported(sessionConfiguration: SessionConfiguration): Boolean {
        return try {
            real.isSessionConfigurationSupported(sessionConfiguration)
        } catch (e: Exception) {
            false
        }
    }
}

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

            realDevice.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(session: CameraCaptureSession) {
                        callback.onConfigured(
                            FakeCameraCaptureSession(session, provider)
                        )
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        callback.onConfigureFailed(session)
                    }

                },
                handler
            )

        } else {
            realDevice.createCaptureSession(surfaces, callback, handler)
        }
    }
}

class FakeCameraCaptureSession(
    private val real: CameraCaptureSession,
    private val provider: CameraFrameProvider
) : CameraCaptureSession() {

    override fun getDevice(): CameraDevice {
        return real.device
    }

    override fun capture(
        request: CaptureRequest,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int {
        return try {
            real.capture(request, listener, handler)
        } catch (e: Exception) {
            0
        }
    }

    override fun captureBurst(
        requests: MutableList<CaptureRequest>,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int {
        return try {
            real.captureBurst(requests, listener, handler)
        } catch (e: Exception) {
            0
        }
    }

    override fun setRepeatingRequest(
        request: CaptureRequest,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int {
        return try {
            real.setRepeatingRequest(request, listener, handler)
        } catch (e: Exception) {
            0
        }
    }

    override fun setRepeatingBurst(
        requests: MutableList<CaptureRequest>,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int {
        return try {
            real.setRepeatingBurst(requests, listener, handler)
        } catch (e: Exception) {
            0
        }
    }

    override fun stopRepeating() {
        try {
            real.stopRepeating()
        } catch (e: Exception) {
        }
    }

    override fun abortCaptures() {
        try {
            real.abortCaptures()
        } catch (e: Exception) {
        }
    }

    override fun prepare(surface: Surface) {
        try {
            real.prepare(surface)
        } catch (e: Exception) {
        }
    }

    override fun isReprocessable(): Boolean {
        return false
    }

    override fun getInputSurface(): Surface? {
        return null
    }

    override fun finalizeOutputConfigurations(
        outputConfigs: MutableList<OutputConfiguration>?
    ) {
        try {
            real.finalizeOutputConfigurations(outputConfigs)
        } catch (e: Exception) {
        }
    }

    override fun close() {
        try {
            provider.stopStreaming()
        } catch (e: Exception) {
        }

        try {
            real.close()
        } catch (e: Exception) {
        }
    }
}