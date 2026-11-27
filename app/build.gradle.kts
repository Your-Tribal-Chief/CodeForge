plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.codeforge.ide"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.codeforge.ide"
        minSdk = 26
        // Deliberate: apps targeting API 29+ may not execute binaries from app-writable storage,
        // which a local Linux userland requires. Same choice as Termux. See docs/ARCHITECTURE.md.
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0-mvp"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        // Publicly committed TEST key so successive CI builds install as updates of each other.
        // It is not a production identity. Replace it before any public release.
        create("codeforgeTest") {
            storeFile = rootProject.file("keystore/codeforge-test.jks")
            storePassword = "codeforge-test"
            keyAlias = "codeforge-test"
            keyPassword = "codeforge-test"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("codeforgeTest")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("codeforgeTest")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Extract .so files to nativeLibraryDir so PRoot and its loader are executable.
            useLegacyPackaging = true
            keepDebugSymbols += listOf("**/libproot.so", "**/libproot-loader.so", "**/libtalloc.so", "**/libandroid-shmem.so")
        }
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES", "/META-INF/INDEX.LIST")
        }
    }

    androidResources {
        // Already compressed; keeps openFd() working for install progress.
        noCompress += listOf("tar.gz", "gz")
    }

    lint {
        disable += listOf("ExpiredTargetSdkVersion", "OldTargetApi")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":terminal-view"))

    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.7"))
    implementation("io.github.Rosemoe.sora-editor:editor")
    implementation("io.github.Rosemoe.sora-editor:language-textmate")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation("junit:junit:4.13.2")
}
