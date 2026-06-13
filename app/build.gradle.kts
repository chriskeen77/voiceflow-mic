plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ck.voiceflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ck.voiceflow"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // stable debug signing: CI commits app/debug.keystore once via the
    // setup-keystore workflow, so every APK has the same signature and
    // updates install over the existing app without uninstalling
    signingConfigs {
        getByName("debug") {
            val ks = file("debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // framework APIs only — no androidx needed
}
