import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abhilash.matinee.tv"
        minSdk = 24
        targetSdk = 35
        versionName = matineeVersion
        versionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]
        // Fire TV Sticks run 32-bit userlands even on 64-bit chips, so both ARM ABIs ship (FFmpeg is native)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.tvprovider:tvprovider:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.media3:media3-exoplayer:1.9.4")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.4")
    implementation("androidx.media3:media3-exoplayer-dash:1.9.4")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.4")
    implementation("androidx.media3:media3-ui:1.9.4")
    // FFmpeg audio decoders (DTS, DTS-HD, TrueHD, AC-3…) for when the device can neither decode nor pass a format through
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")
    // libass: fully styled ASS/SSA subtitles
    implementation("io.github.peerless2012:ass-media:0.5.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
