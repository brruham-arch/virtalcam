package com.virtualcam.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.virtualcam.app.databinding.ActivityMainBinding
import com.virtualcam.app.ui.VirtualAppAdapter
import com.virtualcam.app.manager.MainViewModel
import com.virtualcam.camera.CameraFrameProvider
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: VirtualAppAdapter

    // File pickers
    private val pickApk = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val path = getRealPathFromUri(uri) ?: run {
            toast("Tidak bisa baca path APK")
            return@registerForActivityResult
        }
        lifecycleScope.launch { viewModel.installApp(path) }
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.setPhotoSource(uri)
        updateCameraSourceBadge(CameraFrameProvider.SourceType.PHOTO)
    }

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.setVideoSource(uri)
        updateCameraSourceBadge(CameraFrameProvider.SourceType.VIDEO)
    }

    // ── Lifecycle ─────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupCameraSourcePanel()
        setupFab()
        observeViewModel()
        checkPermissions()

        viewModel.init(this)
    }

    // ── Toolbar ───────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "VirtualCam"
    }

    // ── RecyclerView ──────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = VirtualAppAdapter(
            onLaunch   = { app -> viewModel.launchApp(app.packageName) },
            onUninstall= { app -> viewModel.uninstallApp(app.packageName) }
        )
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    // ── Camera source panel ───────────────────────────────────

    private fun setupCameraSourcePanel() {
        binding.btnSourceReal.setOnClickListener {
            viewModel.useRealCamera()
            updateCameraSourceBadge(CameraFrameProvider.SourceType.NONE)
        }
        binding.btnSourcePhoto.setOnClickListener { pickPhoto.launch("image/*") }
        binding.btnSourceVideo.setOnClickListener { pickVideo.launch("video/*") }
    }

    private fun updateCameraSourceBadge(type: CameraFrameProvider.SourceType) {
        val (icon, label, color) = when (type) {
            CameraFrameProvider.SourceType.NONE  -> Triple("●", "Real Camera", getColor(R.color.green))
            CameraFrameProvider.SourceType.PHOTO -> Triple("▣", "Photo Spoof", getColor(R.color.amber))
            CameraFrameProvider.SourceType.VIDEO -> Triple("▶", "Video Spoof", getColor(R.color.red))
        }
        binding.tvSourceIcon.text  = icon
        binding.tvSourceLabel.text = label
        binding.tvSourceIcon.setTextColor(color)

        // highlight active button
        listOf(binding.btnSourceReal, binding.btnSourcePhoto, binding.btnSourceVideo)
            .forEach { it.alpha = 0.45f }
        when (type) {
            CameraFrameProvider.SourceType.NONE  -> binding.btnSourceReal.alpha  = 1f
            CameraFrameProvider.SourceType.PHOTO -> binding.btnSourcePhoto.alpha = 1f
            CameraFrameProvider.SourceType.VIDEO -> binding.btnSourceVideo.alpha = 1f
        }
    }

    // ── FAB ───────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabAddApp.setOnClickListener {
            pickApk.launch("application/vnd.android.package-archive")
        }
    }

    // ── Observe ───────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.apps.observe(this) { apps ->
            adapter.submitList(apps)
            binding.tvEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.message.observe(this) { msg ->
            msg?.let { toast(it); viewModel.clearMessage() }
        }

        viewModel.loading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    // ── Permissions ───────────────────────────────────────────

    private fun checkPermissions() {
        val needed = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }

    // ── Utils ─────────────────────────────────────────────────

    private fun getRealPathFromUri(uri: Uri): String? {
        // Try content resolver path
        val cursor = contentResolver.query(uri, arrayOf("_data"), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex("_data")
                if (idx >= 0) return it.getString(idx)
            }
        }
        // Fallback to Uri path
        return uri.path
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
