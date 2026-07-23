plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hassan.zensposed"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hassan.zensposed"
        minSdk = 31
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // Used by CI / local release builds when env vars (or gradle.properties) are set.
        // Never commit the keystore or passwords.
        create("release") {
            val storePath = System.getenv("ZENSPOSED_STORE_FILE")
                ?: (project.findProperty("ZENSPOSED_STORE_FILE") as String?)
            val storePassword = System.getenv("ZENSPOSED_STORE_PASSWORD")
                ?: (project.findProperty("ZENSPOSED_STORE_PASSWORD") as String?)
            val keyAlias = System.getenv("ZENSPOSED_KEY_ALIAS")
                ?: (project.findProperty("ZENSPOSED_KEY_ALIAS") as String?)
            val keyPassword = System.getenv("ZENSPOSED_KEY_PASSWORD")
                ?: (project.findProperty("ZENSPOSED_KEY_PASSWORD") as String?)

            if (!storePath.isNullOrBlank() &&
                !storePassword.isNullOrBlank() &&
                !keyAlias.isNullOrBlank() &&
                !keyPassword.isNullOrBlank()
            ) {
                storeFile = file(storePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.libsu.core)
    implementation(libs.libsu.service)

    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)

    compileOnly(libs.xposed.api)
}
