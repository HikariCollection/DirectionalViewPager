# Get started with DirectionalViewPager

Add `DirectionalViewPager` to an Android project, render your first vertical or horizontal pages, and run the interactive demo application.
Requires Android Gradle Plugin `8.0+`, `minSdk 23`, and a GitHub account token for GitHub Packages resolution.

## Add the GitHub Packages repository

Configure the repository inside your `settings.gradle.kts` dependency resolution block:

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

> [!NOTE]
> GitHub Packages requires a personal access token with `read:packages` scope even for public repositories. Store `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties` or export `GITHUB_ACTOR` and `GITHUB_TOKEN` in CI.

## Add the library dependency

Declare the dependency in your module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("androidx.viewpager.widget:directional-viewpager:2.0.0")
}
```

## Configure the pager layout

Add [DirectionalViewPager](../library/src/main/kotlin/androidx/viewpager/widget/DirectionalViewPager.kt) to an XML layout and optionally set `android:orientation` and `app:userInputEnabled`:

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

Attach a `PagerAdapter` and register page listeners in your activity or fragment:

```kotlin
val pager = findViewById<DirectionalViewPager>(R.id.pager)
pager.adapter = myPagerAdapter
pager.pageMargin = resources.getDimensionPixelSize(R.dimen.page_gap)
pager.isHorizontal = true
```

## Run the demo application

Build and install the Material 3 `:demo` application onto a connected Android device or emulator:

```bash
./gradlew :demo:installDebug
```

Use the top control chips in [ViewPagerActivity](../demo/src/main/kotlin/androidx/directionalviewpager/demo/ViewPagerActivity.kt) to toggle orientation, RTL mirroring, input locking, page margins, and `PageTransformer` presets.
