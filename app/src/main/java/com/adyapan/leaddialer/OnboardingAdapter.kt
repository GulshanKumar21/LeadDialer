package com.adyapan.leaddialer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter(private val list: List<OnboardingItem>) :
    RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image      : ImageView = view.findViewById(R.id.image)
        val tvTitle    : TextView  = view.findViewById(R.id.tvOnboardTitle)
        val tvDesc     : TextView  = view.findViewById(R.id.tvOnboardDesc)
        val accentBar  : View      = view.findViewById(R.id.accentBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.image.setImageResource(item.image)
        holder.tvTitle.text = item.title
        holder.tvDesc.text  = item.description
        try {
            holder.accentBar.setBackgroundColor(Color.parseColor(item.accentColor))
        } catch (_: Exception) {}
    }
}