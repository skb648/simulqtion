plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.realitycompiler"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.realitycompiler"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        val backendUrl = (project.findProperty("RC_BACKEND_URL") as String?) ?: "http://10.0.2.2:8000"
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendUrl\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.google.ar:core:1.54.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
