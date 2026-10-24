plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.miftah.core.adb"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(project(":core:execution"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // ADB protocol + pairing (dual-licensed GPL-3.0-or-later OR Apache-2.0). Hidden behind AdbTransport.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // TLS provider required by libadb for Wireless Debugging pairing.
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    // Self-signed certificate generation for the ADB identity.
    implementation("org.bouncycastle:bcprov-jdk15to18:1.81")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
}
