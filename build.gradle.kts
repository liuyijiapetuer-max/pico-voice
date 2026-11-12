plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.20" apply false
}
