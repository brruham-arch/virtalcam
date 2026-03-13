package com.virtualcam.app

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.virtualcam.app.manager.InstalledApp
import com.virtualcam.app.manager.MainViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar

    private var installedApps: List<InstalledApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.app_list)

        progressBar = findViewById(R.id.loading_bar)

        observeViewModel()

        listView.setOnItemClickListener { _, _, position, _ ->

            val app = installedApps[position]

            viewModel.selectApp(app)

            Toast.makeText(
                this,
                "Selected: ${app.name}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeViewModel() {

        viewModel.apps.observe(this) { apps ->

            installedApps = apps

            val names = apps.map { it.name }

            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                names
            )

            listView.adapter = adapter
        }

        viewModel.isLoading.observe(this) { loading ->

            if (loading) {

                progressBar.visibility = View.VISIBLE

            } else {

                progressBar.visibility = View.GONE
            }
        }

        viewModel.message.observe(this) { msg ->

            msg?.let {

                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()

                viewModel.clearMessage()
            }
        }
    }
}
