package com.virtualcam.app.manager

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean
)

class SystemAppScanner(private val context: Context) {

    fun getInstalledApps(): List<InstalledApp> {

        val pm: PackageManager = context.packageManager

        val applications = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val result = mutableListOf<InstalledApp>()

        for (app in applications) {

            val label = pm.getApplicationLabel(app).toString()

            val icon = pm.getApplicationIcon(app)

            val pkg = app.packageName

            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            result.add(
                InstalledApp(
                    name = label,
                    packageName = pkg,
                    icon = icon,
                    isSystem = isSystem
                )
            )
        }

        result.sortBy { it.name.lowercase() }

        return result
    }
}
