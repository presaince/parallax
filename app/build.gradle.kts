plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// The release APK is signed by apksigner in scripts/release.ps1, which reads
// app/keystore.properties. There is deliberately no signingConfig here: under AGP 9.4.1 it
// validates and never applies, so it only made assembleRelease look signed when it was not.

android {
    namespace = "com.presaince.glasstube"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.presaince.glasstube"
        // API 33 is the floor because NewPipeExtractor calls JDK methods that only exist from
        // Android 13. Desugaring is deliberately not used; modern devices only.
        minSdk = 33
        targetSdk = 36
        // Both numbers move together, with scripts/bump-version.mjs --apply. Never hand-edit one:
        // the page names the version and the updater compares versionCode, so a build that keeps
        // code 3 is a build that nobody is offered.
        versionCode = 13
        versionName = "0.3.10"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Robolectric needs the merged resources and manifest to stand in for a device. The SDK it
    // emulates is pinned in src/test/resources/robolectric.properties.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Room cannot export the schema without an explicit location now that exportSchema is on.
    // The schemas are committed: the first migration written against them will otherwise be blind.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Signing is a separate apksigner pass in scripts/release.ps1, not an AGP signing config:
    // under AGP 9.4.1 this block is half-applied — validateSigningRelease fails while
    // assembleRelease still writes an unsigned APK — so the config made the build lie about
    // what it produced. One signing path, at the end, on the file that is uploaded.

    buildTypes {
        release {
            // Minification stays off until a release build has been run on a device: an untested
            // R8 pass is a crash waiting for the first user.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

tasks.withType<Test>().configureEach {
    // The canary is opt-in; pass -Dglasstube.canary=true to the Gradle invocation.
    systemProperty("glasstube.canary", System.getProperty("glasstube.canary") ?: "false")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Images. On the shared OkHttp client, so thumbnails reuse the same connection pool.
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // GPLv3. Dependency only, never vendored.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Android stubs org.json, so the import parsers need the real library on the JVM.
    testImplementation("org.json:json:20240303")
    // Robolectric lets Android-bound units (Context, SharedPreferences, Room, Compose) run as
    // plain JVM tests. The emulated SDK is pinned in src/test/resources/robolectric.properties.
    testImplementation("org.robolectric:robolectric:4.17")
    // Compose UI tests, run on the JVM through Robolectric. Versionless: the compose-bom above
    // aligns them.
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // A real socket for the update and bug-upload paths, without a network.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// The suite is never attached to assembleDebug: a compile check must stay a compile check. Tests
// are run explicitly, targeted with --tests while iterating and whole before a release.
