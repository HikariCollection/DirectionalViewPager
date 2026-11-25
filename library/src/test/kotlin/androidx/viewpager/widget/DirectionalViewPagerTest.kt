package androidx.viewpager.widget

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DirectionalViewPagerTest {
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        val displayMetrics =
            DisplayMetrics().apply {
                density = 2.0f
            }
        val resources =
            mockk<Resources>(relaxed = true) {
                every { this@mockk.displayMetrics } returns displayMetrics
            }
        context =
            mockk<Context>(relaxed = true) {
                every { this@mockk.resources } returns resources
            }

        val viewConfig =
            mockk<ViewConfiguration>(relaxed = true) {
                every { scaledPagingTouchSlop } returns 16
                every { scaledMaximumFlingVelocity } returns 8000
            }
        mockkStatic(ViewConfiguration::class)
        every { ViewConfiguration.get(any()) } returns viewConfig
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun buildPager(
        horizontal: Boolean = true,
        pageCount: Int = 3,
        width: Int = 1000,
        height: Int = 2000,
        scrollXValue: Int = 0,
        scrollYValue: Int = 0,
    ): DirectionalViewPager {
        val pager = spyk(DirectionalViewPager(context, horizontal))
        every { pager.measuredWidth } returns width
        every { pager.measuredHeight } returns height
        every { pager.paddingLeft } returns 0
        every { pager.paddingRight } returns 0
        every { pager.paddingTop } returns 0
        every { pager.paddingBottom } returns 0
        every { pager.scrollX } returns scrollXValue
        every { pager.scrollY } returns scrollYValue
        every { pager.layoutDirection } returns View.LAYOUT_DIRECTION_LTR

        val adapter =
            mockk<PagerAdapter>(relaxed = true) {
                every { count } returns pageCount
                every { getPageWidth(any()) } returns 1.0f
            }
        pager.mAdapter = adapter
        pager.mItems.clear()
        for (i in 0 until pageCount) {
            pager.mItems.add(
                DirectionalViewPager.ItemInfo().apply {
                    position = i
                    sizeFactor = 1.0f
                    offset = i.toFloat()
                },
            )
        }
        pager.mFirstOffset = 0f
        pager.mLastOffset = (pageCount - 1).toFloat()
        return pager
    }

    @Test
    fun `canScrollHorizontally respects bounds and orientation`() {
        val atStart = buildPager(horizontal = true, scrollXValue = 0)
        assertFalse(atStart.canScrollHorizontally(-1))
        assertTrue(atStart.canScrollHorizontally(1))
        assertFalse(atStart.canScrollHorizontally(0))

        val atEnd = buildPager(horizontal = true, scrollXValue = 2000)
        assertTrue(atEnd.canScrollHorizontally(-1))
        assertFalse(atEnd.canScrollHorizontally(1))

        val vertical = buildPager(horizontal = false, scrollXValue = 500)
        assertFalse(vertical.canScrollHorizontally(1))
        assertFalse(vertical.canScrollHorizontally(-1))
    }

    @Test
    fun `canScrollVertically reads scrollY and height instead of scrollX and width`() {
        val scrolledOnlyHorizontally =
            buildPager(
                horizontal = false,
                scrollXValue = 500,
                scrollYValue = 0,
            )
        assertFalse(scrolledOnlyHorizontally.canScrollVertically(-1))
        assertTrue(scrolledOnlyHorizontally.canScrollVertically(1))

        val scrolledVertically =
            buildPager(
                horizontal = false,
                scrollXValue = 0,
                scrollYValue = 100,
            )
        assertTrue(scrolledVertically.canScrollVertically(-1))
        assertTrue(scrolledVertically.canScrollVertically(1))

        val atBottom =
            buildPager(
                horizontal = false,
                pageCount = 3,
                height = 2000,
                scrollYValue = 4000,
            )
        assertTrue(atBottom.canScrollVertically(-1))
        assertFalse(atBottom.canScrollVertically(1))
    }

    @Test
    fun `canScrollVertically returns false when horizontal or adapter null`() {
        val horizontal = buildPager(horizontal = true, scrollYValue = 500)
        assertFalse(horizontal.canScrollVertically(1))
        assertFalse(horizontal.canScrollVertically(-1))

        val noAdapter = buildPager(horizontal = false, scrollYValue = 500)
        noAdapter.mAdapter = null
        assertFalse(noAdapter.canScrollVertically(1))
    }

    @Test
    fun `determineTargetPage advances on sufficient fling`() {
        val pager = buildPager(horizontal = false, pageCount = 4)
        pager.mCurItem = 1

        // Fling toward next page (negative delta and negative velocity > min 400 * density = 800)
        val nextPage =
            pager.determineTargetPage(
                currentPage = 1,
                pageOffset = 0.2f,
                velocity = -1200,
                delta = -100,
            )
        assertEquals(2, nextPage)

        // Fling toward previous page (positive delta and positive velocity)
        val prevPage =
            pager.determineTargetPage(
                currentPage = 1,
                pageOffset = 0.8f,
                velocity = 1200,
                delta = 100,
            )
        assertEquals(1, prevPage)
    }

    @Test
    fun `determineTargetPage stays on page for short drag and clamps bounds`() {
        val pager = buildPager(horizontal = true, pageCount = 3)
        pager.mCurItem = 1

        val stay =
            pager.determineTargetPage(
                currentPage = 1,
                pageOffset = 0.2f,
                velocity = 100,
                delta = -10,
            )
        assertEquals(1, stay)

        val clampedEnd =
            pager.determineTargetPage(
                currentPage = 2,
                pageOffset = 0.9f,
                velocity = -2000,
                delta = -200,
            )
        assertEquals(2, clampedEnd)
    }

    @Test
    fun `calculatePageOffsets sets first and last offset for single page`() {
        val pager = buildPager(horizontal = false, pageCount = 1)
        val onlyItem = pager.mItems[0]
        onlyItem.offset = 0f

        pager.calculatePageOffsets(onlyItem, 0, null)

        assertEquals(0f, pager.mFirstOffset, 1e-5f)
        assertEquals(0f, pager.mLastOffset, 1e-5f)
    }

    @Test
    fun `calculatePageOffsets uses clientHeight for margin offset in vertical mode`() {
        val pager = buildPager(horizontal = false, pageCount = 3, width = 1000, height = 2000)
        pager.pageMargin = 200 // marginOffset = 200 / 2000 = 0.1f

        val curItem = pager.mItems[0]
        curItem.offset = 0f
        pager.calculatePageOffsets(curItem, 0, null)

        assertEquals(0f, pager.mItems[0].offset, 1e-5f)
        assertEquals(1.1f, pager.mItems[1].offset, 1e-5f)
        assertEquals(2.2f, pager.mItems[2].offset, 1e-5f)
        assertEquals(0f, pager.mFirstOffset, 1e-5f)
        assertEquals(2.2f, pager.mLastOffset, 1e-5f)
    }

    @Test
    fun `isHorizontal toggle updates orientation and resets scroll`() {
        val pager = buildPager(horizontal = true)
        assertTrue(pager.isHorizontal)

        pager.isHorizontal = false
        assertFalse(pager.isHorizontal)

        pager.isHorizontal = true
        assertTrue(pager.isHorizontal)
    }

    @Test
    fun `isUserInputEnabled blocks touch and key events while preserving programmatic paging`() {
        val pager = buildPager(horizontal = true, pageCount = 3)
        pager.isUserInputEnabled = false

        val downEvent =
            mockk<MotionEvent>(relaxed = true) {
                every { action } returns MotionEvent.ACTION_DOWN
                every { actionMasked } returns MotionEvent.ACTION_DOWN
            }
        val keyEvent =
            mockk<KeyEvent>(relaxed = true) {
                every { action } returns KeyEvent.ACTION_DOWN
                every { keyCode } returns KeyEvent.KEYCODE_DPAD_RIGHT
            }

        assertFalse(pager.onInterceptTouchEvent(downEvent))
        assertFalse(pager.onTouchEvent(downEvent))
        assertFalse(pager.executeKeyEvent(keyEvent))

        pager.currentItem = 1
        assertEquals(1, pager.currentItem)
    }

    @Test
    fun `RTL horizontal mode mirrors scroll bounds and fling direction`() {
        val atStartRtl = buildPager(horizontal = true, pageCount = 3, width = 1000, scrollXValue = 0)
        every { atStartRtl.layoutDirection } returns View.LAYOUT_DIRECTION_RTL
        assertTrue(atStartRtl.canScrollHorizontally(-1))
        assertFalse(atStartRtl.canScrollHorizontally(1))

        val atEndRtl = buildPager(horizontal = true, pageCount = 3, width = 1000, scrollXValue = -2000)
        every { atEndRtl.layoutDirection } returns View.LAYOUT_DIRECTION_RTL
        assertFalse(atEndRtl.canScrollHorizontally(-1))
        assertTrue(atEndRtl.canScrollHorizontally(1))

        atStartRtl.mCurItem = 1
        val nextRtl =
            atStartRtl.determineTargetPage(
                currentPage = 1,
                pageOffset = 0.2f,
                velocity = 1200,
                delta = 100,
            )
        assertEquals(2, nextRtl)
    }

    @Test
    fun `nestedScrollingParent3 filters axis and consumes unconsumed child scroll`() {
        val pager = buildPager(horizontal = false, pageCount = 3, height = 2000, scrollYValue = 0)
        val child = mockk<View>(relaxed = true)

        assertTrue(pager.onStartNestedScroll(child, child, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
        assertFalse(pager.onStartNestedScroll(child, child, ViewCompat.SCROLL_AXIS_HORIZONTAL, ViewCompat.TYPE_TOUCH))
        assertFalse(pager.onStartNestedScroll(child, child, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH))

        pager.isUserInputEnabled = false
        assertFalse(pager.onStartNestedScroll(child, child, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
        pager.isUserInputEnabled = true

        pager.onNestedScrollAccepted(child, child, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
        val consumed = IntArray(2)
        pager.onNestedScroll(child, 0, 0, 0, 200, ViewCompat.TYPE_TOUCH, consumed)

        verify { pager.scrollTo(0, 200) }
    }

    @Test
    fun `onRtlPropertiesChanged does not access padding synchronously to avoid resolvePadding recursion`() {
        val pager = buildPager(horizontal = true, pageCount = 3, width = 1000)
        every { pager.paddingLeft } throws AssertionError("paddingLeft must not be read inside onRtlPropertiesChanged")

        pager.onRtlPropertiesChanged(View.LAYOUT_DIRECTION_RTL)

        verify { pager.requestLayout() }
    }
}
