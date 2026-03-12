package com.virtualcam.app.manager

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.virtualcam.core.InstallResult
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
        refreshApps()
    }

    fun installApp(apkPath: String) {
        viewModelScope.launch {
            _loading.value = true
            when (val result = engine.installApp(apkPath)) {
                is InstallResult.Success -> {
                    _message.value = "✓ ${result.app.appName} berhasil dipasang"
                    refreshApps()
                }
                is InstallResult.Error -> {
                    _message.value = "✗ Gagal: ${result.message}"
                }
            }
            _loading.value = false
        }
    }

    fun uninstallApp(packageName: String) {
        engine.uninstallApp(packageName)
        refreshApps()
    }

    fun launchApp(packageName: String) {
        val ok = engine.launchApp(packageName)
        if (!ok) _message.value = "Tidak bisa launch app"
    }

    fun setPhotoSource(uri: Uri) {
        engine.setPhotoSource(uri)
        _message.value = "📷 Kamera → foto aktif"
    }

    fun setVideoSource(uri: Uri) {
        engine.setVideoSource(uri)
        _message.value = "🎬 Kamera → video aktif"
    }

    fun useRealCamera() {
        engine.useRealCamera()
        _message.value = "Kamera asli aktif"
    }

    fun clearMessage() { _message.value = null }

    private fun refreshApps() {
        _apps.value = engine.getInstalledApps()
    }

    override fun onCleared() {
        super.onCleared()
        engine.destroy()
    }
}
