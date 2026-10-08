plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/* Sound (SoundPool) + haptics (Vibrator): one distinct sound and pattern per CueType. */
android {
    namespace = "com.watchthetime.feedback"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(project(":core-domain"))
    implementation(libs.androidx.core.ktx)
}
