package androidx.directionalviewpager.demo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.directionalviewpager.demo.databinding.ActivityMainBinding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.DirectionalViewPager
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.max

class ViewPagerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var transformerMode = TransformerMode.NONE
    private var currentScrollState = DirectionalViewPager.SCROLL_STATE_IDLE
    private var currentPosition = 0
    private var currentOffsetPercent = 0
    private var fakeDragAnimator: ValueAnimator? = null
    private var overviewSummaryView: TextView? = null

    private enum class TransformerMode(
        val label: String,
    ) {
        NONE("None"),
        DEPTH("Depth"),
        ZOOM_OUT("Zoom-Out"),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        applySystemBarInsets()
        setupPager()
        setupControls()
        updateChromeState()
    }

    override fun onDestroy() {
        fakeDragAnimator?.cancel()
        super.onDestroy()
    }

    private fun applySystemBarInsets() {
        val bottomBarBaseTop = binding.bottomBar.paddingTop
        val bottomBarBaseBottom = binding.bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.setPadding(bars.left, bars.top, bars.right, 0)
            binding.bottomBar.setPadding(
                bars.left + dp(16),
                bottomBarBaseTop,
                bars.right + dp(16),
                bottomBarBaseBottom + bars.bottom,
            )
            insets
        }
    }

    private fun setupPager() {
        binding.pager.adapter = ShowcaseAdapter()
        applyPageMargin(enabled = true)

        binding.pager.addOnPageChangeListener(
            object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int,
                ) {
                    currentPosition = position
                    currentOffsetPercent = (positionOffset * 100).toInt()
                    updateTelemetry()
                }

                override fun onPageSelected(position: Int) {
                    currentPosition = position
                    updateTelemetry()
                    updateNavButtons()
                }

                override fun onPageScrollStateChanged(state: Int) {
                    currentScrollState = state
                    updateTelemetry()
                }
            },
        )
    }

    private fun setupControls() {
        binding.chipOrientation.setOnCheckedChangeListener { _, isChecked ->
            binding.pager.isHorizontal = isChecked
            updateChromeState()
        }

        binding.chipRtl.setOnCheckedChangeListener { _, isChecked ->
            binding.pager.layoutDirection =
                if (isChecked) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            updateChromeState()
        }

        binding.chipLock.setOnCheckedChangeListener { _, isChecked ->
            binding.pager.isUserInputEnabled = !isChecked
            updateChromeState()
        }

        binding.chipMargin.setOnCheckedChangeListener { _, isChecked ->
            applyPageMargin(enabled = isChecked)
        }

        binding.chipTransformer.setOnClickListener {
            val modes = TransformerMode.entries
            transformerMode = modes[(transformerMode.ordinal + 1) % modes.size]
            applyTransformer(transformerMode)
            updateChromeState()
        }

        binding.btnPrev.setOnClickListener {
            val cur = binding.pager.currentItem
            if (cur > 0) {
                binding.pager.setCurrentItem(cur - 1, true)
            }
        }

        binding.btnNext.setOnClickListener {
            val count = binding.pager.adapter?.count ?: 0
            val cur = binding.pager.currentItem
            if (cur < count - 1) {
                binding.pager.setCurrentItem(cur + 1, true)
            }
        }

        binding.btnFakeDrag.setOnClickListener {
            runFakeDrag()
        }
    }

    private fun applyPageMargin(enabled: Boolean) {
        if (enabled) {
            val dividerColor =
                MaterialColors.getColor(
                    binding.pager,
                    com.google.android.material.R.attr.colorOutlineVariant,
                )
            binding.pager.pageMargin = dp(24)
            binding.pager.setPageMarginDrawable(ColorDrawable(dividerColor))
        } else {
            binding.pager.pageMargin = 0
            binding.pager.setPageMarginDrawable(null)
        }
    }

    private fun applyTransformer(mode: TransformerMode) {
        when (mode) {
            TransformerMode.NONE -> {
                binding.pager.setPageTransformer(false, null)
            }

            TransformerMode.DEPTH -> {
                binding.pager.setPageTransformer(true, DirectionalDepthTransformer())
            }

            TransformerMode.ZOOM_OUT -> {
                binding.pager.setPageTransformer(false, DirectionalZoomOutTransformer())
            }
        }
    }

    private fun runFakeDrag() {
        if (binding.pager.isFakeDragging || fakeDragAnimator?.isRunning == true) return
        val count = binding.pager.adapter?.count ?: return
        val forward = binding.pager.currentItem < count - 1
        val pageSpan = if (binding.pager.isHorizontal) binding.pager.width else binding.pager.height
        if (pageSpan <= 0 || !binding.pager.beginFakeDrag()) return

        val sign =
            if (forward) {
                if (binding.pager.isHorizontal && binding.pager.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    1f
                } else {
                    -1f
                }
            } else {
                if (binding.pager.isHorizontal && binding.pager.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    -1f
                } else {
                    1f
                }
            }
        val totalDistance = sign * (pageSpan * 0.6f)
        var lastValue = 0f

        fakeDragAnimator =
            ValueAnimator.ofFloat(0f, totalDistance).apply {
                duration = 280L
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    if (!binding.pager.isFakeDragging) return@addUpdateListener
                    val value = anim.animatedValue as Float
                    binding.pager.fakeDragBy(value - lastValue)
                    lastValue = value
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (binding.pager.isFakeDragging) {
                                binding.pager.endFakeDrag()
                            }
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            if (binding.pager.isFakeDragging) {
                                binding.pager.endFakeDrag()
                            }
                        }
                    },
                )
                start()
            }
    }

    private fun updateChromeState() {
        val axisLabel =
            if (binding.pager.isHorizontal) {
                getString(R.string.chip_horizontal)
            } else {
                getString(R.string.chip_vertical)
            }
        val rtlLabel = if (binding.pager.layoutDirection == View.LAYOUT_DIRECTION_RTL) "RTL" else "LTR"
        val lockLabel = if (binding.pager.isUserInputEnabled) "Unlocked" else "Locked"

        binding.chipOrientation.text = axisLabel
        binding.chipTransformer.text = getString(R.string.chip_transformer_fmt, transformerMode.label)
        binding.toolbar.subtitle = getString(R.string.subtitle_fmt, axisLabel, rtlLabel, lockLabel)
        overviewSummaryView?.text = buildOverviewSummary(axisLabel, rtlLabel, lockLabel)
        updateTelemetry()
        updateNavButtons()
    }

    private fun updateTelemetry() {
        val count = binding.pager.adapter?.count ?: 0
        val stateName =
            when (currentScrollState) {
                DirectionalViewPager.SCROLL_STATE_DRAGGING -> "DRAGGING"
                DirectionalViewPager.SCROLL_STATE_SETTLING -> "SETTLING"
                else -> "IDLE"
            }
        binding.statusText.text =
            getString(
                R.string.status_fmt,
                currentPosition + 1,
                count,
                stateName,
                currentOffsetPercent,
            )
    }

    private fun updateNavButtons() {
        val count = binding.pager.adapter?.count ?: 0
        val cur = binding.pager.currentItem
        binding.btnPrev.isEnabled = cur > 0
        binding.btnNext.isEnabled = cur < count - 1
    }

    private fun buildOverviewSummary(
        axis: String,
        rtl: String,
        lock: String,
    ): String = "Active axis: $axis\nLayout direction: $rtl\nTouch input: $lock\nTransformer: ${transformerMode.label}"

    private fun dp(value: Int): Int =
        TypedValue
            .applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            ).toInt()

    private inner class DirectionalDepthTransformer : ViewPager.PageTransformer {
        override fun transformPage(
            page: View,
            position: Float,
        ) {
            val horizontal = binding.pager.isHorizontal
            page.pivotX = page.width / 2f
            page.pivotY = page.height / 2f
            when {
                position < -1f || position > 1f -> {
                    page.alpha = 0f
                }

                position <= 0f -> {
                    page.alpha = 1f
                    page.translationX = 0f
                    page.translationY = 0f
                    page.scaleX = 1f
                    page.scaleY = 1f
                }

                else -> {
                    page.alpha = 1f - position
                    if (horizontal) {
                        page.translationX = page.width * -position
                        page.translationY = 0f
                    } else {
                        page.translationX = 0f
                        page.translationY = page.height * -position
                    }
                    val scale = 0.85f + (1f - 0.85f) * (1f - abs(position))
                    page.scaleX = scale
                    page.scaleY = scale
                }
            }
        }
    }

    private inner class DirectionalZoomOutTransformer : ViewPager.PageTransformer {
        override fun transformPage(
            page: View,
            position: Float,
        ) {
            when {
                position < -1f || position > 1f -> {
                    page.alpha = 0f
                }

                else -> {
                    val scale = max(0.88f, 1f - abs(position))
                    val vertMargin = page.height * (1f - scale) / 2f
                    val horizMargin = page.width * (1f - scale) / 2f
                    if (binding.pager.isHorizontal) {
                        page.translationX = if (position < 0f) horizMargin - vertMargin / 2f else -horizMargin + vertMargin / 2f
                        page.translationY = 0f
                    } else {
                        page.translationX = 0f
                        page.translationY = if (position < 0f) vertMargin - horizMargin / 2f else -vertMargin + horizMargin / 2f
                    }
                    page.scaleX = scale
                    page.scaleY = scale
                    page.alpha = 0.5f + ((scale - 0.88f) / (1f - 0.88f)) * 0.5f
                }
            }
        }
    }

    private inner class ShowcaseAdapter : PagerAdapter() {
        override fun getCount(): Int = 5

        override fun instantiateItem(
            container: ViewGroup,
            position: Int,
        ): Any {
            val view =
                when (position) {
                    0 -> buildOverviewPage(container)
                    1 -> buildTransformerPage(container)
                    2 -> buildNestedScrollPage(container)
                    3 -> buildRecyclerPage(container)
                    else -> buildProgrammaticPage(container)
                }
            container.addView(view)
            return view
        }

        override fun destroyItem(
            container: ViewGroup,
            position: Int,
            `object`: Any,
        ) {
            val view = `object` as View
            if (position == 0) {
                overviewSummaryView = null
            }
            container.removeView(view)
        }

        override fun isViewFromObject(
            view: View,
            `object`: Any,
        ): Boolean = view === `object`

        private fun createCardShell(
            container: ViewGroup,
            title: String,
            body: String,
            tonal: Boolean = false,
            extraContent: ((LinearLayout) -> Unit)? = null,
        ): View {
            val ctx = container.context
            val card =
                MaterialCardView(ctx).apply {
                    layoutParams =
                        ViewGroup
                            .MarginLayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ).apply {
                                val m = dp(20)
                                setMargins(m, m, m, m)
                            }
                    radius = dp(24).toFloat()
                    if (tonal) {
                        setCardBackgroundColor(
                            MaterialColors.getColor(
                                this,
                                com.google.android.material.R.attr.colorPrimaryContainer,
                            ),
                        )
                        strokeWidth = 0
                    }
                }

            val content =
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    val p = dp(24)
                    setPadding(p, p, p, p)
                }

            val titleView =
                TextView(ctx).apply {
                    text = title
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
                    if (tonal) {
                        setTextColor(
                            MaterialColors.getColor(
                                this,
                                com.google.android.material.R.attr.colorOnPrimaryContainer,
                            ),
                        )
                    }
                }
            val bodyView =
                TextView(ctx).apply {
                    text = body
                    setPadding(0, dp(12), 0, dp(16))
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                    if (tonal) {
                        setTextColor(
                            MaterialColors.getColor(
                                this,
                                com.google.android.material.R.attr.colorOnPrimaryContainer,
                            ),
                        )
                    }
                }

            content.addView(titleView)
            content.addView(bodyView)
            extraContent?.invoke(content)
            card.addView(content)
            return card
        }

        private fun buildOverviewPage(container: ViewGroup): View =
            createCardShell(
                container = container,
                title = "Page 0 - Overview",
                body =
                    "Swipe or use the control chips above to switch axis, RTL layout direction, " +
                        "input lock, margin, and PageTransformer.",
                tonal = true,
            ) { root ->
                val summary =
                    TextView(root.context).apply {
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setTextColor(
                            MaterialColors.getColor(
                                this,
                                com.google.android.material.R.attr.colorOnPrimaryContainer,
                            ),
                        )
                    }
                overviewSummaryView = summary
                val axis =
                    if (binding.pager.isHorizontal) {
                        getString(R.string.chip_horizontal)
                    } else {
                        getString(R.string.chip_vertical)
                    }
                val rtl = if (binding.pager.layoutDirection == View.LAYOUT_DIRECTION_RTL) "RTL" else "LTR"
                val lock = if (binding.pager.isUserInputEnabled) "Unlocked" else "Locked"
                summary.text = buildOverviewSummary(axis, rtl, lock)
                root.addView(summary)
            }

        private fun buildTransformerPage(container: ViewGroup): View =
            createCardShell(
                container = container,
                title = "Page 1 - PageTransformer",
                body =
                    "Tap the FX chip to cycle None, Depth, and Zoom-Out transforms. " +
                        "Both adapt translation and scaling to vertical and horizontal paging.",
            )

        private fun buildNestedScrollPage(container: ViewGroup): View {
            val ctx = container.context
            return MaterialCardView(ctx).apply {
                layoutParams =
                    ViewGroup
                        .MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ).apply {
                            val m = dp(20)
                            setMargins(m, m, m, m)
                        }
                radius = dp(24).toFloat()

                val scrollView =
                    NestedScrollView(ctx).apply {
                        val textView =
                            TextView(ctx).apply {
                                val p = dp(20)
                                setPadding(p, p, p, p)
                                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                                text =
                                    buildString {
                                        append("Page 2 - NestedScrollView\n\n")
                                        append(
                                            "Scroll to the top or bottom edge in vertical mode and keep dragging " +
                                                "to hand off to DirectionalViewPager.\n\n",
                                        )
                                        for (i in 1..28) {
                                            append("NestedScrollView line $i\n\n")
                                        }
                                    }
                            }
                        addView(textView)
                    }
                addView(scrollView)
            }
        }

        private fun buildRecyclerPage(container: ViewGroup): View {
            val ctx = container.context
            return MaterialCardView(ctx).apply {
                layoutParams =
                    ViewGroup
                        .MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ).apply {
                            val m = dp(20)
                            setMargins(m, m, m, m)
                        }
                radius = dp(24).toFloat()

                val recycler =
                    RecyclerView(ctx).apply {
                        layoutManager = LinearLayoutManager(ctx)
                        adapter =
                            object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                                override fun getItemCount(): Int = 30

                                override fun onCreateViewHolder(
                                    parent: ViewGroup,
                                    viewType: Int,
                                ): RecyclerView.ViewHolder {
                                    val row =
                                        TextView(parent.context).apply {
                                            layoutParams =
                                                RecyclerView.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                                )
                                            setPadding(dp(20), dp(14), dp(20), dp(14))
                                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                                        }
                                    return object : RecyclerView.ViewHolder(row) {}
                                }

                                override fun onBindViewHolder(
                                    holder: RecyclerView.ViewHolder,
                                    position: Int,
                                ) {
                                    (holder.itemView as TextView).text =
                                        if (position == 0) {
                                            "Page 3 - RecyclerView (Item 0)"
                                        } else {
                                            "RecyclerView item #$position"
                                        }
                                }
                            }
                    }
                addView(recycler)
            }
        }

        private fun buildProgrammaticPage(container: ViewGroup): View =
            createCardShell(
                container = container,
                title = "Page 4 - Programmatic & Fake Drag",
                body = "Lock touch input with the Lock Input chip and test Prev, Next, Fake Drag, or Jump to Page 0.",
                tonal = true,
            ) { root ->
                val jumpButton =
                    MaterialButton(root.context).apply {
                        text = getString(R.string.action_jump_first)
                        setOnClickListener {
                            binding.pager.setCurrentItem(0, true)
                        }
                    }
                root.addView(jumpButton)
            }
    }
}
