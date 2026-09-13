plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose.compiler)
    id("kotlin-kapt")
}

val torNativeBinaries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val torNativeLibsDir = layout.buildDirectory.dir("generated/tor-native/jniLibs")

val extractTorNativeLibs by tasks.registering(Sync::class) {
    from({ torNativeBinaries.map { zipTree(it) } })
    include("armeabi-v7a/*.so", "arm64-v8a/*.so", "x86/*.so", "x86_64/*.so")
    into(torNativeLibsDir)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach {
        dependsOn(extractTorNativeLibs)
    }

android {
    namespace = "com.seamless.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.seamless.droid"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 26
        versionName = "1.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(torNativeLibsDir)
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    // Image loading for avatars in profile screen
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // Lifecycle
    implementation(libs.bundles.lifecycle)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Permissions
    implementation(libs.accompanist.permissions)
    
    // Cryptography
    implementation(libs.bundles.cryptography)
    
    // JSON
    implementation(libs.gson)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Bluetooth
    implementation(libs.nordic.ble)

    // WebSocket
    implementation(libs.okhttp)

    // Arti (Tor in Rust) Android bridge - use published AAR with native libs
    // Arti handles SOCKS proxy for outbound Tor connections
    implementation("info.guardianproject:arti-mobile-ex:1.2.3")
    
    // Briar's onionwrapper for proper Tor hidden service support
    // This bundles native Tor binaries and handles hidden service publishing
    // Required for other devices to connect TO us via our .onion address
    implementation("org.briarproject:onionwrapper-android:0.1.3")
    implementation("org.briarproject:tor-android:0.4.8.22")
    implementation("org.briarproject:lyrebird-android:0.6.2")
    torNativeBinaries("org.briarproject:tor-android:0.4.8.22")
    torNativeBinaries("org.briarproject:lyrebird-android:0.6.2")

    // Google Play Services Location
    implementation(libs.gms.location)

    // Security preferences
    implementation(libs.androidx.security.crypto)
    
    // EXIF orientation handling for images
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    
    // QR Code generation for Walkie-Talkie pairing
    implementation(libs.zxing.core)
    
    // CameraX for QR scanning
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    
    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.bundles.compose.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    
    // SQLCipher for encrypted database storage
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Biometric for App Lock
    implementation("androidx.biometric:biometric:1.1.0")

    // NanoHTTPD for embedded Mailbox REST server (zero additional native dependencies)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
