plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.biometrics.contactless"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.biometrics.contactless"
        minSdk = 24        // API 24 (Android 7.0), per spec
        targetSdk = 34      // API 34+, per spec
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        viewBinding = true
    }

    // Optional native C++/JNI path for high-speed OpenCV operations,
    // per the spec's "C++ / JNI (Optional)" dependency note.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // CameraX (v1.3.0+, per spec)
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // OpenCV for Android (v4.8.0+, per spec).
    // Add as a module dependency (File > New > Import Module, pointing at
    // the OpenCV-android-sdk's "sdk" folder) or via a Maven artifact if
    // your project uses one -- OpenCV's official distribution is a
    // downloadable SDK, not always a single Maven coordinate, so the exact
    // line here depends on which distribution method the project adopts.
    implementation(project(":opencv"))

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    testImplementation("junit:junit:4.13.2")
    // Robolectric lets BenchmarkLoggerTest run android.util.Log calls on
    // the plain JVM (no emulator/device) -- a stock unit test throws
    // "Log not mocked" without this.
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // Instrumented tests need OpenCV loaded on-device -- see TESTING.md.
    androidTestImplementation(project(":opencv"))
}
