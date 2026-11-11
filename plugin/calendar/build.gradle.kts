import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * 校历插件（原生 dex 模块）。
 *
 * 只作为插件源码保留：构建产物 `hzau-calendar-<version>.osplugin` 发布到插件市场，
 * 宿主不预装、不内置。
 */
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.openschedule.plugin.hzau.calendar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.openschedule.plugin.hzau.calendar"
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
    compileOnly(project(":core:plugin-sdk"))
}
