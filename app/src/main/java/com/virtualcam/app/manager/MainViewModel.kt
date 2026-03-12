package com.virtualcam.app.manager

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.virtualcam.core.InstallResult
import com.virtualcam.core.SystemAppScanner
import com.virtualcam.core.VirtualAppInfo
import com.virtualcam.core.VirtualSpaceEngine
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _apps = MutableLiveData<List<VirtualAppInfo>>(emptyList())
    val apps: LiveData<List<VirtualAppInfo>> = _apps

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private lateinit var engine: VirtualSpaceEngine

    fun init(context: Context) {

        engine = VirtualSpaceEngine.get(context)

        engine.init()

        loadSystemApps()

    }

    fun loadSystemApps() {

        viewModelScope.launch {

            _apps.value = SystemAppScanner.getInstalledApps(getApplication())

        }

    }

    fun cloneApp(app: VirtualAppInfo) {

        viewModelScope.launch {

            _loading.value = true

            when (val result = engine.installApp(app.apkPath)) {

                is InstallResult.Success -> {

                    _message.value = "✓ ${result.app.appName} berhasil di clone"

                }

                is InstallResult.Error -> {

                    _message.value = "✗ ${result.message}"

                }

            }

            _loading.value = false

        }

    }

    fun launchApp(packageName: String) {

        val ok = engine.launchApp(packageName)

        if (!ok) _message.value = "Launch gagal"

    }

    fun setPhotoSource(uri: Uri) {

        engine.setPhotoSource(uri)

        _message.value = "📷 Kamera → foto"

    }

    fun setVideoSource(uri: Uri) {

        engine.setVideoSource(uri)

        _message.value = "🎬 Kamera → video"

    }

    fun useRealCamera() {

        engine.useRealCamera()

        _message.value = "Kamera asli aktif"

    }

    fun clearMessage() {

        _message.value = null

    }

}
