package com.virtualcam.core

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import com.virtualcam.camera.CameraHook
import com.virtualcam.camera.CameraFrameProvider
import kotlinx.coroutines.*

class VirtualSpaceEngine private constructor(private val context: Context) {

    val frameProvider: CameraFrameProvider get() = CameraHook.getProvider()
        ?: CameraFrameProvider(context)

    private val virtualApps = mutableListOf<VirtualAppInfo>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        @Volatile private var instance: VirtualSpaceEngine? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: VirtualSpaceEngine(context.applicationContext).also { instance = it }
        }
    }

    fun init() {
        loadVirtualApps()
    }

    // ── Install app ke virtual space ──────────────────────────

    suspend fun installApp(apkPath: String): InstallResult = withContext(Dispatchers.IO) {
        try {
            // Coba via VirtualCore dulu
            val result = installViaVirtualCore(apkPath)
            if (result != null) return@withContext result

            // Fallback: baca info APK manual
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkPath, 0)
                ?: return@withContext InstallResult.Error("APK tidak valid")

            info.applicationInfo.sourceDir = apkPath
            info.applicationInfo.publicSourceDir = apkPath

            val icon: Drawable? = try { pm.getApplicationIcon(info.applicationInfo) } catch (e: Exception) { null }

            val app = VirtualAppInfo(
                packageName = info.packageName,
                appName = pm.getApplicationLabel(info.applicationInfo).toString(),
                apkPath = apkPath,
                icon = icon
            )
            addApp(app)
            InstallResult.Success(app)
        } catch (e: Exception) {
            InstallResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun installViaVirtualCore(apkPath: String): InstallResult? {
        return try {
            val vc = Class.forName("com.lody.virtual.client.core.VirtualCore")
            val get = vc.getMethod("get")
            val instance = get.invoke(null)

            // VirtualCore.get().installPackage(apkPath, flags)
            val installPkg = vc.getMethod("installPackage", String::class.java, Int::class.java)
            val result = installPkg.invoke(instance, apkPath, 0)

            // Parse InstallResult dari VirtualApp
            val resultClass = result?.javaClass ?: return null
            val isSuccess = resultClass.getField("isSuccess").getBoolean(result)

            if (!isSuccess) {
                val error = resultClass.getField("error").get(result)?.toString() ?: "install failed"
                return InstallResult.Error(error)
            }

            val packageName = resultClass.getField("packageName").get(result)?.toString()
                ?: return null

            val pm = context.packageManager
            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: Exception) { null }

            val app = VirtualAppInfo(
                packageName = packageName,
                appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName,
                apkPath = apkPath,
                icon = appInfo?.let { try { pm.getApplicationIcon(it) } catch (e: Exception) { null } }
            )
            addApp(app)
            Log.i("VirtualCam", "Installed via VirtualCore: $packageName")
            InstallResult.Success(app)
        } catch (e: Exception) {
            Log.w("VirtualCam", "VirtualCore install failed: ${e.message}")
            null
        }
    }

    // ── Launch app di dalam virtual space ─────────────────────

    fun launchApp(packageName: String): Boolean {
        // Coba launch via VirtualCore dulu
        if (launchViaVirtualCore(packageName)) return true

        // Fallback: launch normal
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) { false }
    }

    private fun launchViaVirtualCore(packageName: String): Boolean {
        return try {
            val vc = Class.forName("com.lody.virtual.client.core.VirtualCore")
            val get = vc.getMethod("get")
            val instance = get.invoke(null)

            // Cek apakah sudah diinstall di virtual space
            val isInstalled = vc.getMethod("isAppInstalled", String::class.java)
                .invoke(instance, packageName) as? Boolean ?: false

            if (!isInstalled) return false

            // VirtualCore.get().getLaunchIntent(packageName, userId)
            val getLaunchIntent = vc.getMethod("getLaunchIntent", String::class.java, Int::class.java)
            val intent = getLaunchIntent.invoke(instance, packageName, 0) as? Intent
                ?: return false

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i("VirtualCam", "Launched via VirtualCore: $packageName")
            true
        } catch (e: Exception) {
            Log.w("VirtualCam", "VirtualCore launch failed: ${e.message}")
            false
        }
    }

    // ── App management ────────────────────────────────────────

    fun addApp(app: VirtualAppInfo) {
        if (virtualApps.none { it.packageName == app.packageName })
            virtualApps.add(app)
    }

    fun uninstallApp(packageName: String) {
        try {
            val vc = Class.forName("com.lody.virtual.client.core.VirtualCore")
            val get = vc.getMethod("get")
            val instance = get.invoke(null)
            vc.getMethod("uninstallPackage", String::class.java, Int::class.java)
                .invoke(instance, packageName, 0)
        } catch (e: Exception) { /* VirtualCore tidak ada, hapus dari list saja */ }
        virtualApps.removeAll { it.packageName == packageName }
    }

    fun getInstalledApps(): List<VirtualAppInfo> = virtualApps.toList()

    // ── Camera control ────────────────────────────────────────

    fun setPhotoSource(uri: Uri) = CameraHook.setPhoto(uri)
    fun setVideoSource(uri: Uri) = CameraHook.setVideo(uri)
    fun useRealCamera() = CameraHook.setReal()

    // ── Internal ──────────────────────────────────────────────

    private fun loadVirtualApps() {
        // Load dari VirtualCore kalau ada
        try {
            val vc = Class.forName("com.lody.virtual.client.core.VirtualCore")
            val get = vc.getMethod("get")
            val instance = get.invoke(null)
            val getApps = vc.getMethod("getInstalledApps", Int::class.java)
            val list = getApps.invoke(instance, 0) as? List<*> ?: return
            val pm = context.packageManager
            list.forEach { pkg ->
                val pkgName = pkg?.javaClass?.getField("packageName")?.get(pkg)?.toString() ?: return@forEach
                val appInfo = try { pm.getApplicationInfo(pkgName, 0) } catch (e: Exception) { return@forEach }
                virtualApps.add(VirtualAppInfo(
                    packageName = pkgName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    apkPath = appInfo.sourceDir,
                    icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                ))
            }
        } catch (e: Exception) { /* VirtualCore tidak ada */ }
    }

    fun destroy() {
        scope.cancel()
        instance = null
    }
}

data class VirtualAppInfo(
    val packageName: String,
    val appName: String,
    val apkPath: String,
    val icon: android.graphics.drawable.Drawable? = null
)

sealed class InstallResult {
    data class Success(val app: VirtualAppInfo) : InstallResult()
    data class Error(val message: String) : InstallResult()
}
