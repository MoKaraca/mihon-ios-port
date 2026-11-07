plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "mihon.core.metro"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.metro.runtime)
        }
    }
}
