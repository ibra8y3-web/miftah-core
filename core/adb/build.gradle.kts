plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
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
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
}
