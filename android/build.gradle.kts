plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "com.obsidian_north"
version = "3.3.1"

android {
    namespace = "com.obsidian_north.mediastore"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    androidResources {
        // This module ships no resources; avoid transitive R-class overhead.
        isNonTransitiveRClass = true
    }

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
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
