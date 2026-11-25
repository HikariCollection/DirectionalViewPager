# Configure nested scrolling, RTL, and input control

Use this guide to integrate `DirectionalViewPager` with nested scrollable containers, support right-to-left (RTL) locales, lock user gestures, and drive programmatic drags.
All behaviors work with standard `PagerAdapter` implementations and require no adapter subclass changes.

## Cooperate with nested parents and children

`DirectionalViewPager` implements both `NestedScrollingChild3` and `NestedScrollingParent3` with `isNestedScrollingEnabled` enabled by default.

1. Embed scrollable children such as `NestedScrollView` or `RecyclerView` inside pages returned by `PagerAdapter.instantiateItem`.
2. When a child hits its scroll boundary during a `TYPE_TOUCH` drag along the active pager axis, unconsumed delta automatically hands off to `DirectionalViewPager`.
3. Wrap `DirectionalViewPager` inside `CoordinatorLayout` or `SwipeRefreshLayout` when outer containers should consume pre-scroll or overscroll deltas first.

> [!NOTE]
> `onStartNestedScroll` accepts `ViewCompat.TYPE_TOUCH` along the active axis when `isUserInputEnabled` is `true`. Fling momentum inside a child `RecyclerView` (`TYPE_NON_TOUCH`) stops at the child's edge instead of unintentionally flipping pages.

## Support RTL layout direction

Enable horizontal RTL layout mirroring through standard Android `android:layoutDirection="rtl"` or programmatically at runtime:

```kotlin
pager.isHorizontal = true
pager.layoutDirection = View.LAYOUT_DIRECTION_RTL
```

In horizontal RTL mode:

- Adapter indices remain logical (`0` through `count - 1`), with page `0` positioned on the right and subsequent pages extending to the left.
- Drag bounds, fling velocity, `EdgeEffect` glow edges, `pageMargin` drawables, `canScrollHorizontally`, DPAD left/right keys, and accessibility scroll actions mirror automatically.
- Vertical mode (`isHorizontal = false`) remains top-to-bottom regardless of `layoutDirection`.

## Lock user input while preserving programmatic navigation

Disable touch swipes, DPAD navigation, and accessibility scroll actions without blocking code-driven transitions:

```kotlin
pager.isUserInputEnabled = false
pager.setCurrentItem(2, true)
```

If `isUserInputEnabled` is set to `false` mid-drag, the pager snaps back to `currentItem` and releases active touch state.

## Drive synchronized fake drags

Synchronize page motion with an external `ValueAnimator` or sibling touch handler:

```kotlin
if (pager.beginFakeDrag()) {
    pager.fakeDragBy(-deltaPx)
    pager.endFakeDrag()
}
```

Pass negative pixel deltas to advance in LTR horizontal or vertical mode, and positive deltas to advance in RTL horizontal mode.
