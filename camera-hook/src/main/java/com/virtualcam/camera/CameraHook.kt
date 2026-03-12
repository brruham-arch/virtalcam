package com.virtualcam.camera

import android.content.Context
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler

/**
 * Camera hook yang terintegrasi dengan VirtualApp.
 *
 * VirtualApp punya sistem hook sendiri berbasis reflection + Binder proxy.
 * Kita daftarkan hook kamera di sini, lalu VirtualApp akan memanggil
 * intercept() setiap kali target app mencoba buka kamera.
 *
 * Cara daftarkannya: di VirtualCamApp.onCreate(), setelah VirtualCore.startup(),
 * panggil CameraHook.install(context).
 */
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

    /**
     * Hook CameraManager.openCamera() via VirtualApp hook system.
     *
     * VirtualApp menyediakan interface Hook yang bisa di-register.
     * Setiap kali target app panggil openCamera(), kita intercept
     * dan inject CameraFrameProvider sebagai outputnya.
     */
    private fun hookCameraManager(context: Context) {
        try {
            // Daftarkan hook via VirtualApp's MethodHook system
            // VirtualApp sudah intercept semua system service calls dari target app.
            // Kita tinggal pasang di lapisan camera service-nya.

            val hookClass = Class.forName("com.lody.virtual.client.hook.base.MethodProxy")
            val hookManager = Class.forName("com.lody.virtual.client.hook.base.HookManager")

            // Hook openCamera
            val openCameraHook = object : Any() {
                // Ini dipanggil VirtualApp setiap target app call openCamera()
                fun beforeCall(cameraId: String, callback: CameraDevice.StateCallback, handler: Handler?): Boolean {
                    if (mode == Mode.REAL) return false // lanjut ke kamera asli

                    // Inject fake camera device
                    val fakeCallback = FakeCameraStateCallback(callback, provider!!)
                    return true // block call asli, pakai fake
                }
            }

            // Register ke VirtualApp hook system
            // Actual call: VirtualCore.get().addHook(CameraServiceHook)
            registerCameraServiceHook()

        } catch (e: Exception) {
            // VirtualApp belum aktif atau versi berbeda
            // Fallback ke direct hook
            VirtualCameraHook.init(context, provider!!)
        }
    }

    private fun registerCameraServiceHook() {
        try {
            // VirtualApp hook registration
            // Path: com.lody.virtual.client.hook.proxies.CameraServiceProxy
            val hookProxiesClass = Class.forName(
                "com.lody.virtual.client.hook.proxies.CameraServiceProxy"
            )
            val instance = hookProxiesClass.getDeclaredField("INSTANCE")
                .apply { isAccessible = true }
                .get(null)

            // Set our provider
            hookProxiesClass.getDeclaredMethod("setCameraFrameProvider", CameraFrameProvider::class.java)
                .apply { isAccessible = true }
                .invoke(instance, provider)

        } catch (e: Exception) {
            // VirtualApp tidak punya CameraServiceProxy — buat sendiri via reflection
            injectCameraProxyManually()
        }
    }

    private fun injectCameraProxyManually() {
        try {
            // Ambil list hook yang sudah ada di VirtualApp
            val hookManagerClass = Class.forName("com.lody.virtual.client.hook.base.HookManager")
            val getInstance = hookManagerClass.getMethod("getInstance")
            val manager = getInstance.invoke(null)

            // Cari camera service hook
            val findHook = hookManagerClass.getMethod("findHook", String::class.java)
            val cameraHook = findHook.invoke(manager, "camera") ?: return

            // Inject provider ke camera hook
            val setProvider = cameraHook.javaClass.getDeclaredMethod(
                "setFrameProvider", CameraFrameProvider::class.java
            ).apply { isAccessible = true }
            setProvider.invoke(cameraHook, provider)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Fake CameraDevice.StateCallback yang intercept onOpened()
 * dan feed frames dari CameraFrameProvider ke surface target app.
 */
class FakeCameraStateCallback(
    private val real: CameraDevice.StateCallback,
    private val provider: CameraFrameProvider
) : CameraDevice.StateCallback() {

    override fun onOpened(camera: CameraDevice) {
        // Wrap camera device dengan proxy kita
        val fakeCam = FakeCameraDevice(camera, provider)
        real.onOpened(fakeCam)
    }

    override fun onDisconnected(camera: CameraDevice) = real.onDisconnected(camera)
    override fun onError(camera: CameraDevice, error: Int) = real.onError(camera, error)
}
