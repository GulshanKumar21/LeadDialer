package com.adyapan.leaddialer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.*
import android.view.animation.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private lateinit var viewModel: LeadViewModel

    private lateinit var bannerImage: ImageView
    private lateinit var tvAnimated: TextView
    private lateinit var tvAnimated2: TextView
    private lateinit var textContainer: LinearLayout

    private var tvHeroTotal: TextView? = null
    private var tvHeroConnected: TextView? = null
    private var tvHeroHot: TextView? = null
    private var tvHeroSales: TextView? = null
    private var progressGoal: ProgressBar? = null
    private var tvProgressLabel: TextView? = null
    private var tvProgressPercent: TextView? = null
    private var tvHeroInitials: TextView? = null

    private var totdListener: ListenerRegistration? = null
    private var adminTargetListener: com.google.firebase.database.ValueEventListener? = null

    private var cachedTotal = 0
    private var cachedConnected = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_dashboard,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        val factory = LeadViewModelFactory(
            requireActivity().application
        )

        viewModel = ViewModelProvider(
            requireActivity(),
            factory
        )[LeadViewModel::class.java]

        initViews(view)
        startIntroAnimation()
        setupGreeting(view)
        setupStatsObservers(view)
        animateCardEntrance(view)
        observeThoughtOfTheDay(view)
    }

    private fun initViews(view: View) {

        // Banner
        bannerImage = view.findViewById(R.id.bannerImage)
        tvAnimated = view.findViewById(R.id.tvAnimated)
        tvAnimated2 = view.findViewById(R.id.tvAnimated2)
        textContainer = view.findViewById(R.id.textContainer)

        // Stats
        tvHeroTotal = view.findViewById(R.id.tvHeroTotal)
        tvHeroConnected = view.findViewById(R.id.tvHeroConnected)
        tvHeroHot = view.findViewById(R.id.tvHeroHot)
        tvHeroSales = view.findViewById(R.id.tvHeroSales)

        progressGoal = view.findViewById(R.id.progressGoal)

        tvProgressLabel = view.findViewById(R.id.tvProgressLabel)

        tvProgressPercent = view.findViewById(R.id.tvProgressPercent)
    }

    private fun startIntroAnimation() {

        val text = "ADYAPANSCHOOL"

        // Reset states
        tvAnimated.text = ""
        tvAnimated.alpha = 0f

        tvAnimated2.alpha = 0f

        textContainer.alpha = 1f
        textContainer.scaleX = 0.92f
        textContainer.scaleY = 0.92f

        bannerImage.alpha = 0f

        // Smooth intro card animation
        textContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setInterpolator(OvershootInterpolator(0.5f))
            .start()

        typeText(text, 0)
    }

    private fun typeText(
        fullText: String,
        index: Int
    ) {

        if (index <= fullText.length) {

            tvAnimated.text =
                fullText.substring(0, index)

            // Smooth typing effect
            tvAnimated.alpha = 0.7f
            tvAnimated.translationY = 8f

            tvAnimated.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // Subtitle reveal
            if (index > 5) {
                tvAnimated2.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .start()
            }

            // SINGLE delay only
            tvAnimated.postDelayed({

                typeText(fullText, index + 1)

            }, 120)

        } else {

            // Text completed
            tvAnimated.postDelayed({

                textContainer.animate()
                    .alpha(0f)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(700)
                    .setInterpolator(
                        AccelerateDecelerateInterpolator()
                    )
                    .withEndAction {

                        // Banner fade in
                        bannerImage.animate()
                            .alpha(1f)
                            .setDuration(1400)
                            .setInterpolator(
                                DecelerateInterpolator()
                            )
                            .withEndAction {

                                startSoftBannerAnimation()

                                // Keep banner visible
                                bannerImage.postDelayed({

                                    bannerImage.animate()
                                        .alpha(0f)
                                        .setDuration(1000)
                                        .withEndAction {

                                            // Restart cycle
                                            startIntroAnimation()

                                        }
                                        .start()

                                }, 240000)

                            }
                            .start()
                    }
                    .start()

            }, 500)
        }
    }

    private fun setupGreeting(view: View) {

        val hour = Calendar.getInstance()
            .get(Calendar.HOUR_OF_DAY)

        val greeting = when (hour) {

            in 0..11 -> "Good Morning, ✨"

            in 12..16 -> "Good Afternoon, ☀️"

            in 17..20 -> "Good Evening, 🌤️"

            else -> "Good Night, 🌙"
        }

        val user =
            com.google.firebase.auth.FirebaseAuth
                .getInstance()
                .currentUser

        val email = user?.email ?: "user@gmail.com"

        val namePart = email.substringBefore("@")

        val cleanName = namePart
            .replace(Regex("[^a-zA-Z]"), "")
            .lowercase()
            .replaceFirstChar { it.titlecase() }

        val displayName =
            if (cleanName.isNotEmpty())
                cleanName
            else
                "User"

        view.findViewById<TextView>(R.id.tvGreeting)
            .apply {
                text = greeting
                alpha = 0f
                translationY = 40f

                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(700)
                    .setStartDelay(100)
                    .setInterpolator(DecelerateInterpolator(1.3f))
                    .start()
            }

        view.findViewById<TextView>(R.id.tvUserName)
            .apply {

                text = "$displayName 👋"

                alpha = 0f

                translationY = 40f

                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(700)
                    .setStartDelay(200)
                    .setInterpolator(DecelerateInterpolator(1.3f))
                    .start()
            }

        tvHeroInitials?.text =
            if (displayName.length >= 2)
                displayName.take(2).uppercase()
            else
                displayName.uppercase()
    }

    private fun setupStatsObservers(view: View) {

        val tvTotalLeads =
            view.findViewById<TextView>(R.id.tvTotalLeads)

        val tvPending =
            view.findViewById<TextView>(R.id.tvPending)

        val tvConnected =
            view.findViewById<TextView>(R.id.tvConnected)

        val tvInterested =
            view.findViewById<TextView>(R.id.tvInterested)

        viewModel.refreshStats()

        viewModel.totalLeads.observe(
            viewLifecycleOwner
        ) { total ->

            cachedTotal = total

            animateCounter(tvTotalLeads, total)

            tvHeroTotal?.text = total.toString()

            updateGoalCard()
        }

        viewModel.totalPending.observe(
            viewLifecycleOwner
        ) {

            animateCounter(tvPending, it)
        }

        viewModel.totalConnected.observe(
            viewLifecycleOwner
        ) { connected ->

            cachedConnected = connected

            animateCounter(tvConnected, connected)

            tvHeroConnected?.text =
                connected.toString()

            updateGoalCard()
        }

        viewModel.allLeads.observe(
            viewLifecycleOwner
        ) { leads ->

            // Busy count — shown in place of old Hot Lead card
            val busyCount = leads.count { it.status == "Busy" }
            tvInterested.text = busyCount.toString()
            tvHeroHot?.text   = busyCount.toString()

            // ── Sales Done count ──
            val salesCount = leads.count { it.salesDone }
            view.findViewById<TextView>(R.id.tvSalesDone)?.let {
                animateCounter(it, salesCount)
            }
            tvHeroSales?.text = salesCount.toString()
        }

        // ── Expected Sales Setup ──
        val tvExpectedSales = view.findViewById<TextView>(R.id.tvExpectedSales)
        val btnEditExpectedSales = view.findViewById<android.widget.ImageButton>(R.id.btnEditExpectedSales)

        val todayDateStr = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            lifecycleScope.launch {
                FirestoreSource.expectedSalesFlow(uid, todayDateStr).collect { sales ->
                    tvExpectedSales?.text = sales.toString()
                }
            }

            // ── Admin Target Setup ──
            val tvAdminTarget = view.findViewById<TextView>(R.id.tvAdminTargetDashboard)
            val adminTargetRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("adminTargets").child(uid).child("target")
            
            adminTargetListener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val target = (snapshot.value as? Number)?.toInt() ?: 0
                    if (target > 0) {
                        tvAdminTarget?.text = target.toString()
                    } else {
                        tvAdminTarget?.text = "Not Set"
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            }
            adminTargetRef.addValueEventListener(adminTargetListener!!)
        }

        btnEditExpectedSales?.setOnClickListener {
            val input = android.widget.EditText(requireContext())
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            val currentVal = tvExpectedSales?.text.toString()
            if (currentVal.isNotEmpty() && currentVal != "0") {
                input.setText(currentVal)
            }

            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Expected Sales")
                .setMessage("Enter your expected sales for today:")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newVal = input.text.toString().toIntOrNull() ?: 0
                    lifecycleScope.launch {
                        FirestoreSource.saveExpectedSales(todayDateStr, newVal)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Fix: Bypass ScrollView delay for instant 3D animation ──
        val touchListener = android.view.View.OnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> v.isPressed = true
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.isPressed = false
            }
            false
        }

        val cardInterested = view.findViewById<android.view.View>(R.id.cardInterested)
        val cardSalesDone  = view.findViewById<android.view.View>(R.id.cardSalesDone)
        val cardTotalLeads = view.findViewById<android.view.View>(R.id.cardTotalLeads)
        val cardConnected  = view.findViewById<android.view.View>(R.id.cardConnected)
        val cardPending    = view.findViewById<android.view.View>(R.id.cardPending)

        cardInterested?.setOnTouchListener(touchListener)
        cardSalesDone?.setOnTouchListener(touchListener)
        cardTotalLeads?.setOnTouchListener(touchListener)
        cardConnected?.setOnTouchListener(touchListener)
        cardPending?.setOnTouchListener(touchListener)

        // ── Make cards clickable ─────────────────────────────────
        cardInterested?.setOnClickListener {
            (requireActivity() as? MainActivity)?.loadFragmentWithBack(
                FilteredLeadsFragment.newInstance("Busy"),
                "📵 Busy"
            )
        }

        cardSalesDone?.setOnClickListener {
            (requireActivity() as? MainActivity)?.loadFragmentWithBack(
                FilteredLeadsFragment.newInstance("SalesDone"),
                "💰 Sales Done"
            )
        }

        cardTotalLeads?.setOnClickListener {
            (requireActivity() as? MainActivity)?.loadFragmentWithBack(
                FilteredLeadsFragment.newInstance("Total Leads"),
                "👥 Total Leads"
            )
        }

        cardConnected?.setOnClickListener {
            (requireActivity() as? MainActivity)?.loadFragmentWithBack(
                FilteredLeadsFragment.newInstance("Connected"),
                "📞 Connected"
            )
        }

        cardPending?.setOnClickListener {
            (requireActivity() as? MainActivity)?.loadFragmentWithBack(
                FilteredLeadsFragment.newInstance("Pending"),
                "🕐 Pending"
            )
        }
    }

    private fun updateGoalCard() {

        val total = cachedTotal

        val connected = cachedConnected

        val percent =
            if (total > 0)
                (connected * 100 / total)
            else
                0

        // Animate progress bar smoothly
        progressGoal?.let { progress ->
            ObjectAnimator.ofInt(
                progress,
                "progress",
                progress.progress,
                percent
            ).apply {
                duration = 800
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        tvProgressPercent?.text = "$percent%"

        tvProgressLabel?.text =
            "$connected / $total leads called"
    }

    private fun animateCounter(
        textView: TextView,
        targetValue: Int
    ) {

        val current =
            textView.text.toString()
                .toIntOrNull() ?: 0

        ValueAnimator.ofInt(
            current,
            targetValue
        ).apply {

            duration = 1200
            interpolator = DecelerateInterpolator(1.5f)

            addUpdateListener {

                textView.text =
                    (it.animatedValue as Int)
                        .toString()
            }

            start()
        }
    }

    private fun animateCardEntrance(view: View) {

        listOf(

            view.findViewById<View>(R.id.cardTotalLeads),

            view.findViewById<View>(R.id.cardPending),

            view.findViewById<View>(R.id.cardConnected),

            view.findViewById<View>(R.id.cardInterested),

            view.findViewById<View>(R.id.cardSalesDone)

        ).forEachIndexed { index, card ->

            card?.apply {

                alpha = 0f

                translationY = 80f

                scaleX = 0.95f
                scaleY = 0.95f

                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(600)
                    .setStartDelay(
                        400L + (index * 120L)
                    )
                    .setInterpolator(OvershootInterpolator(0.5f))
                    .start()
            }
        }
    }

    // ── Real-time Thought of the Day from Firestore ───────────────────────────
    private fun observeThoughtOfTheDay(view: View) {
        val tvThought = view.findViewById<TextView>(R.id.tvThought) ?: return
        val tvAuthor  = view.findViewById<TextView>(R.id.tvThoughtAuthor)

        totdListener = FirebaseFirestore.getInstance()
            .collection("settings")
            .document("thoughtOfTheDay")
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val text   = snap.getString("text")   ?: return@addSnapshotListener
                    val author = snap.getString("author") ?: "Adyapan Team"
                    tvThought.text = "\"$text\""
                    tvAuthor?.text = "— $author"
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        totdListener?.remove()
        
        adminTargetListener?.let {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("adminTargets").child(uid).child("target").removeEventListener(it)
            }
        }
        adminTargetListener = null
    }

    override fun onResume() {
        super.onResume()
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Dashboard"
    }

    private fun startSoftBannerAnimation() {

        val scaleX = ObjectAnimator.ofFloat(
            bannerImage,
            "scaleX",
            1f,
            1.015f,
            1f
        )

        val scaleY = ObjectAnimator.ofFloat(
            bannerImage,
            "scaleY",
            1f,
            1.015f,
            1f
        )

        scaleX.duration = 4000
        scaleY.duration = 4000

        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatCount = ValueAnimator.INFINITE

        scaleX.interpolator =
            AccelerateDecelerateInterpolator()

        scaleY.interpolator =
            AccelerateDecelerateInterpolator()

        scaleX.start()
        scaleY.start()
    }
}