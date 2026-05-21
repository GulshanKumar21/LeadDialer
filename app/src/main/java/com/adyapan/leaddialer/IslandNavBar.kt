package com.adyapan.leaddialer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * IslandNavBar — a fully custom, floating "Dynamic Island"-inspired
 * bottom navigation bar. No BottomNavigationView under the hood.
 *
 * Items: Home | Leads | [CENTER: Call Log] | Calendar | Attendance
 *
 * Design:
 * - Floating pill shape with soft shadow
 * - A glowing orange "bubble" slides under the selected item
 * - Center item sits elevated (like a mini-FAB) with a pulsing glow ring
 * - Selection triggers springy scale + translate animations
 */
class IslandNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    interface OnNavItemSelectedListener {
        fun onNavItemSelected(itemId: Int)
    }

    var listener: OnNavItemSelectedListener? = null

    // Item IDs matching the existing menu ids
    companion object {
        val ITEM_HOME       = R.id.nav_home
        val ITEM_LEADS      = R.id.nav_lead
        val ITEM_CENTER     = R.id.nav_center
        val ITEM_CALENDAR   = R.id.nav_calendar
        val ITEM_ATTENDANCE = R.id.nav_attendance
    }

    private data class NavItem(
        val id: Int,
        val iconRes: Int,
        val label: String
    )

    private val navItems = listOf(
        NavItem(ITEM_HOME,       R.drawable.ic_dashboard,             "Home"),
        NavItem(ITEM_LEADS,      R.drawable.ic_leads,                 "Leads"),
        NavItem(ITEM_CENTER,     R.drawable.outline_call_log_24,      "Calls"),
        NavItem(ITEM_CALENDAR,   R.drawable.outline_calendar_month_24,"Calendar"),
        NavItem(ITEM_ATTENDANCE, R.drawable.ic_attendance_new,            "Attend")
    )

    private val itemViews = mutableListOf<View>()
    private var selectedIndex = 0

    // The sliding bubble indicator
    private lateinit var slidingBubble: View
    private lateinit var container: LinearLayout

    init {
        inflate(context, R.layout.widget_island_nav, this)
        container     = findViewById(R.id.islandNavContainer)
        slidingBubble = findViewById(R.id.slidingBubble)

        clipChildren  = false
        clipToPadding = false

        buildItems()
        selectIndex(0, animate = false)
    }

    private fun buildItems() {
        val inflater = LayoutInflater.from(context)

        navItems.forEachIndexed { index, item ->
            // Reused the standard normal nav item layout for all items to make them perfectly simple and uniform.
            val itemView = inflater.inflate(R.layout.widget_island_nav_item, container, false)

            val icon  = itemView.findViewById<ImageView>(R.id.navItemIcon)
            val label = itemView.findViewById<TextView>(R.id.navItemLabel)

            icon.setImageResource(item.iconRes)
            label?.text = item.label

            itemView.setOnClickListener {
                if (selectedIndex != index) {
                    selectIndex(index, animate = true)
                    listener?.onNavItemSelected(item.id)
                }
            }

            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            container.addView(itemView, lp)
            itemViews.add(itemView)
        }
    }

    private fun selectIndex(index: Int, animate: Boolean) {
        selectedIndex = index

        itemViews.forEachIndexed { i, view ->
            val icon      = view.findViewById<ImageView>(R.id.navItemIcon)
            val label     = view.findViewById<TextView>(R.id.navItemLabel)
            val highlight = view.findViewById<android.view.View>(R.id.iconHighlight)
            val isSelected = (i == index)

            // Reset scale/translation
            icon.scaleX = 1f
            icon.scaleY = 1f
            icon.translationY = 0f

            if (isSelected) {
                icon.alpha = 1f
                label?.alpha = 1f
                label?.setTextColor(0xFF2A2A35.toInt())
                icon.setColorFilter(0xFF2A2A35.toInt()) // brand carbon slate icon tint
                highlight?.setBackgroundResource(R.drawable.bg_nav_bubble_circle)
                highlight?.elevation = 6f
            } else {
                icon.alpha = 1f
                label?.alpha = 1f
                label?.setTextColor(0xFF9E9E9E.toInt())
                icon.setColorFilter(0xFF757575.toInt()) // simple grey icon tint
                highlight?.setBackgroundResource(0)
                highlight?.elevation = 0f
            }
        }

        // Hide sliding bubble as user wants simple effect
        slidingBubble.alpha = 0f
    }

    private fun slideBubbleTo(targetIndex: Int) {
        // Disabled per user request for simplicity
    }

    /** Call this to programmatically select an item (e.g., from Activity) */
    fun setSelectedItemId(itemId: Int) {
        val index = navItems.indexOfFirst { it.id == itemId }
        if (index >= 0) selectIndex(index, animate = true)
    }

    /** Start a looping pulse animation on the center item's glow ring */
    fun startCenterGlow() {
        // Safe no-op as the center item is now simple and uniform
    }
}
