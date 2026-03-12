package com.virtualcam.app

import android.app.Application
import androidx.multidex.MultiDex
import com.virtualcam.camera.CameraHook

class VirtualCamApp : Application() {

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        CameraHook.install(this)
    }
}
