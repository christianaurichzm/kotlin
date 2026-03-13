plugins {
    id("common-configuration")
    id("test-federation-convention")
    id("com.autonomousapps.dependency-analysis")
    kotlin("multiplatform")
}

kotlin {
    macosArm64()
    @Suppress("DEPRECATION_ERROR", "DEPRECATION")
    macosX64()
    linuxX64()
    linuxArm64()
    mingwX64()
}
