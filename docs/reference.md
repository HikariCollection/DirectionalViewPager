# API, XML, and build reference

Authoritative specification for `DirectionalViewPager` XML attributes, Kotlin/Java API members, Gradle coordinates, and repository automation tasks.

## Gradle coordinates and build requirements

| Item | Value | Source |
| :--- | :--- | :--- |
| **Maven group ID** | `androidx.viewpager.widget` | [library/build.gradle.kts](../library/build.gradle.kts) |
| **Maven artifact ID** | `directional-viewpager` | [library/build.gradle.kts](../library/build.gradle.kts) |
| **Library version** | `2.0.0` | [library/build.gradle.kts](../library/build.gradle.kts) |
| **Minimum SDK** | `23` | [libs.versions.toml](../gradle/libs.versions.toml) |
| **Compile / Target SDK** | `37` | [libs.versions.toml](../gradle/libs.versions.toml) |
| **Java bytecode target** | `17` | [library/build.gradle.kts](../library/build.gradle.kts) |
| **License** | Apache-2.0 | [LICENSE](../LICENSE) |

## XML styleable attributes

Defined in [attrs.xml](../library/src/main/res/values/attrs.xml):

| Attribute | Format | Default | Description |
| :--- | :--- | :--- | :--- |
| `android:orientation` | `horizontal` \| `vertical` | `horizontal` (`0`) | Initial scroll axis (`1` sets `isHorizontal = false`) |
| `app:userInputEnabled` | `boolean` | `true` | Enables or disables touch, DPAD, and accessibility scrolling |

## Class API (`androidx.viewpager.widget.DirectionalViewPager`)

<details>
<summary><strong>Constructors, properties, methods, and nested types</strong></summary>

### Constructors

| Signature | Behavior |
| :--- | :--- |
| `DirectionalViewPager(context: Context)` | Creates a horizontal pager (`isHorizontal = true`) |
| `DirectionalViewPager(context: Context, isHorizontal: Boolean)` | Creates a pager with explicit initial orientation |
| `DirectionalViewPager(context: Context, attrs: AttributeSet?)` | Inflates `android:orientation` and `app:userInputEnabled` |

### Properties

| Property | Type | Default | Notes |
| :--- | :--- | :--- | :--- |
| `isHorizontal` | `Boolean` | `true` | Switches axis at runtime and realigns scroll position |
| `isUserInputEnabled` | `Boolean` | `true` | Cancels active touch drag when set to `false` |
| `adapter` | `PagerAdapter?` | `null` | Attaches or replaces the page adapter |
| `currentItem` | `Int` | `0` | Gets or smoothly selects the active page after layout |
| `offscreenPageLimit` | `Int` | `1` | Retained pages per side (clamped to minimum `1`) |
| `pageMargin` | `Int` | `0` | Margin gap between adjacent pages in pixels |
| `isFakeDragging` | `Boolean` | `false` | Read-only flag indicating an active fake drag |

### Methods

| Method | Signature | Notes |
| :--- | :--- | :--- |
| `setCurrentItem` | `(item: Int, smoothScroll: Boolean)` | Selects `item` with optional smooth scroll |
| `setPageMarginDrawable` | `(d: Drawable?)` / `(@DrawableRes resId: Int)` | Sets drawable drawn inside `pageMargin` |
| `setPageTransformer` | `(reverseDrawingOrder: Boolean, transformer: PageTransformer?, pageLayerType: Int = LAYER_TYPE_HARDWARE)` | Applies per-page scroll transformations |
| `addOnPageChangeListener` | `(listener: ViewPager.OnPageChangeListener)` | Adds scroll and selection callback |
| `removeOnPageChangeListener` | `(listener: ViewPager.OnPageChangeListener)` | Removes a registered page change listener |
| `clearOnPageChangeListeners` | `()` | Clears all registered page change listeners |
| `addOnAdapterChangeListener` | `(listener: OnAdapterChangeListener)` | Registers adapter replacement callback |
| `removeOnAdapterChangeListener` | `(listener: OnAdapterChangeListener)` | Unregisters adapter replacement callback |
| `beginFakeDrag` | `(): Boolean` | Starts a fake drag if no real drag is active |
| `fakeDragBy` | `(offset: Float)` | Advances an active fake drag by `offset` pixels |
| `endFakeDrag` | `()` | Completes an active fake drag and settles |
| `canScrollHorizontally` | `(direction: Int): Boolean` | Checks horizontal scroll bounds (RTL-aware) |
| `canScrollVertically` | `(direction: Int): Boolean` | Checks vertical scroll bounds |
| `executeKeyEvent` | `(event: KeyEvent): Boolean` | Handles DPAD and Tab navigation when input is enabled |
| `arrowScroll` | `(direction: Int): Boolean` | Moves focus or pages in the requested focus direction |

### Constants and nested types

| Symbol | Kind | Definition |
| :--- | :--- | :--- |
| `SCROLL_STATE_IDLE` | `const val Int` | `0` |
| `SCROLL_STATE_DRAGGING` | `const val Int` | `1` |
| `SCROLL_STATE_SETTLING` | `const val Int` | `2` |
| `OnAdapterChangeListener` | `fun interface` | `onAdapterChanged(viewPager, oldAdapter, newAdapter)` |
| `LayoutParams` | `class : ViewGroup.LayoutParams` | `isDecor: Boolean`, `gravity: Int` |
| `SavedState` | `class : AbsSavedState` | Preserves `currentItem` and `PagerAdapter` state |

</details>

## Gradle and CI tasks

Defined in [build.gradle.kts](../build.gradle.kts), [ci.yml](../.github/workflows/ci.yml), and [publish.yml](../.github/workflows/publish.yml):

| Command | Purpose |
| :--- | :--- |
| `./gradlew spotlessApply` | Formats `.kt` and `.gradle.kts` files with `ktlint` and UNIX line endings |
| `./gradlew spotlessCheck` | Verifies formatting compliance |
| `./gradlew :library:test` | Runs JUnit 6 + MockK unit tests in `:library` |
| `./gradlew :library:assembleRelease` | Builds the release AAR artifact |
| `./gradlew :demo:assembleDebug` | Builds the Material 3 demo APK |
| `./gradlew :library:publishToMavenLocal` | Publishes the release variant to `~/.m2` |
| `./gradlew :library:publishReleasePublicationToGitHubPackagesRepository` | Publishes to GitHub Packages (triggered on `v*` tags) |
