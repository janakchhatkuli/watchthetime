import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * Pure-Kotlin game engine: event model, reducer, clock math, rules, undo/redo, merge.
 * Has zero Android dependencies so it can be compiled for iOS (Phase 2) by adding
 * iosArm64()/iosSimulatorArm64() targets and building an XCFramework on macOS.
 */
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
