package com.virtualcam.camera

import android.content.Context
import android.hardware.camera2.CameraDevice
import android.net.Uri
import android.os.Handler

object CameraHook {

    @Volatile var photoUri: Uri? = null
    @Volatile var videoUri: Uri? = null
    @Volatile var mode: Mode = Mode.REAL

    enum class Mode { REAL, PHOTO, VIDEO }

    private var provider: CameraFrameProvider? = null

    fun install(context: Context) {
        provider = CameraFrameProvider(context)
        hookCameraManager(context)
    }

    fun setPhoto(uri: Uri) {
        photoUri = uri
        mode = Mode.PHOTO
        provider?.setPhotoSource(uri)
    }

    fun setVideo(uri: Uri) {
        videoUri = uri
        mode = Mode.VIDEO
        provider?.setVideoSource(uri)
    }

    fun setReal() {
        mode = Mode.REAL
        provider?.clearSource()
    }

    fun getProvider(): CameraFrameProvider? = provider

    private fun hookCameraManager(context: Context) {
        try {

            val openCameraHook = object : Any() {
                fun beforeCall(cameraId: String, callback: CameraDevice.StateCallback, handler: Handler?): Boolean {
                    if (mode == Mode.REAL) return false
                    val fakeCallback = FakeCameraStateCallback(callback, provider!!)
                    return true
                }
            }

            registerCameraServiceHook()

        } catch (e: Exception) {
            VirtualCameraHook.init(context, provider!!)
        }
    }

    private fun registerCameraServiceHook() {
        try {

            val hookProxiesClass = Class.forName(
                "com.lody.virtual.client.hook.proxies.CameraServiceProxy"
            )

            val instance = hookProxiesClass.getDeclaredField("INSTANCE")
                .apply { isAccessible = true }
                .get(null)

            hookProxiesClass.getDeclaredMethod(
                "setCameraFrameProvider",
                CameraFrameProvider::class.java
            )
                .apply { isAccessible = true }
                .invoke(instance, provider)

        } catch (e: Exception) {
            injectCameraProxyManually()
        }
    }

    private fun injectCameraProxyManually() {
        try {

            val hookManagerClass =
                Class.forName("com.lody.virtual.client.hook.base.HookManager")

            val getInstance = hookManagerClass.getMethod("getInstance")
            val manager = getInstance.invoke(null)

            val findHook = hookManagerClass.getMethod("findHook", String::class.java)
            val cameraHook = findHook.invoke(manager, "camera") ?: return

            val setProvider = cameraHook.javaClass.getDeclaredMethod(
                "setFrameProvider",
                CameraFrameProvider::class.java
            ).apply { isAccessible = true }

            setProvider.invoke(cameraHook, provider)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class FakeCameraStateCallback(
    private val real: CameraDevice.StateCallback,
    private val provider: CameraFrameProvider
) : CameraDevice.StateCallback() {

    override fun onOpened(camera: CameraDevice) {
        real.onOpened(camera)
    }

    override fun onDisconnected(camera: CameraDevice) =
        real.onDisconnected(camera)

    override fun onError(camera: CameraDevice, error: Int) =
        real.onError(camera, error)
}
