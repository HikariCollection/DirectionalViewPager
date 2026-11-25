/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.viewpager.widget

import android.content.Context
import android.content.res.Resources
import android.database.DataSetObserver
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.FocusFinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewParent
import android.view.accessibility.AccessibilityEvent
import android.view.animation.Interpolator
import android.widget.EdgeEffect
import android.widget.Scroller
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.view.AbsSavedState
import androidx.directionalviewpager.R
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import androidx.viewpager.widget.ViewPager.PageTransformer
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun PagerAdapter.getPageSize(position: Int): Float = getPageWidth(position)

/**
 * Layout manager that allows the user to flip left/right or up/down through pages of data.
 * You supply an implementation of a [PagerAdapter] to generate the pages that the view shows.
 */
open class DirectionalViewPager :
    ViewGroup,
    NestedScrollingChild3,
    NestedScrollingParent3 {
    internal class ItemInfo {
        var `object`: Any? = null
        var position: Int = 0
        var scrolling: Boolean = false
        var sizeFactor: Float = 0f
        var offset: Float = 0f
    }

    /**
     * Callback interface for responding to adapter changes.
     */
    fun interface OnAdapterChangeListener {
        fun onAdapterChanged(
            viewPager: DirectionalViewPager,
            oldAdapter: PagerAdapter?,
            newAdapter: PagerAdapter?,
        )
    }

    private var mExpectedAdapterCount: Int = 0
    internal val mItems = ArrayList<ItemInfo>()
    private val mTempItem = ItemInfo()
    private val mTempRect = Rect()

    internal var mAdapter: PagerAdapter? = null
    internal var mCurItem: Int = 0
    private var mRestoredCurItem: Int = -1
    private var mRestoredAdapterState: Parcelable? = null
    private var mRestoredClassLoader: ClassLoader? = null

    private lateinit var mScroller: Scroller
    private var mIsScrollStarted: Boolean = false
    private var mObserver: PagerObserver? = null

    private var mPageMargin: Int = 0
    private var mMarginDrawable: Drawable? = null
    private var mTopPageBounds: Int = 0
    private var mBottomPageBounds: Int = 0
    private var mLeftPageBounds: Int = 0
    private var mRightPageBounds: Int = 0

    internal var mFirstOffset: Float = -Float.MAX_VALUE
    internal var mLastOffset: Float = Float.MAX_VALUE

    private var mChildWidthMeasureSpec: Int = 0
    private var mChildHeightMeasureSpec: Int = 0
    private var mInLayout: Boolean = false
    private var mScrollingCacheEnabled: Boolean = false
    private var mPopulatePending: Boolean = false
    private var mOffscreenPageLimit: Int = DEFAULT_OFFSCREEN_PAGES

    private var mIsBeingDragged: Boolean = false
    private var mIsUnableToDrag: Boolean = false
    private var mDefaultGutterSize: Int = 0
    private var mGutterSize: Int = 0
    private var mTouchSlop: Int = 0

    private var mLastMotionX: Float = 0f
    private var mLastMotionY: Float = 0f
    private var mInitialMotionX: Float = 0f
    private var mInitialMotionY: Float = 0f
    private var mActivePointerId: Int = INVALID_POINTER

    private var mVelocityTracker: VelocityTracker? = null
    private var mMinimumVelocity: Int = 0
    private var mMaximumVelocity: Int = 0
    private var mFlingDistance: Int = 0
    private var mCloseEnough: Int = 0

    private var mFakeDragging: Boolean = false
    private var mFakeDragBeginTime: Long = 0

    private lateinit var mStartEdge: EdgeEffect
    private lateinit var mEndEdge: EdgeEffect

    private var mFirstLayout: Boolean = true
    private var mPendingScrollToCurrentItem: Boolean = false
    private var mCalledSuper: Boolean = false
    private var mDecorChildCount: Int = 0

    private var mOnPageChangeListeners: MutableList<OnPageChangeListener>? = null
    private var mOnPageChangeListener: OnPageChangeListener? = null
    private var mInternalPageChangeListener: OnPageChangeListener? = null
    private var mAdapterChangeListeners: MutableList<OnAdapterChangeListener>? = null
    private var mPageTransformer: PageTransformer? = null
    private var mPageTransformerLayerType: Int = 0

    private var mDrawingOrder: Int = DRAW_ORDER_DEFAULT
    private var mDrawingOrderedChildren: ArrayList<View>? = null
    private var mInitialized: Boolean = false
    private var mScrollState: Int = SCROLL_STATE_IDLE

    private val mChildHelper = NestedScrollingChildHelper(this)
    private val mParentHelper = NestedScrollingParentHelper(this)
    private val mScrollOffset = IntArray(2)
    private val mScrollConsumed = IntArray(2)
    private val mNestedConsumed = IntArray(2)
    private var mNestedOffsetX: Int = 0
    private var mNestedOffsetY: Int = 0
    private var mNestedScrollInProgress: Boolean = false

    var isUserInputEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!value && mIsBeingDragged) {
                scrollToItem(mCurItem, smoothScroll = true, velocity = 0, dispatchSelected = false)
                resetTouch()
            }
        }

    var isHorizontal: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!mInitialized) return

            mInitialMotionX = 0f
            mInitialMotionY = 0f
            mLastMotionX = 0f
            mLastMotionY = 0f
            mActivePointerId = INVALID_POINTER
            mIsBeingDragged = false
            mIsUnableToDrag = false
            mVelocityTracker?.clear()

            if (!mFirstLayout && mAdapter != null) {
                recalculateOffsetsForOrientationChange()
                populate()
            }
            scrollToItem(mCurItem, smoothScroll = false, velocity = 0, dispatchSelected = false)
            requestLayout()
        }

    private val mEndScrollRunnable =
        Runnable {
            setScrollState(SCROLL_STATE_IDLE)
            populate()
        }

    constructor(context: Context) : this(context, true)

    constructor(context: Context, isHorizontal: Boolean) : super(context) {
        this.isHorizontal = isHorizontal
        initViewPager(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.DirectionalViewPager, 0, 0)
        try {
            isHorizontal = a.getInt(R.styleable.DirectionalViewPager_android_orientation, 0) != 1
            isUserInputEnabled = a.getBoolean(R.styleable.DirectionalViewPager_userInputEnabled, true)
            initViewPager(context)
        } finally {
            a.recycle()
        }
    }

    private fun initViewPager(ctx: Context) {
        setWillNotDraw(false)
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        isFocusable = true
        isNestedScrollingEnabled = true

        mScroller = Scroller(ctx, sInterpolator)
        val configuration = ViewConfiguration.get(ctx)
        val density = ctx.resources?.displayMetrics?.density ?: 1f

        mTouchSlop = configuration?.scaledPagingTouchSlop ?: 0
        mMinimumVelocity = (MIN_FLING_VELOCITY * density).toInt()
        mMaximumVelocity = configuration?.scaledMaximumFlingVelocity ?: 0
        mStartEdge = EdgeEffect(ctx)
        mEndEdge = EdgeEffect(ctx)

        mFlingDistance = (MIN_DISTANCE_FOR_FLING * density).toInt()
        mCloseEnough = (CLOSE_ENOUGH * density).toInt()
        mDefaultGutterSize = (DEFAULT_GUTTER_SIZE * density).toInt()

        ViewCompat.setAccessibilityDelegate(this, MyAccessibilityDelegate())

        if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        @Suppress("DEPRECATION")
        ViewCompat.setOnApplyWindowInsetsListener(
            this,
            object : androidx.core.view.OnApplyWindowInsetsListener {
                private val mTempRect = Rect()

                override fun onApplyWindowInsets(
                    v: View,
                    originalInsets: WindowInsetsCompat,
                ): WindowInsetsCompat {
                    val applied = ViewCompat.onApplyWindowInsets(v, originalInsets)
                    if (applied.isConsumed) {
                        return applied
                    }

                    val res = mTempRect
                    res.left = applied.systemWindowInsetLeft
                    res.top = applied.systemWindowInsetTop
                    res.right = applied.systemWindowInsetRight
                    res.bottom = applied.systemWindowInsetBottom

                    for (i in 0 until childCount) {
                        val childInsets = ViewCompat.dispatchApplyWindowInsets(getChildAt(i), applied)
                        res.left = min(childInsets.systemWindowInsetLeft, res.left)
                        res.top = min(childInsets.systemWindowInsetTop, res.top)
                        res.right = min(childInsets.systemWindowInsetRight, res.right)
                        res.bottom = min(childInsets.systemWindowInsetBottom, res.bottom)
                    }

                    return applied.replaceSystemWindowInsets(res.left, res.top, res.right, res.bottom)
                }
            },
        )

        mInitialized = true
    }

    private fun recalculateOffsetsForOrientationChange() {
        val adapter = mAdapter ?: return
        val curInfo = infoForPosition(mCurItem) ?: return
        val clientSize = getClientSize()
        val marginOffset = if (clientSize > 0) mPageMargin.toFloat() / clientSize else 0f
        var offset = 0f
        for (pos in 0 until curInfo.position) {
            offset += adapter.getPageSize(pos) + marginOffset
        }
        curInfo.offset = offset
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(mEndScrollRunnable)
        if (this::mScroller.isInitialized && !mScroller.isFinished) {
            mScroller.abortAnimation()
        }
        super.onDetachedFromWindow()
    }

    internal fun setScrollState(newState: Int) {
        if (mScrollState == newState) return
        mScrollState = newState
        if (mPageTransformer != null) {
            enableLayers(newState != SCROLL_STATE_IDLE)
        }
        dispatchOnScrollStateChanged(newState)
    }

    var adapter: PagerAdapter?
        get() = mAdapter
        set(value) {
            val oldAdapter = mAdapter
            if (oldAdapter != null) {
                oldAdapter.setViewPagerObserver(null)
                oldAdapter.startUpdate(this)
                for (i in mItems.indices) {
                    val ii = mItems[i]
                    oldAdapter.destroyItem(this, ii.position, ii.`object`!!)
                }
                oldAdapter.finishUpdate(this)
                mItems.clear()
                removeNonDecorViews()
                mCurItem = 0
                scrollTo(0, 0)
            }

            mAdapter = value
            mExpectedAdapterCount = 0

            if (value != null) {
                if (mObserver == null) {
                    mObserver = PagerObserver()
                }
                value.setViewPagerObserver(mObserver)
                mPopulatePending = false
                val wasFirstLayout = mFirstLayout
                mFirstLayout = true
                mExpectedAdapterCount = value.count
                if (mRestoredCurItem >= 0) {
                    value.restoreState(mRestoredAdapterState, mRestoredClassLoader)
                    setCurrentItemInternal(mRestoredCurItem, smoothScroll = false, always = true)
                    mRestoredCurItem = -1
                    mRestoredAdapterState = null
                    mRestoredClassLoader = null
                } else if (!wasFirstLayout) {
                    populate()
                } else {
                    requestLayout()
                }
            }

            mAdapterChangeListeners?.let { listeners ->
                for (i in listeners.indices) {
                    listeners[i].onAdapterChanged(this, oldAdapter, value)
                }
            }
        }

    private fun removeNonDecorViews() {
        var i = 0
        while (i < childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            if (!lp.isDecor) {
                removeViewAt(i)
                i--
            }
            i++
        }
    }

    fun addOnAdapterChangeListener(listener: OnAdapterChangeListener) {
        if (mAdapterChangeListeners == null) {
            mAdapterChangeListeners = ArrayList()
        }
        mAdapterChangeListeners!!.add(listener)
    }

    fun removeOnAdapterChangeListener(listener: OnAdapterChangeListener) {
        mAdapterChangeListeners?.remove(listener)
    }

    internal fun getClientWidth(): Int = measuredWidth - paddingLeft - paddingRight

    internal fun getClientHeight(): Int = measuredHeight - paddingTop - paddingBottom

    internal fun getClientSize(): Int = if (isHorizontal) getClientWidth() else getClientHeight()

    var currentItem: Int
        get() = mCurItem
        set(item) {
            mPopulatePending = false
            setCurrentItemInternal(item, !mFirstLayout, false)
        }

    fun setCurrentItem(
        item: Int,
        smoothScroll: Boolean,
    ) {
        mPopulatePending = false
        setCurrentItemInternal(item, smoothScroll, false)
    }

    internal fun setCurrentItemInternal(
        item: Int,
        smoothScroll: Boolean,
        always: Boolean,
        velocity: Int = 0,
    ) {
        val adapter = mAdapter
        if (adapter == null || adapter.count <= 0) {
            setScrollingCacheEnabled(false)
            return
        }
        if (!always && mCurItem == item && mItems.isNotEmpty()) {
            setScrollingCacheEnabled(false)
            return
        }

        var target = item
        if (target < 0) {
            target = 0
        } else if (target >= adapter.count) {
            target = adapter.count - 1
        }

        val pageLimit = mOffscreenPageLimit
        if (target > mCurItem + pageLimit || target < mCurItem - pageLimit) {
            for (i in mItems.indices) {
                mItems[i].scrolling = true
            }
        }
        val dispatchSelected = mCurItem != target

        if (mFirstLayout) {
            mCurItem = target
            if (dispatchSelected) {
                dispatchOnPageSelected(target)
            }
            requestLayout()
        } else {
            populate(target)
            scrollToItem(target, smoothScroll, velocity, dispatchSelected)
        }
    }

    private fun scrollToItem(
        item: Int,
        smoothScroll: Boolean,
        velocity: Int,
        dispatchSelected: Boolean,
    ) {
        val curInfo = infoForPosition(item)
        var destX = 0
        var destY = 0
        if (curInfo != null) {
            val clampedOffset = max(mFirstOffset, min(curInfo.offset, mLastOffset))
            if (isHorizontal) {
                val rawX = (getClientWidth() * clampedOffset).toInt()
                destX = if (isRtlHorizontal()) -rawX else rawX
            } else {
                destY = (getClientHeight() * clampedOffset).toInt()
            }
        }
        if (smoothScroll) {
            smoothScrollTo(destX, destY, velocity)
            if (dispatchSelected) {
                dispatchOnPageSelected(item)
            }
        } else {
            if (dispatchSelected) {
                dispatchOnPageSelected(item)
            }
            completeScroll(false)
            scrollTo(destX, destY)
            pageScrolled(destX, destY)
        }
    }

    @Deprecated(
        "Use addOnPageChangeListener and removeOnPageChangeListener instead.",
        ReplaceWith("addOnPageChangeListener(listener)"),
    )
    fun setOnPageChangeListener(listener: OnPageChangeListener?) {
        mOnPageChangeListener = listener
    }

    fun addOnPageChangeListener(listener: OnPageChangeListener) {
        if (mOnPageChangeListeners == null) {
            mOnPageChangeListeners = ArrayList()
        }
        mOnPageChangeListeners!!.add(listener)
    }

    fun removeOnPageChangeListener(listener: OnPageChangeListener) {
        mOnPageChangeListeners?.remove(listener)
    }

    fun clearOnPageChangeListeners() {
        mOnPageChangeListeners?.clear()
    }

    @JvmOverloads
    fun setPageTransformer(
        reverseDrawingOrder: Boolean,
        transformer: PageTransformer?,
        pageLayerType: Int = LAYER_TYPE_HARDWARE,
    ) {
        val hasTransformer = transformer != null
        val needsPopulate = hasTransformer != (mPageTransformer != null)
        mPageTransformer = transformer
        isChildrenDrawingOrderEnabled = hasTransformer
        if (hasTransformer) {
            mDrawingOrder = if (reverseDrawingOrder) DRAW_ORDER_REVERSE else DRAW_ORDER_FORWARD
            mPageTransformerLayerType = pageLayerType
        } else {
            mDrawingOrder = DRAW_ORDER_DEFAULT
        }
        if (needsPopulate) {
            populate()
        }
    }

    override fun getChildDrawingOrder(
        childCount: Int,
        i: Int,
    ): Int {
        val index = if (mDrawingOrder == DRAW_ORDER_REVERSE) childCount - 1 - i else i
        val ordered = mDrawingOrderedChildren!!
        return (ordered[index].layoutParams as LayoutParams).childIndex
    }

    internal fun setInternalPageChangeListener(listener: OnPageChangeListener?): OnPageChangeListener? {
        val oldListener = mInternalPageChangeListener
        mInternalPageChangeListener = listener
        return oldListener
    }

    var offscreenPageLimit: Int
        get() = mOffscreenPageLimit
        set(value) {
            var limit = value
            if (limit < DEFAULT_OFFSCREEN_PAGES) {
                Log.w(TAG, "Requested offscreen page limit $limit too small; defaulting to $DEFAULT_OFFSCREEN_PAGES")
                limit = DEFAULT_OFFSCREEN_PAGES
            }
            if (limit != mOffscreenPageLimit) {
                mOffscreenPageLimit = limit
                populate()
            }
        }

    var pageMargin: Int
        get() = mPageMargin
        set(marginPixels) {
            val oldMargin = mPageMargin
            mPageMargin = marginPixels
            val width = width
            val height = height
            recomputeScrollPosition(width, width, height, height, marginPixels, oldMargin)
            requestLayout()
        }

    fun setPageMarginDrawable(d: Drawable?) {
        mMarginDrawable = d
        if (d != null) {
            refreshDrawableState()
        }
        setWillNotDraw(d == null)
        invalidate()
    }

    fun setPageMarginDrawable(
        @DrawableRes resId: Int,
    ) {
        setPageMarginDrawable(ContextCompat.getDrawable(context, resId))
    }

    override fun verifyDrawable(who: Drawable): Boolean = super.verifyDrawable(who) || who === mMarginDrawable

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val d = mMarginDrawable
        if (d != null && d.isStateful) {
            d.state = drawableState
        }
    }

    internal fun distanceInfluenceForSnapDuration(f: Float): Float {
        var value = f - 0.5f
        value *= (0.3f * Math.PI / 2.0).toFloat()
        return sin(value)
    }

    @JvmOverloads
    internal fun smoothScrollTo(
        x: Int,
        y: Int,
        velocity: Int = 0,
    ) {
        if (childCount == 0) {
            setScrollingCacheEnabled(false)
            return
        }

        var sx = scrollX
        var sy = scrollY
        val wasScrolling = !mScroller.isFinished
        if (wasScrolling) {
            if (isHorizontal) {
                sx = if (mIsScrollStarted) mScroller.currX else mScroller.startX
            } else {
                sy = if (mIsScrollStarted) mScroller.currY else mScroller.startY
            }
            mScroller.abortAnimation()
            setScrollingCacheEnabled(false)
        }

        val dx = x - sx
        val dy = y - sy
        if (dx == 0 && dy == 0) {
            completeScroll(false)
            populate()
            setScrollState(SCROLL_STATE_IDLE)
            return
        }

        setScrollingCacheEnabled(true)
        setScrollState(SCROLL_STATE_SETTLING)

        val directionSize: Int
        val directionDelta: Float
        if (isHorizontal) {
            directionSize = getClientWidth()
            directionDelta = dx.toFloat()
        } else {
            directionSize = getClientHeight()
            directionDelta = dy.toFloat()
        }

        val distanceRatio = min(1f, abs(directionDelta) / directionSize)
        val halfDirectionSize = directionSize / 2
        val distance = halfDirectionSize + halfDirectionSize * distanceInfluenceForSnapDuration(distanceRatio)

        val absVelocity = abs(velocity)
        var duration =
            if (absVelocity > 0) {
                4 * (1000 * abs(distance / absVelocity)).roundToInt()
            } else {
                val pageSize = directionSize * (mAdapter?.getPageSize(mCurItem) ?: 1f)
                val pageDelta = abs(directionDelta) / (pageSize + mPageMargin)
                ((pageDelta + 1) * 100).toInt()
            }
        duration = min(duration, MAX_SETTLE_DURATION)

        mIsScrollStarted = false
        mScroller.startScroll(sx, sy, dx, dy, duration)
        postInvalidateOnAnimation()
    }

    internal fun addNewItem(
        position: Int,
        index: Int,
    ): ItemInfo {
        val adapter = mAdapter!!
        val ii =
            ItemInfo().apply {
                this.position = position
                this.`object` = adapter.instantiateItem(this@DirectionalViewPager, position)
                this.sizeFactor = adapter.getPageSize(position)
            }
        if (index < 0 || index >= mItems.size) {
            mItems.add(ii)
        } else {
            mItems.add(index, ii)
        }
        return ii
    }

    internal fun dataSetChanged() {
        val adapter = mAdapter ?: return
        val adapterCount = adapter.count
        mExpectedAdapterCount = adapterCount
        var needPopulate = mItems.size < mOffscreenPageLimit * 2 + 1 && mItems.size < adapterCount
        var newCurrItem = mCurItem

        var isUpdating = false
        var i = 0
        while (i < mItems.size) {
            val ii = mItems[i]
            val newPos = adapter.getItemPosition(ii.`object`!!)

            if (newPos == PagerAdapter.POSITION_UNCHANGED) {
                i++
                continue
            }

            if (newPos == PagerAdapter.POSITION_NONE) {
                mItems.removeAt(i)
                i--

                if (!isUpdating) {
                    adapter.startUpdate(this)
                    isUpdating = true
                }

                adapter.destroyItem(this, ii.position, ii.`object`!!)
                needPopulate = true

                if (mCurItem == ii.position) {
                    newCurrItem = max(0, min(mCurItem, adapterCount - 1))
                    needPopulate = true
                }
                i++
                continue
            }

            if (ii.position != newPos) {
                if (ii.position == mCurItem) {
                    newCurrItem = newPos
                }
                ii.position = newPos
                needPopulate = true
            }
            i++
        }

        if (isUpdating) {
            adapter.finishUpdate(this)
        }

        Collections.sort(mItems, COMPARATOR)

        if (needPopulate) {
            for (childIndex in 0 until childCount) {
                val child = getChildAt(childIndex)
                val lp = child.layoutParams as LayoutParams
                if (!lp.isDecor) {
                    lp.sizeFactor = 0f
                }
            }

            setCurrentItemInternal(newCurrItem, smoothScroll = false, always = true)
            requestLayout()
        }
    }

    internal fun populate() {
        populate(mCurItem)
    }

    internal fun populate(newCurrentItem: Int) {
        var oldCurInfo: ItemInfo? = null
        if (mCurItem != newCurrentItem) {
            oldCurInfo = infoForPosition(mCurItem)
            mCurItem = newCurrentItem
        }

        val adapter = mAdapter
        if (adapter == null) {
            sortChildDrawingOrder()
            return
        }

        if (mPopulatePending) {
            if (DEBUG) {
                Log.i(TAG, "populate is pending, skipping for now...")
            }
            sortChildDrawingOrder()
            return
        }

        if (windowToken == null) {
            return
        }

        adapter.startUpdate(this)

        val pageLimit = mOffscreenPageLimit
        val startPos = max(0, mCurItem - pageLimit)
        val count = adapter.count
        val endPos = min(count - 1, mCurItem + pageLimit)

        if (count != mExpectedAdapterCount) {
            val resName =
                try {
                    resources.getResourceName(id)
                } catch (_: Resources.NotFoundException) {
                    Integer.toHexString(id)
                }
            throw IllegalStateException(
                "The application's PagerAdapter changed the adapter's contents without calling " +
                    "PagerAdapter#notifyDataSetChanged! Expected adapter item count: $mExpectedAdapterCount, " +
                    "found: $count Pager id: $resName Pager class: $javaClass " +
                    "Problematic adapter: ${adapter.javaClass}",
            )
        }

        var curIndex = 0
        var curItem: ItemInfo? = null
        while (curIndex < mItems.size) {
            val ii = mItems[curIndex]
            if (ii.position >= mCurItem) {
                if (ii.position == mCurItem) {
                    curItem = ii
                }
                break
            }
            curIndex++
        }

        if (curItem == null && count > 0) {
            curItem = addNewItem(mCurItem, curIndex)
        }

        if (curItem != null) {
            var extraSizeStart = 0f
            var itemIndex = curIndex - 1
            var ii: ItemInfo? = if (itemIndex >= 0) mItems[itemIndex] else null
            val clientSize: Int
            val startPadding: Float
            val endPadding: Float
            if (isHorizontal) {
                clientSize = getClientWidth()
                startPadding = paddingLeft.toFloat()
                endPadding = paddingRight.toFloat()
            } else {
                clientSize = getClientHeight()
                startPadding = paddingTop.toFloat()
                endPadding = paddingBottom.toFloat()
            }

            val startSizeNeeded = if (clientSize <= 0) 0f else 2f - curItem.sizeFactor + startPadding / clientSize
            for (pos in mCurItem - 1 downTo 0) {
                if (extraSizeStart >= startSizeNeeded && pos < startPos) {
                    if (ii == null) break
                    if (pos == ii.position && !ii.scrolling) {
                        mItems.removeAt(itemIndex)
                        adapter.destroyItem(this, pos, ii.`object`!!)
                        itemIndex--
                        curIndex--
                        ii = if (itemIndex >= 0) mItems[itemIndex] else null
                    }
                } else if (ii != null && pos == ii.position) {
                    extraSizeStart += ii.sizeFactor
                    itemIndex--
                    ii = if (itemIndex >= 0) mItems[itemIndex] else null
                } else {
                    val added = addNewItem(pos, itemIndex + 1)
                    extraSizeStart += added.sizeFactor
                    curIndex++
                    ii = if (itemIndex >= 0) mItems[itemIndex] else null
                }
            }

            var extraSizeEnd = curItem.sizeFactor
            itemIndex = curIndex + 1
            if (extraSizeEnd < 2f) {
                ii = if (itemIndex < mItems.size) mItems[itemIndex] else null
                val endSizeNeeded = if (clientSize <= 0) 0f else endPadding / clientSize + 2f
                for (pos in mCurItem + 1 until count) {
                    if (extraSizeEnd >= endSizeNeeded && pos > endPos) {
                        if (ii == null) break
                        if (pos == ii.position && !ii.scrolling) {
                            mItems.removeAt(itemIndex)
                            adapter.destroyItem(this, pos, ii.`object`!!)
                            ii = if (itemIndex < mItems.size) mItems[itemIndex] else null
                        }
                    } else if (ii != null && pos == ii.position) {
                        extraSizeEnd += ii.sizeFactor
                        itemIndex++
                        ii = if (itemIndex < mItems.size) mItems[itemIndex] else null
                    } else {
                        val added = addNewItem(pos, itemIndex)
                        itemIndex++
                        extraSizeEnd += added.sizeFactor
                        ii = if (itemIndex < mItems.size) mItems[itemIndex] else null
                    }
                }
            }

            calculatePageOffsets(curItem, curIndex, oldCurInfo)
        }

        adapter.setPrimaryItem(this, mCurItem, curItem?.`object`!!)
        adapter.finishUpdate(this)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            lp.childIndex = i
            if (!lp.isDecor && lp.sizeFactor == 0f) {
                val ii = infoForChild(child)
                if (ii != null) {
                    lp.sizeFactor = ii.sizeFactor
                    lp.position = ii.position
                }
            }
        }
        sortChildDrawingOrder()

        if (hasFocus()) {
            val currentFocused = findFocus()
            var ii = currentFocused?.let { infoForAnyChild(it) }
            if (ii == null || ii.position != mCurItem) {
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    ii = infoForChild(child)
                    if (ii != null && ii.position == mCurItem) {
                        if (child.requestFocus(FOCUS_FORWARD)) {
                            break
                        }
                    }
                }
            }
        }
    }

    private fun sortChildDrawingOrder() {
        if (mDrawingOrder != DRAW_ORDER_DEFAULT) {
            val children = mDrawingOrderedChildren ?: ArrayList<View>().also { mDrawingOrderedChildren = it }
            children.clear()
            for (i in 0 until childCount) {
                children.add(getChildAt(i))
            }
            Collections.sort(children, sPositionComparator)
        }
    }

    internal fun calculatePageOffsets(
        curItem: ItemInfo,
        curIndex: Int,
        oldCurInfo: ItemInfo?,
    ) {
        val adapter = mAdapter ?: return
        val count = adapter.count
        val clientSize = getClientSize()
        val marginOffset = if (clientSize > 0) mPageMargin.toFloat() / clientSize else 0f

        if (oldCurInfo != null) {
            val oldCurPosition = oldCurInfo.position
            if (oldCurPosition < curItem.position) {
                var itemIndex = 0
                var offset = oldCurInfo.offset + oldCurInfo.sizeFactor + marginOffset
                var pos = oldCurPosition + 1
                while (pos <= curItem.position && itemIndex < mItems.size) {
                    var ii = mItems[itemIndex]
                    while (pos > ii.position && itemIndex < mItems.size - 1) {
                        itemIndex++
                        ii = mItems[itemIndex]
                    }
                    while (pos < ii.position) {
                        offset += adapter.getPageSize(pos) + marginOffset
                        pos++
                    }
                    ii.offset = offset
                    offset += ii.sizeFactor + marginOffset
                    pos++
                }
            } else if (oldCurPosition > curItem.position) {
                var itemIndex = mItems.size - 1
                var offset = oldCurInfo.offset
                var pos = oldCurPosition - 1
                while (pos >= curItem.position && itemIndex >= 0) {
                    var ii = mItems[itemIndex]
                    while (pos < ii.position && itemIndex > 0) {
                        itemIndex--
                        ii = mItems[itemIndex]
                    }
                    while (pos > ii.position) {
                        offset -= adapter.getPageSize(pos) + marginOffset
                        pos--
                    }
                    offset -= ii.sizeFactor + marginOffset
                    ii.offset = offset
                    pos--
                }
            }
        }

        val itemCount = mItems.size
        var offset = curItem.offset
        var pos = curItem.position - 1
        mFirstOffset = if (curItem.position == 0) curItem.offset else -Float.MAX_VALUE
        mLastOffset = if (curItem.position == count - 1) curItem.offset + curItem.sizeFactor - 1 else Float.MAX_VALUE

        for (i in curIndex - 1 downTo 0) {
            val ii = mItems[i]
            while (pos > ii.position) {
                offset -= adapter.getPageSize(pos--) + marginOffset
            }
            offset -= ii.sizeFactor + marginOffset
            ii.offset = offset
            if (ii.position == 0) {
                mFirstOffset = offset
            }
            pos--
        }

        offset = curItem.offset + curItem.sizeFactor + marginOffset
        pos = curItem.position + 1
        for (i in curIndex + 1 until itemCount) {
            val ii = mItems[i]
            while (pos < ii.position) {
                offset += adapter.getPageSize(pos++) + marginOffset
            }
            if (ii.position == count - 1) {
                mLastOffset = offset + ii.sizeFactor - 1
            }
            ii.offset = offset
            offset += ii.sizeFactor + marginOffset
            pos++
        }
    }

    class SavedState : AbsSavedState {
        internal var position: Int = 0
        internal var adapterState: Parcelable? = null
        internal var loader: ClassLoader? = null

        constructor(superState: Parcelable) : super(superState)

        internal constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
            val classLoader = loader ?: javaClass.classLoader
            position = source.readInt()
            @Suppress("DEPRECATION")
            adapterState = source.readParcelable(classLoader)
            this.loader = classLoader
        }

        override fun writeToParcel(
            out: Parcel,
            flags: Int,
        ) {
            super.writeToParcel(out, flags)
            out.writeInt(position)
            out.writeParcelable(adapterState, flags)
        }

        override fun toString(): String =
            "DirectionalViewPager.SavedState{${Integer.toHexString(System.identityHashCode(this))} position=$position}"

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> =
                object : Parcelable.ClassLoaderCreator<SavedState> {
                    override fun createFromParcel(
                        source: Parcel,
                        loader: ClassLoader,
                    ): SavedState = SavedState(source, loader)

                    override fun createFromParcel(source: Parcel): SavedState = SavedState(source, null)

                    override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
                }
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState() ?: return null
        val ss = SavedState(superState)
        ss.position = mCurItem
        ss.adapterState = mAdapter?.saveState()
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)
        val adapter = mAdapter
        if (adapter != null) {
            adapter.restoreState(state.adapterState, state.loader)
            setCurrentItemInternal(state.position, smoothScroll = false, always = true)
        } else {
            mRestoredCurItem = state.position
            mRestoredAdapterState = state.adapterState
            mRestoredClassLoader = state.loader
        }
    }

    override fun addView(
        child: View,
        index: Int,
        params: ViewGroup.LayoutParams,
    ) {
        var resolvedParams = params
        if (!checkLayoutParams(resolvedParams)) {
            resolvedParams = generateLayoutParams(resolvedParams)
        }
        val lp = resolvedParams as LayoutParams
        lp.isDecor = lp.isDecor or isDecorView(child)
        if (mInLayout) {
            check(!lp.isDecor) { "Cannot add pager decor view during layout" }
            lp.needsMeasure = true
            addViewInLayout(child, index, resolvedParams)
        } else {
            super.addView(child, index, resolvedParams)
        }

        if (USE_CACHE) {
            @Suppress("DEPRECATION")
            child.isDrawingCacheEnabled = child.visibility != GONE && mScrollingCacheEnabled
        }
    }

    override fun removeView(view: View) {
        if (mInLayout) {
            removeViewInLayout(view)
        } else {
            super.removeView(view)
        }
    }

    internal fun infoForChild(child: View): ItemInfo? {
        val adapter = mAdapter ?: return null
        for (i in mItems.indices) {
            val ii = mItems[i]
            if (adapter.isViewFromObject(child, ii.`object`!!)) {
                return ii
            }
        }
        return null
    }

    internal fun infoForAnyChild(child: View): ItemInfo? {
        var current = child
        var parent: ViewParent?
        while (current.parent.also { parent = it } !== this) {
            if (parent == null || parent !is View) {
                return null
            }
            current = parent as View
        }
        return infoForChild(current)
    }

    internal fun infoForPosition(position: Int): ItemInfo? {
        for (i in mItems.indices) {
            val ii = mItems[i]
            if (ii.position == position) {
                return ii
            }
        }
        return null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mFirstLayout = true
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), getDefaultSize(0, heightMeasureSpec))

        val maxGutterSize = (if (isHorizontal) measuredWidth else measuredHeight) / 10
        mGutterSize = min(maxGutterSize, mDefaultGutterSize)

        var childWidthSize = measuredWidth - paddingLeft - paddingRight
        var childHeightSize = measuredHeight - paddingTop - paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val lp = child.layoutParams as? LayoutParams
                if (lp != null && lp.isDecor) {
                    val hgrav = lp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
                    val vgrav = lp.gravity and Gravity.VERTICAL_GRAVITY_MASK
                    var widthMode = MeasureSpec.AT_MOST
                    var heightMode = MeasureSpec.AT_MOST
                    val consumeVertical = vgrav == Gravity.TOP || vgrav == Gravity.BOTTOM
                    val consumeHorizontal = hgrav == Gravity.LEFT || hgrav == Gravity.RIGHT

                    if (consumeVertical) {
                        widthMode = MeasureSpec.EXACTLY
                    } else if (consumeHorizontal) {
                        heightMode = MeasureSpec.EXACTLY
                    }

                    var widthSize = childWidthSize
                    var heightSize = childHeightSize
                    if (lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                        widthMode = MeasureSpec.EXACTLY
                        if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                            widthSize = lp.width
                        }
                    }
                    if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                        heightMode = MeasureSpec.EXACTLY
                        if (lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                            heightSize = lp.height
                        }
                    }
                    val widthSpec = MeasureSpec.makeMeasureSpec(widthSize, widthMode)
                    val heightSpec = MeasureSpec.makeMeasureSpec(heightSize, heightMode)
                    child.measure(widthSpec, heightSpec)

                    if (consumeVertical) {
                        childHeightSize -= child.measuredHeight
                    } else if (consumeHorizontal) {
                        childWidthSize -= child.measuredWidth
                    }
                }
            }
        }

        mChildWidthMeasureSpec = MeasureSpec.makeMeasureSpec(childWidthSize, MeasureSpec.EXACTLY)
        mChildHeightMeasureSpec = MeasureSpec.makeMeasureSpec(childHeightSize, MeasureSpec.EXACTLY)

        mInLayout = true
        populate()
        mInLayout = false

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val lp = child.layoutParams as? LayoutParams
                if (lp == null || !lp.isDecor) {
                    val factor = lp?.sizeFactor ?: 1f
                    if (isHorizontal) {
                        val widthSpec = MeasureSpec.makeMeasureSpec((childWidthSize * factor).toInt(), MeasureSpec.EXACTLY)
                        child.measure(widthSpec, mChildHeightMeasureSpec)
                    } else {
                        val heightSpec = MeasureSpec.makeMeasureSpec((childHeightSize * factor).toInt(), MeasureSpec.EXACTLY)
                        child.measure(mChildWidthMeasureSpec, heightSpec)
                    }
                }
            }
        }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            recomputeScrollPosition(w, oldw, h, oldh, mPageMargin, mPageMargin)
        }
    }

    private fun recomputeScrollPosition(
        width: Int,
        oldWidth: Int,
        height: Int,
        oldHeight: Int,
        margin: Int,
        oldMargin: Int,
    ) {
        if (isHorizontal) {
            val rtl = isRtlHorizontal()
            if (oldWidth > 0 && mItems.isNotEmpty()) {
                if (!mScroller.isFinished) {
                    val targetX = currentItem * getClientWidth()
                    mScroller.finalX = if (rtl) -targetX else targetX
                } else {
                    val widthWithMargin = width - paddingLeft - paddingRight + margin
                    val oldWidthWithMargin = oldWidth - paddingLeft - paddingRight + oldMargin
                    val xpos = scrollX
                    val pageOffset = xpos.toFloat() / oldWidthWithMargin
                    val newOffsetPixels = (pageOffset * widthWithMargin).toInt()
                    scrollTo(newOffsetPixels, scrollY)
                }
            } else {
                val ii = infoForPosition(mCurItem)
                val scrollOffset = if (ii != null) min(ii.offset, mLastOffset) else 0f
                val rawScrollPos = (scrollOffset * (width - paddingLeft - paddingRight)).toInt()
                val scrollPos = if (rtl) -rawScrollPos else rawScrollPos
                if (scrollPos != scrollX) {
                    completeScroll(false)
                    scrollTo(scrollPos, scrollY)
                }
            }
        } else {
            if (oldHeight > 0 && mItems.isNotEmpty()) {
                if (!mScroller.isFinished) {
                    mScroller.finalY = currentItem * getClientHeight()
                } else {
                    val heightWithMargin = height - paddingTop - paddingBottom + margin
                    val oldHeightWithMargin = oldHeight - paddingTop - paddingBottom + oldMargin
                    val ypos = scrollY
                    val pageOffset = ypos.toFloat() / oldHeightWithMargin
                    val newOffsetPixels = (pageOffset * heightWithMargin).toInt()
                    scrollTo(scrollX, newOffsetPixels)
                }
            } else {
                val ii = infoForPosition(mCurItem)
                val scrollOffset = if (ii != null) min(ii.offset, mLastOffset) else 0f
                val scrollPos = (scrollOffset * (height - paddingTop - paddingBottom)).toInt()
                if (scrollPos != scrollY) {
                    completeScroll(false)
                    scrollTo(scrollX, scrollPos)
                }
            }
        }
    }

    override fun onLayout(
        changed: Boolean,
        l: Int,
        t: Int,
        r: Int,
        b: Int,
    ) {
        val count = childCount
        val width = r - l
        val height = b - t
        var paddingLeft = paddingLeft
        var paddingTop = paddingTop
        var paddingRight = paddingRight
        var paddingBottom = paddingBottom
        val scrollX = scrollX
        val scrollY = scrollY

        var decorCount = 0

        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val lp = child.layoutParams as LayoutParams
                if (lp.isDecor) {
                    val hgrav = lp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
                    val vgrav = lp.gravity and Gravity.VERTICAL_GRAVITY_MASK
                    var childLeft =
                        when (hgrav) {
                            Gravity.LEFT -> {
                                val left = paddingLeft
                                paddingLeft += child.measuredWidth
                                left
                            }

                            Gravity.CENTER_HORIZONTAL -> {
                                max((width - child.measuredWidth) / 2, paddingLeft)
                            }

                            Gravity.RIGHT -> {
                                val left = width - paddingRight - child.measuredWidth
                                paddingRight += child.measuredWidth
                                left
                            }

                            else -> {
                                paddingLeft
                            }
                        }
                    var childTop =
                        when (vgrav) {
                            Gravity.TOP -> {
                                val top = paddingTop
                                paddingTop += child.measuredHeight
                                top
                            }

                            Gravity.CENTER_VERTICAL -> {
                                max((height - child.measuredHeight) / 2, paddingTop)
                            }

                            Gravity.BOTTOM -> {
                                val top = height - paddingBottom - child.measuredHeight
                                paddingBottom += child.measuredHeight
                                top
                            }

                            else -> {
                                paddingTop
                            }
                        }
                    if (isHorizontal) {
                        childLeft += scrollX
                    } else {
                        childTop += scrollY
                    }
                    child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
                    decorCount++
                }
            }
        }

        val childWidth = width - paddingLeft - paddingRight
        val childHeight = height - paddingTop - paddingBottom

        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val lp = child.layoutParams as LayoutParams
                val ii = if (!lp.isDecor) infoForChild(child) else null
                if (ii != null) {
                    val childLeft: Int
                    val childTop: Int
                    if (isHorizontal) {
                        childLeft =
                            if (isRtlHorizontal()) {
                                paddingLeft + childWidth - (childWidth * (ii.offset + lp.sizeFactor)).toInt()
                            } else {
                                paddingLeft + (childWidth * ii.offset).toInt()
                            }
                        childTop = paddingTop
                    } else {
                        childLeft = paddingLeft
                        childTop = paddingTop + (childHeight * ii.offset).toInt()
                    }
                    if (lp.needsMeasure) {
                        lp.needsMeasure = false
                        val widthSpec =
                            if (isHorizontal) {
                                MeasureSpec.makeMeasureSpec((childWidth * lp.sizeFactor).toInt(), MeasureSpec.EXACTLY)
                            } else {
                                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
                            }
                        val heightSpec =
                            if (isHorizontal) {
                                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                            } else {
                                MeasureSpec.makeMeasureSpec((childHeight * lp.sizeFactor).toInt(), MeasureSpec.EXACTLY)
                            }
                        child.measure(widthSpec, heightSpec)
                    }
                    child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
                }
            }
        }

        mTopPageBounds = paddingTop
        mBottomPageBounds = height - paddingBottom
        mLeftPageBounds = paddingLeft
        mRightPageBounds = width - paddingRight
        mDecorChildCount = decorCount

        if (mFirstLayout || mPendingScrollToCurrentItem) {
            scrollToItem(mCurItem, smoothScroll = false, velocity = 0, dispatchSelected = false)
        }
        mFirstLayout = false
        mPendingScrollToCurrentItem = false
    }

    override fun computeScroll() {
        mIsScrollStarted = true
        if (!mScroller.isFinished && mScroller.computeScrollOffset()) {
            val oldX = scrollX
            val oldY = scrollY
            val x = mScroller.currX
            val y = mScroller.currY

            if (oldX != x || oldY != y) {
                scrollTo(x, y)
                if (!pageScrolled(x, y)) {
                    mScroller.abortAnimation()
                    if (isHorizontal) {
                        scrollTo(0, y)
                    } else {
                        scrollTo(x, 0)
                    }
                }
            }

            postInvalidateOnAnimation()
            return
        }

        completeScroll(true)
    }

    private fun pageScrolled(
        xpos: Int,
        ypos: Int,
    ): Boolean {
        if (mItems.isEmpty()) {
            if (mFirstLayout) {
                return false
            }
            mCalledSuper = false
            onPageScrolled(0, 0f, 0)
            check(mCalledSuper) { "onPageScrolled did not call superclass implementation" }
            return false
        }

        val ii = infoForCurrentScrollPosition()!!
        val size = getClientSize()
        val sizeWithMargin = size + mPageMargin
        val marginOffset = if (size > 0) mPageMargin.toFloat() / size else 0f
        val currentPage = ii.position
        val pos = if (isHorizontal) (if (isRtlHorizontal()) -xpos else xpos) else ypos
        val pageOffset = if (size > 0) ((pos.toFloat() / size) - ii.offset) / (ii.sizeFactor + marginOffset) else 0f
        val offsetPixels = (pageOffset * sizeWithMargin).toInt()

        mCalledSuper = false
        onPageScrolled(currentPage, pageOffset, offsetPixels)
        check(mCalledSuper) { "onPageScrolled did not call superclass implementation" }
        return true
    }

    @CallSuper
    protected open fun onPageScrolled(
        position: Int,
        offset: Float,
        offsetPixels: Int,
    ) {
        if (mDecorChildCount > 0) {
            if (isHorizontal) {
                val scrollX = scrollX
                var paddingLeft = paddingLeft
                var paddingRight = paddingRight
                val width = width
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    val lp = child.layoutParams as LayoutParams
                    if (!lp.isDecor) continue

                    val hgrav = lp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
                    var childLeft =
                        when (hgrav) {
                            Gravity.LEFT -> {
                                val left = paddingLeft
                                paddingLeft += child.width
                                left
                            }

                            Gravity.CENTER_HORIZONTAL -> {
                                max((width - child.measuredWidth) / 2, paddingLeft)
                            }

                            Gravity.RIGHT -> {
                                val left = width - paddingRight - child.measuredWidth
                                paddingRight += child.measuredWidth
                                left
                            }

                            else -> {
                                paddingLeft
                            }
                        }
                    childLeft += scrollX

                    val childOffset = childLeft - child.left
                    if (childOffset != 0) {
                        child.offsetLeftAndRight(childOffset)
                    }
                }
            } else {
                val scrollY = scrollY
                var paddingTop = paddingTop
                var paddingBottom = paddingBottom
                val height = height
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    val lp = child.layoutParams as LayoutParams
                    if (!lp.isDecor) continue

                    val vgrav = lp.gravity and Gravity.VERTICAL_GRAVITY_MASK
                    var childTop =
                        when (vgrav) {
                            Gravity.TOP -> {
                                val top = paddingTop
                                paddingTop += child.height
                                top
                            }

                            Gravity.CENTER_VERTICAL -> {
                                max((height - child.measuredHeight) / 2, paddingTop)
                            }

                            Gravity.BOTTOM -> {
                                val top = height - paddingBottom - child.measuredHeight
                                paddingBottom += child.measuredHeight
                                top
                            }

                            else -> {
                                paddingTop
                            }
                        }
                    childTop += scrollY

                    val childOffset = childTop - child.top
                    if (childOffset != 0) {
                        child.offsetTopAndBottom(childOffset)
                    }
                }
            }
        }

        dispatchOnPageScrolled(position, offset, offsetPixels)

        mPageTransformer?.let { transformer ->
            if (isHorizontal) {
                val scrollX = scrollX
                val clientWidth = getClientWidth()
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    val lp = child.layoutParams as LayoutParams
                    if (lp.isDecor) continue
                    val transformPos = (child.left - scrollX).toFloat() / clientWidth
                    transformer.transformPage(child, transformPos)
                }
            } else {
                val scrollY = scrollY
                val clientHeight = getClientHeight()
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    val lp = child.layoutParams as LayoutParams
                    if (lp.isDecor) continue
                    val transformPos = (child.top - scrollY).toFloat() / clientHeight
                    transformer.transformPage(child, transformPos)
                }
            }
        }

        mCalledSuper = true
    }

    private fun dispatchOnPageScrolled(
        position: Int,
        offset: Float,
        offsetPixels: Int,
    ) {
        mOnPageChangeListener?.onPageScrolled(position, offset, offsetPixels)
        mOnPageChangeListeners?.let { listeners ->
            for (i in listeners.indices) {
                listeners[i].onPageScrolled(position, offset, offsetPixels)
            }
        }
        mInternalPageChangeListener?.onPageScrolled(position, offset, offsetPixels)
    }

    private fun dispatchOnPageSelected(position: Int) {
        mOnPageChangeListener?.onPageSelected(position)
        mOnPageChangeListeners?.let { listeners ->
            for (i in listeners.indices) {
                listeners[i].onPageSelected(position)
            }
        }
        mInternalPageChangeListener?.onPageSelected(position)
    }

    private fun dispatchOnScrollStateChanged(state: Int) {
        mOnPageChangeListener?.onPageScrollStateChanged(state)
        mOnPageChangeListeners?.let { listeners ->
            for (i in listeners.indices) {
                listeners[i].onPageScrollStateChanged(state)
            }
        }
        mInternalPageChangeListener?.onPageScrollStateChanged(state)
    }

    private fun completeScroll(postEvents: Boolean) {
        var needPopulate = mScrollState == SCROLL_STATE_SETTLING
        if (needPopulate) {
            setScrollingCacheEnabled(false)
            val wasScrolling = !mScroller.isFinished
            if (wasScrolling) {
                mScroller.abortAnimation()
                val oldX = scrollX
                val oldY = scrollY
                val x = mScroller.currX
                val y = mScroller.currY
                if (oldX != x || oldY != y) {
                    scrollTo(x, y)
                    if (x != oldX || y != oldY) {
                        pageScrolled(x, y)
                    }
                }
            }
        }
        mPopulatePending = false
        for (i in mItems.indices) {
            val ii = mItems[i]
            if (ii.scrolling) {
                needPopulate = true
                ii.scrolling = false
            }
        }
        if (needPopulate) {
            if (postEvents) {
                postOnAnimation(mEndScrollRunnable)
            } else {
                mEndScrollRunnable.run()
            }
        }
    }

    private fun isGutterDragX(
        x: Float,
        dx: Float,
    ): Boolean = (x < mGutterSize && dx > 0) || (x > width - mGutterSize && dx < 0)

    private fun isGutterDragY(
        y: Float,
        dy: Float,
    ): Boolean = (y < mGutterSize && dy > 0) || (y > height - mGutterSize && dy < 0)

    private fun enableLayers(enable: Boolean) {
        for (i in 0 until childCount) {
            val layerType = if (enable) mPageTransformerLayerType else LAYER_TYPE_NONE
            getChildAt(i).setLayerType(layerType, null)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action and MotionEvent.ACTION_MASK

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            resetTouch()
            return false
        }

        if (!isUserInputEnabled) {
            return false
        }

        if (action != MotionEvent.ACTION_DOWN) {
            if (mIsBeingDragged) return true
            if (mIsUnableToDrag) return false
        }

        when (action) {
            MotionEvent.ACTION_MOVE -> {
                val activePointerId = mActivePointerId
                if (activePointerId != INVALID_POINTER) {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = ev.getX(pointerIndex)
                        val y = ev.getY(pointerIndex)

                        if (isHorizontal) {
                            val dx = x - mLastMotionX
                            val xDiff = abs(dx)
                            val yDiff = abs(y - mInitialMotionY)

                            if (dx != 0f && !isGutterDragX(mLastMotionX, dx) &&
                                canScroll(this, false, dx.toInt(), x.toInt(), y.toInt())
                            ) {
                                mLastMotionX = x
                                mLastMotionY = y
                                mIsUnableToDrag = true
                                return false
                            }
                            if (xDiff > mTouchSlop && xDiff * 0.5f > yDiff) {
                                mIsBeingDragged = true
                                requestParentDisallowInterceptTouchEvent(true)
                                setScrollState(SCROLL_STATE_DRAGGING)
                                mLastMotionX = if (dx > 0) mInitialMotionX + mTouchSlop else mInitialMotionX - mTouchSlop
                                mLastMotionY = y
                                setScrollingCacheEnabled(true)
                            } else if (yDiff > mTouchSlop) {
                                mIsUnableToDrag = true
                            }
                            if (mIsBeingDragged && performDragX(x)) {
                                postInvalidateOnAnimation()
                            }
                        } else {
                            val dy = y - mLastMotionY
                            val yDiff = abs(dy)
                            val xDiff = abs(x - mInitialMotionX)

                            if (dy != 0f && !isGutterDragY(mLastMotionY, dy) &&
                                canScroll(this, false, dy.toInt(), x.toInt(), y.toInt())
                            ) {
                                mLastMotionX = x
                                mLastMotionY = y
                                mIsUnableToDrag = true
                                return false
                            }
                            if (yDiff > mTouchSlop && yDiff * 0.5f > xDiff) {
                                mIsBeingDragged = true
                                requestParentDisallowInterceptTouchEvent(true)
                                setScrollState(SCROLL_STATE_DRAGGING)
                                mLastMotionY = if (dy > 0) mInitialMotionY + mTouchSlop else mInitialMotionY - mTouchSlop
                                mLastMotionX = x
                                setScrollingCacheEnabled(true)
                            } else if (xDiff > mTouchSlop) {
                                mIsUnableToDrag = true
                            }
                            if (mIsBeingDragged && performDragY(y)) {
                                postInvalidateOnAnimation()
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_DOWN -> {
                mInitialMotionX = ev.x
                mLastMotionX = mInitialMotionX
                mInitialMotionY = ev.y
                mLastMotionY = mInitialMotionY
                mActivePointerId = ev.getPointerId(0)
                mIsUnableToDrag = false
                mNestedOffsetX = 0
                mNestedOffsetY = 0
                val scrollAxis = if (isHorizontal) ViewCompat.SCROLL_AXIS_HORIZONTAL else ViewCompat.SCROLL_AXIS_VERTICAL
                startNestedScroll(scrollAxis, ViewCompat.TYPE_TOUCH)

                mIsScrollStarted = true
                mScroller.computeScrollOffset()
                val catchingSettle =
                    mScrollState == SCROLL_STATE_SETTLING && (
                        (isHorizontal && abs(mScroller.finalX - mScroller.currX) > mCloseEnough) ||
                            (!isHorizontal && abs(mScroller.finalY - mScroller.currY) > mCloseEnough)
                    )
                if (catchingSettle) {
                    mScroller.abortAnimation()
                    mPopulatePending = false
                    populate()
                    mIsBeingDragged = true
                    requestParentDisallowInterceptTouchEvent(true)
                    setScrollState(SCROLL_STATE_DRAGGING)
                } else {
                    completeScroll(false)
                    mIsBeingDragged = false
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
            }
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker?.addMovement(ev)

        return mIsBeingDragged
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (mFakeDragging) {
            return true
        }

        if (!isUserInputEnabled) {
            return false
        }

        if (ev.action == MotionEvent.ACTION_DOWN && ev.edgeFlags != 0) {
            return false
        }

        val adapter = mAdapter
        if (adapter == null || adapter.count == 0) {
            return false
        }

        val trackedEvent = MotionEvent.obtain(ev)
        val action = ev.action
        if ((action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            mNestedOffsetX = 0
            mNestedOffsetY = 0
        }
        trackedEvent.offsetLocation(mNestedOffsetX.toFloat(), mNestedOffsetY.toFloat())
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker?.addMovement(trackedEvent)
        trackedEvent.recycle()

        var needsInvalidate = false

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mScroller.abortAnimation()
                mPopulatePending = false
                populate()

                mInitialMotionX = ev.x
                mLastMotionX = mInitialMotionX
                mInitialMotionY = ev.y
                mLastMotionY = mInitialMotionY
                mActivePointerId = ev.getPointerId(0)
                val scrollAxis = if (isHorizontal) ViewCompat.SCROLL_AXIS_HORIZONTAL else ViewCompat.SCROLL_AXIS_VERTICAL
                startNestedScroll(scrollAxis, ViewCompat.TYPE_TOUCH)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!mIsBeingDragged) {
                    val pointerIndex = ev.findPointerIndex(mActivePointerId)
                    if (pointerIndex == -1) {
                        needsInvalidate = resetTouch()
                    } else {
                        val x = ev.getX(pointerIndex)
                        val xDiff = abs(x - mLastMotionX)
                        val y = ev.getY(pointerIndex)
                        val yDiff = abs(y - mLastMotionY)
                        if (isHorizontal) {
                            if (xDiff > mTouchSlop && xDiff > yDiff) {
                                mIsBeingDragged = true
                                requestParentDisallowInterceptTouchEvent(true)
                                mLastMotionX =
                                    if (x - mInitialMotionX > 0) {
                                        mInitialMotionX + mTouchSlop
                                    } else {
                                        mInitialMotionX - mTouchSlop
                                    }
                                mLastMotionY = y
                                setScrollState(SCROLL_STATE_DRAGGING)
                                setScrollingCacheEnabled(true)
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        } else {
                            if (yDiff > mTouchSlop && yDiff > xDiff) {
                                mIsBeingDragged = true
                                requestParentDisallowInterceptTouchEvent(true)
                                mLastMotionY =
                                    if (y - mInitialMotionY > 0) {
                                        mInitialMotionY + mTouchSlop
                                    } else {
                                        mInitialMotionY - mTouchSlop
                                    }
                                mLastMotionX = x
                                setScrollState(SCROLL_STATE_DRAGGING)
                                setScrollingCacheEnabled(true)
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                    }
                }
                if (mIsBeingDragged) {
                    val activePointerIndex = ev.findPointerIndex(mActivePointerId)
                    if (activePointerIndex != -1) {
                        needsInvalidate =
                            if (isHorizontal) {
                                needsInvalidate or performDragX(ev.getX(activePointerIndex))
                            } else {
                                needsInvalidate or performDragY(ev.getY(activePointerIndex))
                            }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (mIsBeingDragged) {
                    val velocityTracker = mVelocityTracker!!
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
                    mPopulatePending = true
                    val activePointerIndex = ev.findPointerIndex(mActivePointerId)
                    val size = getClientSize()
                    val initialVelocity: Int
                    val scroll: Int
                    val totalDelta: Int
                    if (isHorizontal) {
                        initialVelocity = velocityTracker.getXVelocity(mActivePointerId).toInt()
                        scroll = if (isRtlHorizontal()) -scrollX else scrollX
                        totalDelta = (ev.getX(activePointerIndex) - mInitialMotionX).toInt()
                    } else {
                        initialVelocity = velocityTracker.getYVelocity(mActivePointerId).toInt()
                        scroll = scrollY
                        totalDelta = (ev.getY(activePointerIndex) - mInitialMotionY).toInt()
                    }
                    val ii = infoForCurrentScrollPosition()!!
                    val marginOffset = if (size > 0) mPageMargin.toFloat() / size else 0f
                    val currentPage = ii.position
                    val pageOffset =
                        if (size > 0) {
                            ((scroll.toFloat() / size) - ii.offset) / (ii.sizeFactor + marginOffset)
                        } else {
                            0f
                        }
                    val flingVx = if (isHorizontal) -initialVelocity.toFloat() else 0f
                    val flingVy = if (!isHorizontal) -initialVelocity.toFloat() else 0f
                    if (!dispatchNestedPreFling(flingVx, flingVy)) {
                        val nextPage = determineTargetPage(currentPage, pageOffset, initialVelocity, totalDelta)
                        val canFling = canScrollDirection(if (initialVelocity > 0) -1 else 1)
                        dispatchNestedFling(flingVx, flingVy, canFling)
                        setCurrentItemInternal(nextPage, smoothScroll = true, always = true, velocity = initialVelocity)
                    } else {
                        scrollToItem(mCurItem, smoothScroll = true, velocity = 0, dispatchSelected = false)
                    }
                    needsInvalidate = resetTouch()
                } else {
                    stopNestedScroll(ViewCompat.TYPE_TOUCH)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mIsBeingDragged) {
                    scrollToItem(mCurItem, smoothScroll = true, velocity = 0, dispatchSelected = false)
                    needsInvalidate = resetTouch()
                } else {
                    stopNestedScroll(ViewCompat.TYPE_TOUCH)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                if (isHorizontal) {
                    mLastMotionX = ev.getX(index)
                } else {
                    mLastMotionY = ev.getY(index)
                }
                mActivePointerId = ev.getPointerId(index)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
                val pointerIndex = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex != -1) {
                    if (isHorizontal) {
                        mLastMotionX = ev.getX(pointerIndex)
                    } else {
                        mLastMotionY = ev.getY(pointerIndex)
                    }
                }
            }
        }

        if (needsInvalidate) {
            postInvalidateOnAnimation()
        }
        return true
    }

    private fun resetTouch(): Boolean {
        mActivePointerId = INVALID_POINTER
        endDrag()
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
        mStartEdge.onRelease()
        mEndEdge.onRelease()
        return mStartEdge.isFinished || mEndEdge.isFinished
    }

    private fun requestParentDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun performDragX(x: Float): Boolean {
        var deltaX = (mLastMotionX - x).toInt()
        val fractional = (mLastMotionX - x) - deltaX
        mLastMotionX = x

        mScrollConsumed[0] = 0
        mScrollConsumed[1] = 0
        if (dispatchNestedPreScroll(deltaX, 0, mScrollConsumed, mScrollOffset, ViewCompat.TYPE_TOUCH)) {
            deltaX -= mScrollConsumed[0]
            mLastMotionX -= mScrollOffset[0]
            mNestedOffsetX += mScrollOffset[0]
            mNestedOffsetY += mScrollOffset[1]
        }

        val totalDelta = deltaX + fractional
        val oldScrollX = scrollX
        val unconsumed = performDragDeltaX(totalDelta)
        val consumedX = scrollX - oldScrollX
        val unconsumedInt = unconsumed.toInt()

        mNestedConsumed[0] = 0
        mNestedConsumed[1] = 0
        dispatchNestedScroll(consumedX, 0, unconsumedInt, 0, mScrollOffset, ViewCompat.TYPE_TOUCH, mNestedConsumed)
        mLastMotionX -= mScrollOffset[0]
        mNestedOffsetX += mScrollOffset[0]
        mNestedOffsetY += mScrollOffset[1]

        val residual = unconsumed - mNestedConsumed[0]
        return pullHorizontalEdgeIfNeeded(residual)
    }

    private fun performDragDeltaX(deltaX: Float): Float {
        val oldScrollX = scrollX.toFloat()
        var targetScrollX = oldScrollX + deltaX
        val width = getClientWidth()
        val rtl = isRtlHorizontal()

        val firstItem = mItems[0]
        val lastItem = mItems[mItems.size - 1]
        val minBound: Float
        val maxBound: Float
        if (rtl) {
            minBound = if (lastItem.position != mAdapter!!.count - 1) -(lastItem.offset * width) else -(width * mLastOffset)
            maxBound = if (firstItem.position != 0) -(firstItem.offset * width) else -(width * mFirstOffset)
        } else {
            minBound = if (firstItem.position != 0) firstItem.offset * width else width * mFirstOffset
            maxBound = if (lastItem.position != mAdapter!!.count - 1) lastItem.offset * width else width * mLastOffset
        }

        var unconsumed = 0f
        if (targetScrollX < minBound) {
            unconsumed = targetScrollX - minBound
            targetScrollX = minBound
        } else if (targetScrollX > maxBound) {
            unconsumed = targetScrollX - maxBound
            targetScrollX = maxBound
        }

        mLastMotionX += targetScrollX - targetScrollX.toInt()
        scrollTo(targetScrollX.toInt(), scrollY)
        pageScrolled(targetScrollX.toInt(), 0)
        return unconsumed
    }

    private fun pullHorizontalEdgeIfNeeded(residualUnconsumed: Float): Boolean {
        if (residualUnconsumed == 0f || mItems.isEmpty()) return false
        val width = getClientWidth()
        if (width <= 0) return false
        val rtl = isRtlHorizontal()
        val firstItem = mItems[0]
        val lastItem = mItems[mItems.size - 1]
        val startAbsolute = firstItem.position == 0
        val endAbsolute = lastItem.position == mAdapter!!.count - 1
        return if (rtl) {
            if (residualUnconsumed > 0f && startAbsolute) {
                mStartEdge.onPull(abs(residualUnconsumed) / width)
                true
            } else if (residualUnconsumed < 0f && endAbsolute) {
                mEndEdge.onPull(abs(residualUnconsumed) / width)
                true
            } else {
                false
            }
        } else {
            if (residualUnconsumed < 0f && startAbsolute) {
                mStartEdge.onPull(abs(residualUnconsumed) / width)
                true
            } else if (residualUnconsumed > 0f && endAbsolute) {
                mEndEdge.onPull(abs(residualUnconsumed) / width)
                true
            } else {
                false
            }
        }
    }

    private fun performDragY(y: Float): Boolean {
        var deltaY = (mLastMotionY - y).toInt()
        val fractional = (mLastMotionY - y) - deltaY
        mLastMotionY = y

        mScrollConsumed[0] = 0
        mScrollConsumed[1] = 0
        if (dispatchNestedPreScroll(0, deltaY, mScrollConsumed, mScrollOffset, ViewCompat.TYPE_TOUCH)) {
            deltaY -= mScrollConsumed[1]
            mLastMotionY -= mScrollOffset[1]
            mNestedOffsetX += mScrollOffset[0]
            mNestedOffsetY += mScrollOffset[1]
        }

        val totalDelta = deltaY + fractional
        val oldScrollY = scrollY
        val unconsumed = performDragDeltaY(totalDelta)
        val consumedY = scrollY - oldScrollY
        val unconsumedInt = unconsumed.toInt()

        mNestedConsumed[0] = 0
        mNestedConsumed[1] = 0
        dispatchNestedScroll(0, consumedY, 0, unconsumedInt, mScrollOffset, ViewCompat.TYPE_TOUCH, mNestedConsumed)
        mLastMotionY -= mScrollOffset[1]
        mNestedOffsetX += mScrollOffset[0]
        mNestedOffsetY += mScrollOffset[1]

        val residual = unconsumed - mNestedConsumed[1]
        if (residual == 0f || mItems.isEmpty()) return false
        val height = getClientHeight()
        if (height <= 0) return false
        val firstItem = mItems[0]
        val lastItem = mItems[mItems.size - 1]
        return if (residual < 0f && firstItem.position == 0) {
            mStartEdge.onPull(abs(residual) / height)
            true
        } else if (residual > 0f && lastItem.position == mAdapter!!.count - 1) {
            mEndEdge.onPull(abs(residual) / height)
            true
        } else {
            false
        }
    }

    private fun performDragDeltaY(deltaY: Float): Float {
        val oldScrollY = scrollY.toFloat()
        var targetScrollY = oldScrollY + deltaY
        val height = getClientHeight()

        val firstItem = mItems[0]
        val lastItem = mItems[mItems.size - 1]
        val topBound = if (firstItem.position != 0) firstItem.offset * height else height * mFirstOffset
        val bottomBound = if (lastItem.position != mAdapter!!.count - 1) lastItem.offset * height else height * mLastOffset

        var unconsumed = 0f
        if (targetScrollY < topBound) {
            unconsumed = targetScrollY - topBound
            targetScrollY = topBound
        } else if (targetScrollY > bottomBound) {
            unconsumed = targetScrollY - bottomBound
            targetScrollY = bottomBound
        }

        mLastMotionY += targetScrollY - targetScrollY.toInt()
        scrollTo(scrollX, targetScrollY.toInt())
        pageScrolled(0, targetScrollY.toInt())
        return unconsumed
    }

    private fun infoForCurrentScrollPosition(): ItemInfo? {
        val size = getClientSize()
        val scroll =
            if (isHorizontal) {
                (if (isRtlHorizontal()) -scrollX else scrollX).toFloat()
            } else {
                scrollY.toFloat()
            }
        val scrollOffset = if (size > 0) scroll / size else 0f
        val marginOffset = if (size > 0) mPageMargin.toFloat() / size else 0f
        var lastPos = -1
        var lastOffset = 0f
        var lastSize = 0f
        var first = true

        var lastItem: ItemInfo? = null
        var i = 0
        while (i < mItems.size) {
            var ii = mItems[i]
            if (!first && ii.position != lastPos + 1) {
                ii = mTempItem
                ii.offset = lastOffset + lastSize + marginOffset
                ii.position = lastPos + 1
                ii.sizeFactor = mAdapter!!.getPageSize(ii.position)
                i--
            }
            val offset = ii.offset
            val endBound = offset + ii.sizeFactor + marginOffset
            if (first || scrollOffset >= offset) {
                if (scrollOffset < endBound || i == mItems.size - 1) {
                    return ii
                }
            } else {
                return lastItem
            }
            first = false
            lastPos = ii.position
            lastOffset = offset
            lastSize = ii.sizeFactor
            lastItem = ii
            i++
        }

        return lastItem
    }

    internal fun determineTargetPage(
        currentPage: Int,
        pageOffset: Float,
        velocity: Int,
        delta: Int,
    ): Int {
        val directionalVelocity = if (isRtlHorizontal()) -velocity else velocity
        var targetPage =
            if (abs(delta) > mFlingDistance && abs(directionalVelocity) > mMinimumVelocity) {
                if (directionalVelocity > 0) currentPage else currentPage + 1
            } else {
                val truncator = if (currentPage >= mCurItem) 0.4f else 0.6f
                currentPage + (pageOffset + truncator).toInt()
            }

        if (mItems.isNotEmpty()) {
            val firstItem = mItems[0]
            val lastItem = mItems[mItems.size - 1]
            targetPage = max(firstItem.position, min(targetPage, lastItem.position))
        }

        return targetPage
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        var needsInvalidate = false

        val overScrollMode = overScrollMode
        val adapter = mAdapter
        if (overScrollMode == OVER_SCROLL_ALWAYS ||
            (overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && adapter != null && adapter.count > 1)
        ) {
            if (!mStartEdge.isFinished) {
                val restoreCount = canvas.save()
                if (isHorizontal) {
                    val width = width
                    val height = height - paddingTop - paddingBottom
                    if (isRtlHorizontal()) {
                        canvas.rotate(90f)
                        canvas.translate(-paddingTop.toFloat(), (mFirstOffset - 1) * width)
                    } else {
                        canvas.rotate(270f)
                        canvas.translate((-height + paddingTop).toFloat(), mFirstOffset * width)
                    }
                    mStartEdge.setSize(height, width)
                } else {
                    val width = width - paddingLeft - paddingRight
                    val height = height
                    canvas.translate(paddingLeft.toFloat(), mFirstOffset * height)
                    mStartEdge.setSize(width, height)
                }
                needsInvalidate = needsInvalidate or mStartEdge.draw(canvas)
                canvas.restoreToCount(restoreCount)
            }
            if (!mEndEdge.isFinished) {
                val restoreCount = canvas.save()
                if (isHorizontal) {
                    val width = width
                    val height = height - paddingTop - paddingBottom
                    if (isRtlHorizontal()) {
                        canvas.rotate(270f)
                        canvas.translate((-height + paddingTop).toFloat(), -mLastOffset * width)
                    } else {
                        canvas.rotate(90f)
                        canvas.translate(-paddingTop.toFloat(), -(mLastOffset + 1) * width)
                    }
                    mEndEdge.setSize(height, width)
                } else {
                    val width = width - paddingLeft - paddingRight
                    val height = height
                    canvas.rotate(180f)
                    canvas.translate(-(width + paddingLeft).toFloat(), -(mLastOffset + 1) * height)
                    mEndEdge.setSize(width, height)
                }
                needsInvalidate = needsInvalidate or mEndEdge.draw(canvas)
                canvas.restoreToCount(restoreCount)
            }
        } else {
            mStartEdge.finish()
            mEndEdge.finish()
        }

        if (needsInvalidate) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val marginDrawable = mMarginDrawable
        val adapter = mAdapter
        if (mPageMargin > 0 && marginDrawable != null && mItems.isNotEmpty() && adapter != null) {
            val rtl = isRtlHorizontal()
            val scroll = if (isHorizontal) (if (rtl) -scrollX else scrollX) else scrollY
            val size = if (isHorizontal) width else height
            val marginOffset = mPageMargin.toFloat() / size

            var itemIndex = 0
            var ii = mItems[0]
            var offset = ii.offset
            val itemCount = mItems.size
            val firstPos = ii.position
            val lastPos = mItems[itemCount - 1].position

            for (pos in firstPos until lastPos) {
                while (pos > ii.position && itemIndex < itemCount) {
                    ii = mItems[++itemIndex]
                }

                val drawAt: Float
                if (pos == ii.position) {
                    drawAt = (ii.offset + ii.sizeFactor) * size
                    offset = ii.offset + ii.sizeFactor + marginOffset
                } else {
                    val sizeFactor = adapter.getPageSize(pos)
                    drawAt = (offset + sizeFactor) * size
                    offset += sizeFactor + marginOffset
                }

                if (drawAt + mPageMargin > scroll) {
                    if (isHorizontal) {
                        val left = if (rtl) (size - drawAt - mPageMargin).roundToInt() else drawAt.roundToInt()
                        marginDrawable.setBounds(
                            left,
                            mTopPageBounds,
                            left + mPageMargin,
                            mBottomPageBounds,
                        )
                    } else {
                        marginDrawable.setBounds(
                            mLeftPageBounds,
                            drawAt.roundToInt(),
                            mRightPageBounds,
                            (drawAt + mPageMargin).roundToInt(),
                        )
                    }
                    marginDrawable.draw(canvas)
                }
                if (drawAt > scroll + size) {
                    break
                }
            }
        }
    }

    fun beginFakeDrag(): Boolean {
        if (mIsBeingDragged) return false
        mFakeDragging = true
        setScrollState(SCROLL_STATE_DRAGGING)
        if (isHorizontal) {
            mLastMotionX = 0f
            mInitialMotionX = 0f
        } else {
            mLastMotionY = 0f
            mInitialMotionY = 0f
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        } else {
            mVelocityTracker?.clear()
        }
        val time = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        mVelocityTracker?.addMovement(ev)
        ev.recycle()
        mFakeDragBeginTime = time
        return true
    }

    fun endFakeDrag() {
        check(mFakeDragging) { "No fake drag in progress. Call beginFakeDrag first." }

        if (mAdapter != null) {
            val velocityTracker = mVelocityTracker!!
            velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
            mPopulatePending = true
            val initialVelocity: Int
            val size = getClientSize()
            val scroll: Int
            val totalDelta: Int
            if (isHorizontal) {
                initialVelocity = velocityTracker.getXVelocity(mActivePointerId).toInt()
                scroll = scrollX
                totalDelta = (mLastMotionX - mInitialMotionX).toInt()
            } else {
                initialVelocity = velocityTracker.getYVelocity(mActivePointerId).toInt()
                scroll = scrollY
                totalDelta = (mLastMotionY - mInitialMotionY).toInt()
            }

            val ii = infoForCurrentScrollPosition()!!
            val marginOffset = if (size > 0) mPageMargin.toFloat() / size else 0f
            val currentPage = ii.position
            val pageOffset =
                if (size > 0) {
                    ((scroll.toFloat() / size) - ii.offset) / (ii.sizeFactor + marginOffset)
                } else {
                    0f
                }
            val nextPage = determineTargetPage(currentPage, pageOffset, initialVelocity, totalDelta)
            setCurrentItemInternal(nextPage, smoothScroll = true, always = true, velocity = initialVelocity)
        }
        endDrag()
        mFakeDragging = false
    }

    fun fakeDragBy(offset: Float) {
        check(mFakeDragging) { "No fake drag in progress. Call beginFakeDrag first." }

        val adapter = mAdapter ?: return
        val size = getClientSize()
        val oldScroll: Float
        if (isHorizontal) {
            mLastMotionX += offset
            oldScroll = scrollX.toFloat()
        } else {
            mLastMotionY += offset
            oldScroll = scrollY.toFloat()
        }

        var scroll = oldScroll - offset
        var startBound = size * mFirstOffset
        var endBound = size * mLastOffset

        val firstItem = mItems[0]
        val lastItem = mItems[mItems.size - 1]
        if (firstItem.position != 0) {
            startBound = firstItem.offset * size
        }
        if (lastItem.position != adapter.count - 1) {
            endBound = lastItem.offset * size
        }

        if (scroll < startBound) {
            scroll = startBound
        } else if (scroll > endBound) {
            scroll = endBound
        }

        val time = SystemClock.uptimeMillis()
        val ev =
            if (isHorizontal) {
                mLastMotionX += scroll - scroll.toInt()
                scrollTo(scroll.toInt(), scrollY)
                pageScrolled(scroll.toInt(), 0)
                MotionEvent.obtain(mFakeDragBeginTime, time, MotionEvent.ACTION_MOVE, mLastMotionX, 0f, 0)
            } else {
                mLastMotionY += scroll - scroll.toInt()
                scrollTo(scrollX, scroll.toInt())
                pageScrolled(0, scroll.toInt())
                MotionEvent.obtain(mFakeDragBeginTime, time, MotionEvent.ACTION_MOVE, 0f, mLastMotionY, 0)
            }

        mVelocityTracker?.addMovement(ev)
        ev.recycle()
    }

    val isFakeDragging: Boolean
        get() = mFakeDragging

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            if (isHorizontal) {
                mLastMotionX = ev.getX(newPointerIndex)
            } else {
                mLastMotionY = ev.getY(newPointerIndex)
            }
            mActivePointerId = ev.getPointerId(newPointerIndex)
            mVelocityTracker?.clear()
        }
    }

    private fun endDrag() {
        mIsBeingDragged = false
        mIsUnableToDrag = false
        mVelocityTracker?.recycle()
        mVelocityTracker = null
    }

    private fun setScrollingCacheEnabled(enabled: Boolean) {
        if (mScrollingCacheEnabled != enabled) {
            mScrollingCacheEnabled = enabled
            if (USE_CACHE) {
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    if (child.visibility != GONE) {
                        @Suppress("DEPRECATION")
                        child.isDrawingCacheEnabled = enabled
                    }
                }
            }
        }
    }

    internal fun isRtlHorizontal(): Boolean = isHorizontal && layoutDirection == LAYOUT_DIRECTION_RTL

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        if (mInitialized && isHorizontal) {
            // Defer scroll recalculation until layout to avoid re-entering View.resolvePadding().
            mPendingScrollToCurrentItem = true
            requestLayout()
        }
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        if (mAdapter == null || !isHorizontal) return false
        val width = getClientWidth()
        val scrollX = scrollX
        return if (isRtlHorizontal()) {
            when {
                direction < 0 -> scrollX > -(width * mLastOffset).toInt()
                direction > 0 -> scrollX < -(width * mFirstOffset).toInt()
                else -> false
            }
        } else {
            when {
                direction < 0 -> scrollX > (width * mFirstOffset).toInt()
                direction > 0 -> scrollX < (width * mLastOffset).toInt()
                else -> false
            }
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (mAdapter == null || isHorizontal) return false
        val height = getClientHeight()
        val scrollY = scrollY
        return when {
            direction < 0 -> scrollY > (height * mFirstOffset).toInt()
            direction > 0 -> scrollY < (height * mLastOffset).toInt()
            else -> false
        }
    }

    internal fun canScrollDirection(direction: Int): Boolean =
        if (isHorizontal) canScrollHorizontally(direction) else canScrollVertically(direction)

    private fun canScrollForward(): Boolean = if (isRtlHorizontal()) canScrollHorizontally(-1) else canScrollDirection(1)

    private fun canScrollBackward(): Boolean = if (isRtlHorizontal()) canScrollHorizontally(1) else canScrollDirection(-1)

    protected open fun canScroll(
        v: View,
        checkV: Boolean,
        delta: Int,
        x: Int,
        y: Int,
    ): Boolean {
        if (v is ViewGroup) {
            val scrollX = v.scrollX
            val scrollY = v.scrollY
            for (i in v.childCount - 1 downTo 0) {
                val child = v.getChildAt(i)
                if (x + scrollX >= child.left &&
                    x + scrollX < child.right &&
                    y + scrollY >= child.top &&
                    y + scrollY < child.bottom &&
                    canScroll(child, true, delta, x + scrollX - child.left, y + scrollY - child.top)
                ) {
                    return true
                }
            }
        }

        return checkV && ((isHorizontal && v.canScrollHorizontally(-delta)) || (!isHorizontal && v.canScrollVertically(-delta)))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = super.dispatchKeyEvent(event) || executeKeyEvent(event)

    open fun executeKeyEvent(event: KeyEvent): Boolean {
        if (!isUserInputEnabled) return false
        var handled = false
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isHorizontal) {
                        val alt = event.hasModifiers(KeyEvent.META_ALT_ON)
                        handled =
                            if (alt) {
                                if (isRtlHorizontal()) pageRight() else pageLeft()
                            } else {
                                arrowScroll(FOCUS_LEFT)
                            }
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isHorizontal) {
                        val alt = event.hasModifiers(KeyEvent.META_ALT_ON)
                        handled =
                            if (alt) {
                                if (isRtlHorizontal()) pageLeft() else pageRight()
                            } else {
                                arrowScroll(FOCUS_RIGHT)
                            }
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!isHorizontal) {
                        handled = if (event.hasModifiers(KeyEvent.META_ALT_ON)) pageLeft() else arrowScroll(FOCUS_UP)
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!isHorizontal) {
                        handled = if (event.hasModifiers(KeyEvent.META_ALT_ON)) pageRight() else arrowScroll(FOCUS_DOWN)
                    }
                }

                KeyEvent.KEYCODE_TAB -> {
                    if (event.hasNoModifiers()) {
                        handled = arrowScroll(FOCUS_FORWARD)
                    } else if (event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
                        handled = arrowScroll(FOCUS_BACKWARD)
                    }
                }
            }
        }
        return handled
    }

    open fun arrowScroll(direction: Int): Boolean {
        var currentFocused = findFocus()
        if (currentFocused === this) {
            currentFocused = null
        } else if (currentFocused != null) {
            var isChild = false
            var parent = currentFocused.parent
            while (parent is ViewGroup) {
                if (parent === this) {
                    isChild = true
                    break
                }
                parent = parent.parent
            }
            if (!isChild) {
                val sb = StringBuilder().append(currentFocused.javaClass.simpleName)
                var p = currentFocused.parent
                while (p is ViewGroup) {
                    sb.append(" => ").append(p.javaClass.simpleName)
                    p = p.parent
                }
                Log.e(TAG, "arrowScroll tried to find focus based on non-child current focused view $sb")
                currentFocused = null
            }
        }

        var handled = false
        val nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction)
        if (nextFocused != null && nextFocused !== currentFocused) {
            when (direction) {
                FOCUS_LEFT -> {
                    val nextLeft = getChildRectInPagerCoordinates(mTempRect, nextFocused).left
                    val currLeft = getChildRectInPagerCoordinates(mTempRect, currentFocused).left
                    handled =
                        if (currentFocused != null && nextLeft >= currLeft) {
                            if (isRtlHorizontal()) pageRight() else pageLeft()
                        } else {
                            nextFocused.requestFocus()
                        }
                }

                FOCUS_RIGHT -> {
                    val nextLeft = getChildRectInPagerCoordinates(mTempRect, nextFocused).left
                    val currLeft = getChildRectInPagerCoordinates(mTempRect, currentFocused).left
                    handled =
                        if (currentFocused != null && nextLeft <= currLeft) {
                            if (isRtlHorizontal()) pageLeft() else pageRight()
                        } else {
                            nextFocused.requestFocus()
                        }
                }

                FOCUS_UP -> {
                    val nextTop = getChildRectInPagerCoordinates(mTempRect, nextFocused).top
                    val currTop = getChildRectInPagerCoordinates(mTempRect, currentFocused).top
                    handled =
                        if (currentFocused != null && nextTop >= currTop) {
                            pageLeft()
                        } else {
                            nextFocused.requestFocus()
                        }
                }

                FOCUS_DOWN -> {
                    val nextTop = getChildRectInPagerCoordinates(mTempRect, nextFocused).top
                    val currTop = getChildRectInPagerCoordinates(mTempRect, currentFocused).top
                    handled =
                        if (currentFocused != null && nextTop <= currTop) {
                            pageRight()
                        } else {
                            nextFocused.requestFocus()
                        }
                }
            }
        } else if (direction == FOCUS_LEFT) {
            handled = if (isRtlHorizontal()) pageRight() else pageLeft()
        } else if (direction == FOCUS_RIGHT) {
            handled = if (isRtlHorizontal()) pageLeft() else pageRight()
        } else if (direction == FOCUS_UP || direction == FOCUS_BACKWARD) {
            handled = pageLeft()
        } else if (direction == FOCUS_DOWN || direction == FOCUS_FORWARD) {
            handled = pageRight()
        }

        if (handled) {
            playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction))
        }
        return handled
    }

    private fun getChildRectInPagerCoordinates(
        outRect: Rect?,
        child: View?,
    ): Rect {
        val rect = outRect ?: Rect()
        if (child == null) {
            rect.set(0, 0, 0, 0)
            return rect
        }
        rect.left = child.left
        rect.right = child.right
        rect.top = child.top
        rect.bottom = child.bottom

        var parent = child.parent
        while (parent is ViewGroup && parent !== this) {
            rect.left += parent.left
            rect.right += parent.right
            rect.top += parent.top
            rect.bottom += parent.bottom
            parent = parent.parent
        }
        return rect
    }

    internal fun pageLeft(): Boolean {
        if (mCurItem > 0) {
            setCurrentItem(mCurItem - 1, true)
            return true
        }
        return false
    }

    internal fun pageRight(): Boolean {
        val adapter = mAdapter
        if (adapter != null && mCurItem < adapter.count - 1) {
            setCurrentItem(mCurItem + 1, true)
            return true
        }
        return false
    }

    override fun addFocusables(
        views: ArrayList<View>,
        direction: Int,
        focusableMode: Int,
    ) {
        val focusableCount = views.size
        val descendantFocusability = descendantFocusability

        if (descendantFocusability != FOCUS_BLOCK_DESCENDANTS) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == VISIBLE) {
                    val ii = infoForChild(child)
                    if (ii != null && ii.position == mCurItem) {
                        child.addFocusables(views, direction, focusableMode)
                    }
                }
            }
        }

        if (descendantFocusability != FOCUS_AFTER_DESCENDANTS || focusableCount == views.size) {
            if (!isFocusable) return
            if ((focusableMode and FOCUSABLES_TOUCH_MODE) == FOCUSABLES_TOUCH_MODE &&
                isInTouchMode && !isFocusableInTouchMode
            ) {
                return
            }
            views.add(this)
        }
    }

    override fun addTouchables(views: ArrayList<View>) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == VISIBLE) {
                val ii = infoForChild(child)
                if (ii != null && ii.position == mCurItem) {
                    child.addTouchables(views)
                }
            }
        }
    }

    override fun onRequestFocusInDescendants(
        direction: Int,
        previouslyFocusedRect: Rect?,
    ): Boolean {
        val count = childCount
        val index: Int
        val increment: Int
        val end: Int
        if ((direction and FOCUS_FORWARD) != 0) {
            index = 0
            increment = 1
            end = count
        } else {
            index = count - 1
            increment = -1
            end = -1
        }
        var i = index
        while (i != end) {
            val child = getChildAt(i)
            if (child.visibility == VISIBLE) {
                val ii = infoForChild(child)
                if (ii != null && ii.position == mCurItem) {
                    if (child.requestFocus(direction, previouslyFocusedRect)) {
                        return true
                    }
                }
            }
            i += increment
        }
        return false
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return super.dispatchPopulateAccessibilityEvent(event)
        }

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == VISIBLE) {
                val ii = infoForChild(child)
                if (ii != null && ii.position == mCurItem && child.dispatchPopulateAccessibilityEvent(event)) {
                    return true
                }
            }
        }
        return false
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams()

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = generateDefaultLayoutParams()

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean = p is LayoutParams && super.checkLayoutParams(p)

    override fun generateLayoutParams(attrs: AttributeSet): ViewGroup.LayoutParams = LayoutParams(context, attrs)

    // ---- NestedScrollingChild3 ----

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = mChildHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = startNestedScroll(axes, ViewCompat.TYPE_TOUCH)

    override fun startNestedScroll(
        axes: Int,
        type: Int,
    ): Boolean = mChildHelper.startNestedScroll(axes, type)

    override fun stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }

    override fun stopNestedScroll(type: Int) {
        mChildHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(): Boolean = hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)

    override fun hasNestedScrollingParent(type: Int): Boolean = mChildHelper.hasNestedScrollingParent(type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean = mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray,
    ) {
        mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
    ): Boolean = dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean = mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(
        velocityX: Float,
        velocityY: Float,
    ): Boolean = mChildHelper.dispatchNestedPreFling(velocityX, velocityY)

    // ---- NestedScrollingParent3 ----

    override fun getNestedScrollAxes(): Int = mParentHelper.nestedScrollAxes

    override fun onStartNestedScroll(
        child: View,
        target: View,
        axes: Int,
    ): Boolean = onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH)

    override fun onStartNestedScroll(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean {
        if (!isUserInputEnabled || type != ViewCompat.TYPE_TOUCH) return false
        val expectedAxis = if (isHorizontal) ViewCompat.SCROLL_AXIS_HORIZONTAL else ViewCompat.SCROLL_AXIS_VERTICAL
        return (axes and expectedAxis) != 0
    }

    override fun onNestedScrollAccepted(
        child: View,
        target: View,
        axes: Int,
    ) {
        onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH)
    }

    override fun onNestedScrollAccepted(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ) {
        mParentHelper.onNestedScrollAccepted(child, target, axes, type)
        mNestedScrollInProgress = true
        val expectedAxis = if (isHorizontal) ViewCompat.SCROLL_AXIS_HORIZONTAL else ViewCompat.SCROLL_AXIS_VERTICAL
        startNestedScroll(expectedAxis, type)
    }

    override fun onNestedPreScroll(
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
    ) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH)
    }

    override fun onNestedPreScroll(
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        dispatchNestedPreScroll(dx, dy, consumed, null, type)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
    ) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH, mNestedConsumed)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
    ) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, mNestedConsumed)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        if (mItems.isEmpty() || mAdapter == null) {
            dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, null, type, consumed)
            return
        }

        if (isHorizontal && dxUnconsumed != 0) {
            setScrollState(SCROLL_STATE_DRAGGING)
            val oldScrollX = scrollX
            val unconsumed = performDragDeltaX(dxUnconsumed.toFloat())
            val myConsumedX = scrollX - oldScrollX
            consumed[0] += myConsumedX
            val remainingX = unconsumed.toInt()
            val parentConsumedBefore = consumed[0]
            dispatchNestedScroll(
                dxConsumed + myConsumedX,
                dyConsumed,
                remainingX,
                dyUnconsumed,
                null,
                type,
                consumed,
            )
            val parentConsumedDelta = consumed[0] - parentConsumedBefore
            if (pullHorizontalEdgeIfNeeded(unconsumed - parentConsumedDelta)) {
                postInvalidateOnAnimation()
            }
        } else if (!isHorizontal && dyUnconsumed != 0) {
            setScrollState(SCROLL_STATE_DRAGGING)
            val oldScrollY = scrollY
            val unconsumed = performDragDeltaY(dyUnconsumed.toFloat())
            val myConsumedY = scrollY - oldScrollY
            consumed[1] += myConsumedY
            val remainingY = unconsumed.toInt()
            dispatchNestedScroll(
                dxConsumed,
                dyConsumed + myConsumedY,
                dxUnconsumed,
                remainingY,
                null,
                type,
                consumed,
            )
        } else {
            dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, null, type, consumed)
        }
    }

    override fun onStopNestedScroll(target: View) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH)
    }

    override fun onStopNestedScroll(
        target: View,
        type: Int,
    ) {
        mParentHelper.onStopNestedScroll(target, type)
        if (mNestedScrollInProgress) {
            mNestedScrollInProgress = false
            if (mItems.isNotEmpty() && mAdapter != null) {
                val size = getClientSize()
                val ii = infoForCurrentScrollPosition()
                if (ii != null && size > 0) {
                    val scroll = if (isHorizontal) (if (isRtlHorizontal()) -scrollX else scrollX) else scrollY
                    val marginOffset = mPageMargin.toFloat() / size
                    val pageOffset = ((scroll.toFloat() / size) - ii.offset) / (ii.sizeFactor + marginOffset)
                    val nextPage = determineTargetPage(ii.position, pageOffset, 0, 0)
                    setCurrentItemInternal(nextPage, smoothScroll = true, always = true)
                }
            }
            mStartEdge.onRelease()
            mEndEdge.onRelease()
        }
        stopNestedScroll(type)
    }

    override fun onNestedPreFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
    ): Boolean = dispatchNestedPreFling(velocityX, velocityY)

    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean = dispatchNestedFling(velocityX, velocityY, consumed)

    internal inner class MyAccessibilityDelegate : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityEvent(
            host: View,
            event: AccessibilityEvent,
        ) {
            super.onInitializeAccessibilityEvent(host, event)
            event.className = DirectionalViewPager::class.java.name
            event.isScrollable = canScroll()
            val adapter = mAdapter
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && adapter != null) {
                event.itemCount = adapter.count
                event.fromIndex = mCurItem
                event.toIndex = mCurItem
            }
        }

        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat,
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = DirectionalViewPager::class.java.name
            info.isScrollable = canScroll()
            if (isUserInputEnabled && canScrollForward()) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
            }
            if (isUserInputEnabled && canScrollBackward()) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
            }
        }

        override fun performAccessibilityAction(
            host: View,
            action: Int,
            args: Bundle?,
        ): Boolean {
            if (super.performAccessibilityAction(host, action, args)) {
                return true
            }
            if (!isUserInputEnabled) {
                return false
            }
            return when (action) {
                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> {
                    if (canScrollForward()) {
                        currentItem = mCurItem + 1
                        true
                    } else {
                        false
                    }
                }

                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> {
                    if (canScrollBackward()) {
                        currentItem = mCurItem - 1
                        true
                    } else {
                        false
                    }
                }

                else -> {
                    false
                }
            }
        }

        private fun canScroll(): Boolean {
            val adapter = mAdapter
            return isUserInputEnabled && adapter != null && adapter.count > 1
        }
    }

    private inner class PagerObserver : DataSetObserver() {
        override fun onChanged() {
            dataSetChanged()
        }

        override fun onInvalidated() {
            dataSetChanged()
        }
    }

    class LayoutParams : ViewGroup.LayoutParams {
        @JvmField
        var isDecor: Boolean = false

        @JvmField
        var gravity: Int = Gravity.TOP

        internal var sizeFactor: Float = 0f
        internal var needsMeasure: Boolean = false
        internal var position: Int = 0
        internal var childIndex: Int = 0

        constructor() : super(MATCH_PARENT, MATCH_PARENT)

        constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
            val a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS)
            gravity = a.getInteger(0, Gravity.TOP)
            a.recycle()
        }
    }

    internal class ViewPositionComparator : Comparator<View> {
        override fun compare(
            lhs: View,
            rhs: View,
        ): Int {
            val llp = lhs.layoutParams as LayoutParams
            val rlp = rhs.layoutParams as LayoutParams
            if (llp.isDecor != rlp.isDecor) {
                return if (llp.isDecor) 1 else -1
            }
            return llp.position - rlp.position
        }
    }

    companion object {
        private const val TAG = "DirectionalViewPager"
        private const val DEBUG = false
        private const val USE_CACHE = false

        private const val DEFAULT_OFFSCREEN_PAGES = 1
        private const val MAX_SETTLE_DURATION = 600
        private const val MIN_DISTANCE_FOR_FLING = 25
        private const val DEFAULT_GUTTER_SIZE = 16
        private const val MIN_FLING_VELOCITY = 400
        private const val INVALID_POINTER = -1
        private const val CLOSE_ENOUGH = 2

        private const val DRAW_ORDER_DEFAULT = 0
        private const val DRAW_ORDER_FORWARD = 1
        private const val DRAW_ORDER_REVERSE = 2

        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2

        private val LAYOUT_ATTRS = intArrayOf(android.R.attr.layout_gravity)

        private val COMPARATOR = Comparator<ItemInfo> { lhs, rhs -> lhs.position - rhs.position }

        private val sInterpolator =
            Interpolator { t ->
                val shifted = t - 1.0f
                shifted * shifted * shifted * shifted * shifted + 1.0f
            }

        private val sPositionComparator = ViewPositionComparator()

        private fun isDecorView(view: View): Boolean = view.javaClass.getAnnotation(ViewPager.DecorView::class.java) != null
    }
}
