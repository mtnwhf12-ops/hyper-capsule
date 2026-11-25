plugins {
    id("com.android.application")
}

android {
    namespace = "com.moamen.hypercapsule"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.moamen.hypercapsule"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
