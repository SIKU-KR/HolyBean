plugins {
    id("com.android.library")
}

android {
    namespace = "eloom.holybean.escpos"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        debug { }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.zxing:core:3.4.0")
}