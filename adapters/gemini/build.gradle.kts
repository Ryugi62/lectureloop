plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":application"))
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    // Live check against the real API runs only when explicitly requested (see docs/VERIFICATION.md).
    environment("LIVE_GEMINI", System.getenv("LIVE_GEMINI") ?: "")
    environment("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY") ?: "")
    environment("LIVE_AUDIO", System.getenv("LIVE_AUDIO") ?: "")
}
