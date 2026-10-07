import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services) apply false
}

// PR builds work without Firebase credentials; release CI requires the real file.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun setting(name: String): String = providers.gradleProperty(name).orNull
    ?: System.getenv(name) ?: local.getProperty(name, "")
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
val appVersionCode = providers.gradleProperty("appVersionCode").orElse("5").get().toInt()
val appVersionName = providers.gradleProperty("appVersionName").orElse("2.0.0-dev").get()
require(appVersionCode >= 5) { "versionCode must be at least 5" }
require(appVersionName.isNotBlank()) { "versionName must not be blank" }

android {
    namespace = "app.web.oneonone"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.web.oneonone"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        mapOf(
            "API_URL" to "VITE_API_URL",
            "SUPABASE_URL" to "VITE_SUPABASE_URL",
            "SUPABASE_ANON_KEY" to "VITE_SUPABASE_ANON_KEY",
            "GOOGLE_WEB_CLIENT_ID" to "VITE_GOOGLE_WEB_CLIENT_ID",
        ).forEach { (field, key) -> buildConfigField("String", field, quoted(setting(key))) }
    }

    signingConfigs {
        if (setting("ANDROID_KEYSTORE_PATH").isNotBlank()) {
            create("upload") {
                storeFile = rootProject.file(setting("ANDROID_KEYSTORE_PATH"))
                storePassword = setting("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = "oneonone-upload"
                keyPassword = setting("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            // R8 rules and device verification belong to A7.
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        warningsAsErrors = true
        // Pinned compatible versions are updated deliberately; notices are not correctness failures.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.tooling)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.socketio) { exclude(group = "org.json", module = "json") }
    implementation(libs.supabase.auth)
    implementation(libs.ktor.okhttp)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.workmanager)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.webrtc)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
