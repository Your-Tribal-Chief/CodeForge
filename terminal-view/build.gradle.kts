// Vendored from termux-app v0.118.0 (Apache License 2.0). See THIRD_PARTY_LICENSES.md.
plugins { id("com.android.library") }
android {
    namespace = "com.termux.view"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = false }
}
dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    api(project(":terminal-emulator"))
}
