plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "dev.echelonoverlay.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.echelonoverlay.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:bike"))
    implementation(project(":core:workout"))
    implementation(project(":core:safety"))
    implementation(project(":feature:overlay"))
}
