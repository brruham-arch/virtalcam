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

    val message = MutableLiveData<String?>()

    val isLoading = MutableLiveData<Boolean>()

    init {
        loadApps()
    }

    fun loadApps() {

        isLoading.postValue(true)

        viewModelScope.launch(Dispatchers.IO) {

            try {

                val result = scanner.getInstalledApps()

                apps.postValue(result)

            } catch (e: Exception) {

                message.postValue(e.message ?: "Failed to load apps")
            }

            isLoading.postValue(false)
        }
    }

    fun selectApp(app: InstalledApp) {

        selectedApp.postValue(app)

        message.postValue("Selected: ${app.packageName}")
    }

    fun refreshApps() {

        loadApps()
    }

    fun uninstallApp(packageName: String) {

        try {

            val intent = Intent(Intent.ACTION_DELETE)

            intent.data = Uri.parse("package:$packageName")

            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            context.startActivity(intent)

        } catch (e: Exception) {

            message.postValue(e.message ?: "Uninstall failed")
        }
    }

    fun clearMessage() {

        message.postValue(null)
    }
}
