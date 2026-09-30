plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ai.byak.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "ai.byak.app"
        minSdk = 26
        targetSdk = 36
        // Play already holds versionCode 34 (3.0.1) from the release/byak-v3.0.1-* line.
        // Google Play rejects a reused or lower versionCode, so this line continues at 35.
        // Raise this after every successful upload.
        versionCode = 42
        versionName = "3.2.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        fun prop(name: String, fallback: String) = (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback
        buildConfigField("String", "API_BASE_URL", "\"${prop("BYAK_API_URL", "http://10.0.2.2:8787")}\"")
        // Must be the *Web application* OAuth client of the same Google Cloud project (not the Android client).
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${prop("BYAK_GOOGLE_WEB_CLIENT_ID", "1077439001893-rnboa31frshmo5iopmkbvasd41lv8hqe.apps.googleusercontent.com")}\"")
        // Play Console: one subscription product with a base plan per billing period.
        // Store listing essentials shown in Settings; set them for production builds.
        buildConfigField("String", "SUPPORT_EMAIL", "\"${prop("BYAK_SUPPORT_EMAIL", "")}\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${prop("BYAK_PRIVACY_POLICY_URL", "")}\"")
        buildConfigField("String", "TERMS_URL", "\"${prop("BYAK_TERMS_URL", "")}\"")
        buildConfigField("String", "PLAY_PRODUCT_ID", "\"${prop("BYAK_PLAY_PRODUCT_ID", "byak_pro")}\"")
        buildConfigField("String", "PLAY_MONTHLY_BASE_PLAN", "\"${prop("BYAK_PLAY_MONTHLY_BASE_PLAN", "monthly")}\"")
        buildConfigField("String", "PLAY_YEARLY_BASE_PLAN", "\"${prop("BYAK_PLAY_YEARLY_BASE_PLAN", "yearly")}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    // Upload key for Play. Never commit the keystore: CI decodes it from a secret (see docs/release).
    val keystorePath = System.getenv("BYAK_KEYSTORE_FILE")
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("BYAK_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("BYAK_KEY_ALIAS")
            keyPassword = System.getenv("BYAK_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Lets the release workflow read the version it just built, so it can refuse to
// upload a bundle whose versionCode is not higher than what Play already has.
// Google Play rejects a lower or reused versionCode, and the release branches in
// this repository do not share a single monotonic version line with main.
tasks.register("releaseVersionInfo") {
    val versionCode = android.defaultConfig.versionCode
    val versionName = android.defaultConfig.versionName
    val applicationId = android.defaultConfig.applicationId
    doLast {
        val out = layout.buildDirectory.file("release-version-info.txt").get().asFile
        out.parentFile.mkdirs()
        out.writeText("versionCode=$versionCode\nversionName=$versionName\napplicationId=$applicationId\n")
        logger.lifecycle("BYAK_RELEASE_VERSION_CODE=$versionCode")
        logger.lifecycle("BYAK_RELEASE_VERSION_NAME=$versionName")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.6")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Offline AI (Qwen3 0.6B) — the same LiteRT-LM engine the 2.9 release shipped.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
