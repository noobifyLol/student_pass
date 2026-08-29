plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.studentpass"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.studentpass"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")

    testImplementation("junit:junit:4.13.2")

    // CameraX for taking photos
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")

    // Google ML Kit Text Recognition (Runs 100% Offline)
    implementation("com.google.mlkit:text-recognition:16.0.0")
}