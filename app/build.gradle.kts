plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.kapt") }
val tag = System.getenv("GITHUB_REF_NAME")?.takeIf { it.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")) }
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
android {
    namespace = "br.com.portalwifi"
    compileSdk = 36
    defaultConfig {
        applicationId = "br.com.portalwifi"
        minSdk = 29
        targetSdk = 35
        versionCode = (System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: buildNumber).coerceAtLeast(1)
        versionName = tag?.removePrefix("v") ?: if (System.getenv("GITHUB_RUN_NUMBER") != null) "0.1.$buildNumber" else "0.1.0-dev.$buildNumber"
        buildConfigField("String", "RELEASES_API_URL", "\"https://api.github.com/repos/AmaroPSJunior/WiFi-Captive-Portal/releases/latest\"")
    }
    buildFeatures { buildConfig = true }
    val signingPath = System.getenv("ANDROID_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
    signingConfigs {
        create("ciRelease") {
            signingPath?.let {
                storeFile = file(it)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = "portal"
                keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (signingPath != null) signingConfig = signingConfigs.getByName("ciRelease")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
}
