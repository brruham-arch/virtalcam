package com.virtualcam.camera

import android.content.Context
import android.hardware.camera2.*
import android.os.Handler
import android.view.Surface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Hooks into Camera2 API to intercept [CameraManager.openCamera] calls
 * and replace the real camera output with [CameraFrameProvider].
 *
 * How it works inside VirtualApp:
 * 1. VirtualApp exposes a hook point via its IHookManager / MethodHook system.
 * 2. We intercept openCamera() and return a fake CameraDevice proxy.
 * 3. The fake device routes all surface targets to CameraFrameProvider.
 *
 * For standalone use (no VirtualApp), call [patchContext] before the
 * target app creates its camera manager. This uses dynamic proxy on
 * the system service fetched via reflection.
 */
object VirtualCameraHook {

    private var frameProvider: CameraFrameProvider? = null
    private var hooked = false

    // ── Init ─────────────────────────────────────────────────

    fun init(context: Context, provider: CameraFrameProvider) {
        frameProvider = provider
        if (!hooked) {
            patchCameraManager(context)
            hooked = true
        }
    }

    fun release() {
        frameProvider = null
        hooked = false
    }

    // ── Patch CameraManager via reflection + dynamic proxy ────

    private fun patchCameraManager(context: Context) {
        try {
            // Get the real CameraManager service binder
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val cameraBinder = getService.invoke(null, Context.CAMERA_SERVICE)

            // Get ICameraService stub
            val iCameraServiceClass = Class.forName("android.hardware.camera2.ICameraService")
            val asInterface = iCameraServiceClass
                .declaredClasses.firstOrNull { it.simpleName == "Stub" }
                ?.getMethod("asInterface", android.os.IBinder::class.java)

            val realService = asInterface?.invoke(null, cameraBinder) ?: return

            // Proxy ICameraService to intercept connectDevice
            val proxyService = Proxy.newProxyInstance(
                iCameraServiceClass.classLoader,
                arrayOf(iCameraServiceClass),
                CameraServiceProxy(realService)
            )

            // Inject proxy into CameraManager
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val mCameraServiceField = CameraManager::class.java
                .getDeclaredField("mCameraService")
                .apply { isAccessible = true }
            mCameraServiceField.set(manager, proxyService)

        } catch (e: Exception) {
            // Fallback: VirtualApp hook path will be used instead
            e.printStackTrace()
        }
    }

    // ── Camera service proxy ─────────────────────────────────

    private class CameraServiceProxy(private val real: Any) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            // Intercept connectDevice — this is called when openCamera() is triggered
            if (method.name == "connectDevice" || method.name == "connectLegacy") {
                return createFakeCameraDeviceProxy(args)
            }
            return method.invoke(real, *(args ?: emptyArray()))
        }

        private fun createFakeCameraDeviceProxy(args: Array<out Any>?): Any? {
            // Return a fake ICameraDeviceUser proxy
            return try {
                val iCameraDeviceClass = Class.forName("android.hardware.camera2.ICameraDeviceUser")
                Proxy.newProxyInstance(
                    iCameraDeviceClass.classLoader,
                    arrayOf(iCameraDeviceClass),
                    FakeCameraDeviceUser()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    // ── Fake camera device user (no-op + frame injection) ────

    private class FakeCameraDeviceUser : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            return when (method.name) {
                "submitRequest", "submitRequestList" -> 0L
                "cancelRequest" -> Unit
                "beginConfigure", "endConfigure" -> Unit
                "deleteStream" -> Unit
                "createStream" -> {
                    // Extract the Surface from the stream config and start feeding frames
                    val surface = extractSurface(args)
                    surface?.let { frameProvider?.startStreaming(it) }
                    0 // stream id
                }
                "disconnect" -> {
                    frameProvider?.stopStreaming()
                    Unit
                }
                "flush" -> Unit
                else -> defaultReturn(method.returnType)
            }
        }

        private fun extractSurface(args: Array<out Any>?): Surface? {
            args?.forEach { arg ->
                if (arg is Surface) return arg
                // OutputConfiguration wraps Surface
                try {
                    val getSurface = arg.javaClass.getMethod("getSurface")
                    val s = getSurface.invoke(arg)
                    if (s is Surface) return s
                } catch (_: Exception) {}
            }
            return null
        }

        private fun defaultReturn(type: Class<*>?): Any? = when (type) {
            Int::class.java, java.lang.Integer::class.java -> 0
            Long::class.java, java.lang.Long::class.java   -> 0L
            Boolean::class.java, java.lang.Boolean::class.java -> false
            else -> null
        }
    }

    // ── VirtualApp hook entry point ───────────────────────────
    // Called by VirtualApp's hook system (like Xposed but no root).
    // Register this in your VirtualApp HookProvider class:
    //
    //   HookBinder.hookService(Context.CAMERA_SERVICE, CameraServiceProxy(real))
    //
    // See VirtualSpaceEngine.kt for integration example.
}
