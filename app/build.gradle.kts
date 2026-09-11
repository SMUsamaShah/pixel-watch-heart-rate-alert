plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.usamashah.heartthreshold"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.usamashah.heartthreshold"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.health:health-services-client:1.1.0-rc02")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.guava:guava:33.3.1-android")
}
