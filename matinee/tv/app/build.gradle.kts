import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the release tag's version (matinee-tv-v1.2.3 -> -PmatineeVersion=1.2.3)
val matineeVersion = (findProperty("matineeVersion") as String?) ?: "0.0.1"
val versionParts = matineeVersion.split(".").map { it.toInt() }

// Release signing: matinee/tv/keystore.properties (not in git) or the MATINEE_TV_KEYSTORE* environment variables in CI
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.abhilash.matinee.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.abhilash.matinee.tv"
        minSdk = 24
        targetSdk = 35
        versionName = matineeVersion
        versionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]
    }

    signingConfigs {
        create("release") {
            val store = signingValue("storeFile", "MATINEE_TV_KEYSTORE")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = signingValue("storePassword", "MATINEE_TV_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "MATINEE_TV_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "MATINEE_TV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (signingValue("storeFile", "MATINEE_TV_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
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
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.0.0")
}
