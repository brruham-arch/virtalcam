package com.virtualcam.app.manager

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()

    private val scanner = SystemAppScanner(context)

    val apps = MutableLiveData<List<InstalledApp>>()

    val selectedApp = MutableLiveData<InstalledApp?>()

    val isLoading = MutableLiveData<Boolean>()

    init {
        loadApps()
    }

    fun loadApps() {

        isLoading.postValue(true)

        viewModelScope.launch(Dispatchers.IO) {

            val result = scanner.getInstalledApps()

            apps.postValue(result)

            isLoading.postValue(false)
        }
    }

    fun selectApp(app: InstalledApp) {

        selectedApp.postValue(app)
    }

    fun refreshApps() {

        loadApps()
    }

    fun uninstallApp(packageName: String) {

        val intent = Intent(Intent.ACTION_DELETE)

        intent.data = Uri.parse("package:$packageName")

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        context.startActivity(intent)
    }
}
