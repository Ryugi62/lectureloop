import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Keys stay out of git: put them in local.properties (see README "Run it").
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun key(name: String): String = (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim()

android {
    namespace = "io.github.ryugi62.lectureloop"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.ryugi62.lectureloop"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${key("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${key("GEMINI_MODEL").ifEmpty { "gemini-3.5-flash-lite" }}\"")
        // Production path: the LectureLoop server holds the AI key and checks the RevenueCat entitlement.
        buildConfigField("String", "LECTURELOOP_SERVER_URL", "\"${key("LECTURELOOP_SERVER_URL")}\"")
    }

    buildTypes {
        debug {
            // RevenueCat Test Store: real purchase flow, no store account needed.
            buildConfigField("String", "REVENUECAT_API_KEY", "\"${key("REVENUECAT_TEST_STORE_KEY")}\"")
        }
        release {
            // Never ship a Test Store key: release builds use the Google Play key.
            buildConfigField("String", "REVENUECAT_API_KEY", "\"${key("REVENUECAT_GOOGLE_KEY")}\"")
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation(project(":application"))
    implementation(project(":adapters:gemini"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.revenuecat.purchases)
    implementation(libs.revenuecat.purchases.ui)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
