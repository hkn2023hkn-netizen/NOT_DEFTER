plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hakan.notdefteri"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hakan.notdefteri"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.0.2"
    }

    // Sabit anahtar: her derlemede aynı imza, böylece yeni sürüm eskisinin üzerine kurulabilir.
    signingConfigs {
        create("sabit") {
            storeFile = file("keystore/notdefteri.jks")
            storePassword = "notdefteri"
            keyAlias = "notdefteri"
            keyPassword = "notdefteri"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sabit")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sabit")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
