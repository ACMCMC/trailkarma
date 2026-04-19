import java.util.Properties
import java.io.FileInputStream
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "fyi.acmc.trailkarma"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { localProperties.load(it) }
    }

    fun configValue(
        localKey: String,
        projectKey: String,
        envKey: String
    ): String? = localProperties.getProperty(localKey)
        ?: project.findProperty(projectKey)?.toString()
        ?: System.getenv(envKey)

    val geminiApiKey = configValue(
        localKey = "gemini.apiKey",
        projectKey = "gemini.apiKey",
        envKey = "GEMINI_API_KEY"
    ) ?: ""

    val geminiModel = configValue(
        localKey = "gemini.model",
        projectKey = "gemini.model",
        envKey = "GEMINI_MODEL"
    ) ?: "gemini-2.5-flash"

    defaultConfig {
        applicationId = "fyi.acmc.trailkarma"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")

        // Databricks credentials
        val databricksUrl = localProperties.getProperty("databricks.url")
            ?: project.findProperty("databricks.url")?.toString()
            ?: System.getenv("DATABRICKS_HOST")
            ?: "https://dbc-f1d1578e-8435.cloud.databricks.com"
        val databricksToken = localProperties.getProperty("databricks.token")
            ?: project.findProperty("databricks.token")?.toString()
            ?: System.getenv("DATABRICKS_TOKEN")
            ?: ""
        val databricksWarehouse = localProperties.getProperty("databricks.warehouse")
            ?: project.findProperty("databricks.warehouse")?.toString()
            ?: System.getenv("DATABRICKS_WAREHOUSE")
            ?: "5fa7bca37483870e"

        buildConfigField("String", "DATABRICKS_URL", "\"$databricksUrl\"")
        buildConfigField("String", "DATABRICKS_TOKEN", "\"$databricksToken\"")
        buildConfigField("String", "DATABRICKS_WAREHOUSE", "\"$databricksWarehouse\"")

        // X25519 Public Key for Voice Relay Payload Encryption (Backend)
        buildConfigField("String", "RELAY_ENCRYPTION_PUBLIC_KEY", "\"9e8a82be5922e42cd11dd2ee5a0ae64c0a590172fa047e5a132104314f483404\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
            buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")
        }
        release {
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
            buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.androidx.security.crypto)
    implementation(libs.bouncycastle.bcprov)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Location
    implementation(libs.play.services.location)

    // OSMDroid
    implementation(libs.osmdroid.android)

    // On-device ML
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.select.tf.ops)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // CameraX
    val cameraxVersion = "1.5.3"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
}
