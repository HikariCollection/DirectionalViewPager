# DirectionalViewPager

[![Release](https://img.shields.io/github/v/release/HikariCollection/DirectionalViewPager?style=flat-square&color=0F6B6E)](https://github.com/HikariCollection/DirectionalViewPager/releases)
[![CI](https://img.shields.io/github/actions/workflow/status/HikariCollection/DirectionalViewPager/ci.yml?branch=main&style=flat-square&color=0F6B6E)](https://github.com/HikariCollection/DirectionalViewPager/actions/workflows/ci.yml)
[![minSdk](https://img.shields.io/badge/minSdk-23-4A6364?style=flat-square)](library/build.gradle.kts)
[![License](https://img.shields.io/github/license/HikariCollection/DirectionalViewPager?style=flat-square&color=4A6364)](LICENSE)

A Kotlin Android `ViewGroup` pager supporting both horizontal and vertical swiping with runtime orientation switching for `PagerAdapter` consumers.
Preserves full compatibility with `ViewPager.OnPageChangeListener`, `ViewPager.PageTransformer`, and `ViewPager.DecorView` while adding `NestedScrollingChild3`/`NestedScrollingParent3`, horizontal RTL mirroring, and `isUserInputEnabled`.

---

## Why DirectionalViewPager?

- **Drop-In `ViewPager` Compatibility**: Lives in `androidx.viewpager.widget` and reuses `PagerAdapter`, `ViewPager.OnPageChangeListener`, `ViewPager.PageTransformer`, and `@ViewPager.DecorView` without adapter rewrites.
- **Runtime Orientation Switching**: Flip between horizontal and vertical paging mid-session via `isHorizontal` while preserving the active page and recalculating offsets.
- **Full Nested Scrolling**: Implements both `NestedScrollingChild3` and `NestedScrollingParent3` to cooperate with `CoordinatorLayout`, `SwipeRefreshLayout`, `NestedScrollView`, and `RecyclerView`.
- **RTL & Input Lock**: Mirrors horizontal layout, drag, fling, `EdgeEffect`, DPAD keys, and accessibility actions in `LAYOUT_DIRECTION_RTL`, and supports disabling user gestures via `isUserInputEnabled`.

| Capability | `DirectionalViewPager` | AndroidX `ViewPager` | AndroidX `ViewPager2` |
| :--- | :--- | :--- | :--- |
| **Paging Axes** | Horizontal & Vertical | Horizontal only | Horizontal & Vertical |
| **Runtime Axis Switch** | `pager.isHorizontal = ...` | Not supported | `pager.orientation = ...` |
| **Adapter API** | `PagerAdapter` | `PagerAdapter` | `RecyclerView.Adapter` |
| **Nested Scrolling** | `Child3` & `Parent3` | None | `RecyclerView` internal |
| **Horizontal RTL** | Supported | Not supported | Supported |
| **Input Lock** | `isUserInputEnabled` | Subclass override | `isUserInputEnabled` |
| **Minimum SDK** | API 23 | API 14 | API 14 |

---

## Quickstart

### 1. Installation

Add the GitHub Packages repository to `settings.gradle.kts` and depend on `directional-viewpager`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/HikariCollection/DirectionalViewPager")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
}
```

```kotlin
dependencies {
    implementation("androidx.viewpager.widget:directional-viewpager:2.0.0")
}
```

> [!NOTE]
> GitHub Packages requires a GitHub token (`read:packages` scope). See [Getting started](docs/getting-started.md) for credential setup options.

### 2. Minimal Usage

```xml
<androidx.viewpager.widget.DirectionalViewPager
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/pager"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    app:userInputEnabled="true" />
```

```kotlin
val pager = findViewById<DirectionalViewPager>(R.id.pager)
pager.adapter = myPagerAdapter
pager.isHorizontal = false
```

### 3. Run the Demo App

```bash
./gradlew :demo:installDebug
```

---

## Documentation

- [Getting started](docs/getting-started.md) — Installation, credential configuration, layout setup, and demo app launch
- [Nested scrolling, RTL, and input control](docs/nested-scrolling-and-rtl.md) — `NestedScrollingChild3`/`Parent3`, `LAYOUT_DIRECTION_RTL`, `isUserInputEnabled`, and fake drags
- [Architecture and internals](docs/architecture.md) — Package placement, axis abstraction, RTL coordinate strategy, and scroll delegation flows
- [API, XML, and build reference](docs/reference.md) — Complete XML attributes, Kotlin/Java API members, Gradle coordinates, and CI tasks

---

## License

This project is licensed under the [Apache-2.0 License](LICENSE).
