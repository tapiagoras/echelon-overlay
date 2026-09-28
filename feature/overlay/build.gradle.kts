plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "dev.echelonoverlay.overlay"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }
