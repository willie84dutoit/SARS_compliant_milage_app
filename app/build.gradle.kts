plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mileagetracker.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mileagetracker.app"
        // Brief §8: target Android 10+ (API 29). Below that, the app shows an
        // unsupported-device message and stops background tracking gracefully (T-001 follow-up).
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-mvp"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room exportSchema = true (MileageTrackerDatabase) writes the generated schema JSON
        // here for future-migration diffing (T-001 build order step 3).
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // T-005.1: returnDefaultValues = true lets Android stub classes (e.g. android.graphics.Bitmap)
    // return null/0/false instead of throwing RuntimeException("Stub!") in plain JVM unit tests.
    // This is safe here because (a) only test code sees the stubs and (b) the ViewModel tests that
    // touch Bitmap pass it as a "don't-care" reference through to a FakeOdometerOcrClient that
    // never dereferences it — so getting a null back from Bitmap.createBitmap() or a no-op from
    // other Android stubs is the correct behaviour for JVM tests.
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // T-001 build-order step 2/3: domain + data layers carry the unit/instrumented test source
    // sets listed in the blueprint's package tree.
    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDirs("src/androidTest/kotlin")
        }
    }
}

dependencies {
    // --- Kotlin / Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Provides Task<T>.await() used to bridge Play Services (ML Kit, FusedLocation) callbacks
    // into suspend functions — required by MlKitOdometerOcrClient.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // --- AndroidX core ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // --- Compose (BOM-managed versions) ---
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // --- Hilt (dependency injection per T-001 §3 module graph) ---
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // --- Room (per T-001 §2 schema: TripEntity, TripPhotoEntity) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- DataStore (settings + the T-008 rolling chainTailHash cache) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- CameraX (T-005 odometer capture) ---
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // --- ML Kit Text Recognition (T-005 OCR) ---
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // --- Location / Activity Recognition (T-002/T-004, geo-sensors-specialist) ---
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- WorkManager ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // --- Logging (T-018 field-debuggability: structured logs a tester's device can produce) ---
    implementation("com.jakewharton.timber:timber:5.0.1")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // runTest (kotlinx.coroutines.test) is needed in the instrumented test scope as well as JVM.
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
