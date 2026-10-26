import java.security.MessageDigest

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "dev.broadband.toolbox"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "dev.broadband.toolbox"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = 28
        targetSdk = 36
        ndk {
            abiFilters.clear()
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        val rootDigest = MessageDigest.getInstance("SHA-256")
        fileTree("src/main/kotlin/dev/broadband/toolbox/privileged").files.sortedBy { it.path }.forEach { rootDigest.update(it.readBytes()) }
        fileTree("src/main/aidl").files.sortedBy { it.path }.forEach { rootDigest.update(it.readBytes()) }
        fileTree("src/main/java/dev/broadband/toolbox/dnsapi").files.sortedBy { it.path }.forEach { rootDigest.update(it.readBytes()) }
        fileTree("../../../../native") { include("**/*.c", "**/*.h") }.files.sortedBy { it.path }.forEach { rootDigest.update(it.readBytes()) }
        val rootVersion = rootDigest.digest().take(3).fold(0) { value, byte -> (value shl 8) or (byte.toInt() and 255) }
        buildConfigField("int", "ROOT_SERVICE_VERSION", rootVersion.toString())
    }

    buildFeatures { aidl = true; buildConfig = true }
    externalNativeBuild {
        cmake {
            path = file("../../../../native/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging { jniLibs.useLegacyPackaging = true }
    testOptions { unitTests.isReturnDefaultValues = true }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("dnsjava:dnsjava:3.6.5")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.webkit:webkit:1.14.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
