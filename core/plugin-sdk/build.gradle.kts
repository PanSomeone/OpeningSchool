import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

/**
 * Public SDK for third-party native plugins.
 *
 * Deliberately dependency-free: only Android framework types are exposed, so a
 * plugin APK can be compiled against any Android toolchain and stays valid after
 * the host is minified by R8. No Compose, no app-internal classes.
 */
android {
    namespace = "com.openschedule.plugin.sdk"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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
