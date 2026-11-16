plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.afshin.pcbtrace"
    compileSdk = 34
    defaultConfig { applicationId = "com.afshin.pcbtrace"; minSdk = 29; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17"; freeCompilerArgs += "-opt-in=androidx.camera.camera2.interop.ExperimentalCamera2Interop" }
    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    val cx = "1.3.4"
    implementation("androidx.camera:camera-core:$cx")
    implementation("androidx.camera:camera-camera2:$cx")
    implementation("androidx.camera:camera-lifecycle:$cx")
    implementation("androidx.camera:camera-view:$cx")
}
