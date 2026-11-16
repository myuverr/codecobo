// SPDX-License-Identifier: MPL-2.0

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

android {
    namespace = "com.myuverr.codecobo"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.myuverr.codecobo"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        aidl = true
        compose = true
    }

    val keystorePath = providers.environmentVariable("CODECOBO_KEYSTORE_FILE").orNull
    val keystorePassword = providers.environmentVariable("CODECOBO_KEYSTORE_PASSWORD").orNull
    require((keystorePath == null) == (keystorePassword == null)) {
        "CODECOBO_KEYSTORE_FILE and CODECOBO_KEYSTORE_PASSWORD must be provided together"
    }
    val suppliedSigning = keystorePath?.let { path ->
        signingConfigs.create("release") {
            storeFile = file(path)
            storeType = "PKCS12"
            storePassword = keystorePassword
            keyAlias = "codecobo"
            keyPassword = keystorePassword
            enableV1Signing = false
            enableV3Signing = true
            enableV4Signing = false
        }
    }

    buildTypes {
        if (suppliedSigning != null) {
            configureEach { signingConfig = suppliedSigning }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        abortOnError = true
    }
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

dependencies {
    implementation(libs.material3)
    implementation(libs.activity.compose)
    implementation(libs.room.runtime)
    implementation(libs.lottie.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
    compileOnly(libs.androidx.annotations)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
}
