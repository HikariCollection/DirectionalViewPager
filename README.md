[![CI](https://github.com/HikariCollection/DirectionalViewPager/actions/workflows/ci.yml/badge.svg)](https://github.com/HikariCollection/DirectionalViewPager/actions/workflows/ci.yml)

# DirectionalViewPager

Android `ViewPager` implementation in Kotlin supporting both horizontal and vertical swiping, including runtime orientation switching while remaining compatible with `PagerAdapter`, `ViewPager.OnPageChangeListener`, and `ViewPager.PageTransformer`.

## Installation

Add the GitHub Packages repository to your `settings.gradle.kts`:

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

Then add the dependency (minSdk 23):

```kotlin
dependencies {
    implementation("androidx.viewpager.widget:directional-viewpager:2.0.0")
}
```

## Usage

From XML, optionally specifying `android:orientation`:

```xml
<androidx.viewpager.widget.DirectionalViewPager
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical" />
```

From Kotlin:

```kotlin
val pager = DirectionalViewPager(context, isHorizontal = false)
```

Switch direction at runtime:

```kotlin
pager.isHorizontal = !pager.isHorizontal
```

## Changelog (2.0.0)

- Migrated library and demo modules to Kotlin with AGP 9.5.0, Gradle 9.6.0, and SDK 37.
- Fixed `canScrollVertically` reading horizontal scroll/width instead of vertical scroll/height.
- Fixed vertical `EdgeEffect` canvas transforms and padding offsets in `draw`.
- Fixed vertical `sizeFactor` measurement in `onMeasure`/`onLayout` and vertical decor gravity mask in `onPageScrolled`.
- Fixed accessibility scroll actions and `endFakeDrag` offset calculation in vertical mode.
- Added JUnit 6 + MockK unit tests and GitHub Actions CI / GitHub Packages publishing workflows.
