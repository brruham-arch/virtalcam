package com.virtualcam.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.virtualcam.app.databinding.ItemVirtualAppBinding
import com.virtualcam.core.VirtualAppInfo

class VirtualAppAdapter(
    private val onLaunch: (VirtualAppInfo) -> Unit,
    private val onUninstall: (VirtualAppInfo) -> Unit
) : ListAdapter<VirtualAppInfo, VirtualAppAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVirtualAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val b: ItemVirtualAppBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(app: VirtualAppInfo) {
            b.tvAppName.text    = app.appName
            b.tvPackageName.text= app.packageName
            app.icon?.let { b.ivAppIcon.setImageDrawable(it) }
            b.btnLaunch.setOnClickListener    { onLaunch(app) }
            b.btnUninstall.setOnClickListener { onUninstall(app) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<VirtualAppInfo>() {
            override fun areItemsTheSame(a: VirtualAppInfo, b: VirtualAppInfo) =
                a.packageName == b.packageName
            override fun areContentsTheSame(a: VirtualAppInfo, b: VirtualAppInfo) = a == b
        }
    }
}
