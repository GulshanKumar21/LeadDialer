package com.adyapan.leaddialer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

data class SlideItem(
    val imageResId: Int,
)

class SliderAdapter(private val context: Context) :
    RecyclerView.Adapter<SliderAdapter.SliderViewHolder>() {

    private val slides = mutableListOf<SlideItem>()

    fun setImages(imageResIds: List<Int>) {
        val defaultSlides = listOf(
            SlideItem(
                imageResId = imageResIds.getOrElse(0) { 0 },
            ),
            SlideItem(
                imageResId = imageResIds.getOrElse(1) { 0 },
            ),
            SlideItem(
                imageResId = imageResIds.getOrElse(2) { 0 },
            )
        )
        slides.clear()
        slides.addAll(defaultSlides.filter { it.imageResId != 0 })
        notifyDataSetChanged()
    }

    fun setSlides(newSlides: List<SlideItem>) {
        slides.clear()
        slides.addAll(newSlides)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SliderViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_slider, parent, false)
        return SliderViewHolder(view)
    }

    override fun onBindViewHolder(holder: SliderViewHolder, position: Int) {
        val slide = slides[position]
        Glide.with(context)
            .load(slide.imageResId)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(400))
            .into(holder.imageView)
    }

    override fun getItemCount(): Int = slides.size

    class SliderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.sliderImage)
    }
}