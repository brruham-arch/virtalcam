package com.virtualcam.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex

class VirtualCamApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(base)
        // VirtualCore harus di-init di sini (sebelum Application.onCreate)
        // agar semua hook aktif sebelum apapun jalan
        try {
            val vc = Class.forName("com.lody.virtual.client.core.VirtualCore")
            val get = vc.getMethod("get")
            val instance = get.invoke(null)
            val startup = vc.getMethod("startup", Application::class.java, String::class.java)
            startup.invoke(instance, this, packageName)
            Log.i("VirtualCam", "VirtualCore.startup() OK")
        } catch (e: Exception) {
            Log.w("VirtualCam", "VirtualCore not available: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Install camera hook setelah VirtualCore ready
        try {
            com.virtualcam.camera.CameraHook.install(this)
            Log.i("VirtualCam", "CameraHook installed")
        } catch (e: Exception) {
            Log.e("VirtualCam", "CameraHook failed: ${e.message}")
        }
    }
}
