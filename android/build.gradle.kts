plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "com.obsidian_north"
// Single source of truth is package.json — avoid drift that caused 3.3.1 vs 3.5.0.
// Fallback to hardcoded if package.json not found (e.g. published to npm).
version = run {
    val pkg = rootProject.file("../package.json").let { if (it.exists()) it else file("../package.json") }
    if (pkg.exists()) {
        Regex(""""version"\s*:\s*"([^"]+)"""").find(pkg.readText())?.groupValues?.get(1) ?: "3.5.1"
    } else "3.5.1"
}

android {
    namespace = "com.obsidian_north.mediastore"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    // NOTE: `androidResources.isNonTransitiveRClass` was introduced in AGP 8.3 and is
    // not available in Gradle 9 / AGP 7.x environments. Keep it conditional so the
    // module builds both locally (AGP 8.7, compileSdk 36) and in consumers on Gradle 9.
    // See: https://developer.android.com/build/releases/past-releases/agp-8-3-0-release-notes
    // If you need non-transitive R, set `android.nonTransitiveRClass=true` in gradle.properties.

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        checkDependencies = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Reduce build tasks: this module has no tests and lint is run separately.
// Disable lint + test tasks so `./gradlew build`/`assemble` skips them.
tasks.matching { it.name.contains("lint", ignoreCase = true) }.configureEach {
    enabled = false
}
tasks.withType<Test>().configureEach {
    enabled = false
}
tasks.matching { it.name.contains("Test", ignoreCase = true) }.configureEach {
    enabled = false
}

kotlin {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.facebook.react:react-android")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    // Media3 Inspector - modern replacement for MediaMetadataRetriever / MediaExtractor / getFrameAtTime
    // See https://developer.android.com/media/media3/inspector
    // NOTE: media3-inspector was first published at 1.9.0-alpha01; 1.8.0 does not exist
    // (see https://mvnrepository.com/artifact/androidx.media3/media3-inspector).
    // FrameExtractor moved to :media3-inspector-frame at 1.10.0, so both artifacts are needed.
    implementation("androidx.media3:media3-common:1.11.0")
    implementation("androidx.media3:media3-inspector:1.11.0")
    implementation("androidx.media3:media3-inspector-frame:1.11.0")
    implementation("androidx.media3:media3-extractor:1.11.0")
    // Only ListenableFuture is needed from Guava (Inspector returns ListenableFuture).
    // Use the minimal artifact to avoid ~3k methods / 1.2MB bloat from full guava:33.x-android.
    implementation("com.google.guava:listenablefuture:1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
}
