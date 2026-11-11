import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Sample native plugin, packaged as its own APK.
 *
 * Build with `./gradlew :plugin:sample-native:assembleDebug` and install the
 * resulting APK; the host discovers it through the marker intent filter and can
 * load it after the user enables it.
 */
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.openschedule.plugin.sample.viewbased"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.openschedule.plugin.sample.viewbased"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // The SDK ships with the host at runtime; compileOnly keeps it out of this APK
    // so the host's copy is the single source of truth for the contract classes.
    compileOnly(project(":core:plugin-sdk"))
}
