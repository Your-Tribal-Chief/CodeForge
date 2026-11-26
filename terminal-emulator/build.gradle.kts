// Vendored from termux-app v0.118.0 (Apache License 2.0). See THIRD_PARTY_LICENSES.md.
plugins { id("com.android.library") }
android {
    namespace = "com.termux.terminal"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        externalNativeBuild { ndkBuild { cFlags("-std=c11", "-Wall", "-Os", "-fno-stack-protector", "-Wl,--gc-sections") } }
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    externalNativeBuild { ndkBuild { path = file("src/main/jni/Android.mk") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = false }
}
