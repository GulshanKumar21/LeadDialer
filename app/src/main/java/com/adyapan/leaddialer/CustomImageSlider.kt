package com.adyapan.leaddialer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2

class CustomImageSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val viewPager: ViewPager2 = ViewPager2(context)
    private val adapter = SliderAdapter(context)
    private var onPageChanged: ((Int) -> Unit)? = null

    // Auto-scroll
    private val handler = Handler(Looper.getMainLooper())
    private var autoCycleRunnable: Runnable? = null
    private var autoCycleDelay: Long = 3500L
    private var isCycling = false

    init {
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 1

        // Apply page transformer for a subtle parallax/scale feel
        viewPager.setPageTransformer { page, position ->
            val absPos = Math.abs(position)
            page.alpha = 1 - absPos * 0.3f
            page.scaleY = 1 - absPos * 0.05f
        }

        addView(viewPager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                onPageChanged?.invoke(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                // Pause auto-scroll while user drags, resume after
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    pauseAutoCycle()
                } else if (state == ViewPager2.SCROLL_STATE_IDLE && isCycling) {
                    scheduleNext()
                }
            }
        })
    }

    fun setImageList(imageResIds: List<Int>) {
        adapter.setImages(imageResIds)
    }

    fun startAutoCycle(delayMs: Long = 3500L) {
        autoCycleDelay = delayMs
        isCycling = true
        scheduleNext()
    }

    fun stopAutoCycle() {
        isCycling = false
        autoCycleRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun pauseAutoCycle() {
        autoCycleRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun scheduleNext() {
        autoCycleRunnable?.let { handler.removeCallbacks(it) }
        autoCycleRunnable = Runnable {
            if (adapter.itemCount > 0) {
                val next = (viewPager.currentItem + 1) % adapter.itemCount
                viewPager.currentItem = next
            }
            if (isCycling) scheduleNext()
        }
        handler.postDelayed(autoCycleRunnable!!, autoCycleDelay)
    }

    fun hideDefaultIndicator() { /* No default indicator */ }

    var isUserInputEnabled: Boolean = true
        set(value) {
            field = value
            viewPager.isUserInputEnabled = value
        }

    fun setItemChangeListener(listener: (Int) -> Unit) {
        onPageChanged = listener
    }

    fun getCurrentPosition(): Int = viewPager.currentItem

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoCycle()
    }
}