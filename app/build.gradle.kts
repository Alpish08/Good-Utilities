plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tools.app"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.tools.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 98
        versionName = "2.8.1"
        // S24 Ultra (and every modern phone) is arm64; keeps the bundled Python/FFmpeg small.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Release key: ~/.good-utilities/release.p12, its password in the macOS Keychain under
    // "Good Utilities release key" (or the GU_KEYSTORE_PASSWORD environment variable). Neither is in the repo.
    // Without them (e.g. on another machine) release builds fall back to the debug key.
    val releaseStore = file("${System.getProperty("user.home")}/.good-utilities/release.p12")
    val releasePassword: String? = System.getenv("GU_KEYSTORE_PASSWORD") ?: runCatching {
        providers.exec {
            commandLine("security", "find-generic-password", "-s", "Good Utilities release key", "-w")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().ifEmpty { null }
    }.getOrNull()
    val hasReleaseKey = releaseStore.exists() && releasePassword != null
    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = releaseStore
            storeType = "PKCS12"
            storePassword = releasePassword
            keyAlias = "good-utilities"
            keyPassword = releasePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // yt-dlp runs as a native Python binary, which must be extracted to disk.
    packaging {
        jniLibs { useLegacyPackaging = true }
        // Kuromoji's two jars both ship these license notes.
        resources { pickFirsts += setOf("META-INF/CONTRIBUTORS.md", "META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/LICENSE", "META-INF/NOTICE", "META-INF/javamail.providers", "META-INF/javamail.default.providers", "META-INF/javamail.default.address.map", "META-INF/mailcap", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt") }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.google.zxing:core:3.5.3")
    // Briefs: Gmail over IMAP (app password), newsletter clean-up, links in a Custom Tab.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("dev.chrisbanes.haze:haze:2.1.0") // blur behind the Home tab pill
    implementation("dev.chrisbanes.haze:haze-blur:2.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    // Background remover: ML Kit model served by Play services (unbundled, ~no APK cost).
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    // APK extractor: split merging (the library behind APKEditor / AntiSplit-M) and signing.
    implementation("io.github.reandroid:ARSCLib:1.3.8")
    implementation("com.android.tools.build:apksig:8.7.3")

    // PDF editor + converter: read/write/merge/split PDFs, extract text.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Compressor: hardware (MediaCodec) video transcoding — fast and light on battery.
    val media3 = "1.10.1"
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-common:$media3")

    // Japanese reader: on-device OCR + translation (ML Kit), word splitting (Kuromoji / IPADIC).
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")
}
