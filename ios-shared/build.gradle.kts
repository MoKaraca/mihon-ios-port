import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.multiplatform.compose)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "mihon.ios.shared"
        withHostTest {}
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "MihonShared"
            isStatic = true
            export(projects.i18n)
            export(libs.moko.resources)
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlin.time.ExperimentalTime",
        )
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.i18n)
            api(projects.core.metro)

            implementation(libs.metro.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.jsonOkio)
            implementation(libs.kotlinx.serialization.protobuf)

            implementation(libs.okio)
            implementation(libs.ksoup)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)

            implementation(libs.coil.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            implementation(libs.sqldelight.async)
            implementation(libs.sqldelight.coroutines)

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.tabNavigator)
            implementation(libs.voyager.transitions)

            implementation(libs.materialKolor)
            implementation(libs.xmlutil.kmpCore)
            implementation(libs.xmlutil.serialization)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.sqldelight.androidxDriver)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.nativeDriver)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("tachiyomi.data")
            dialect(libs.sqldelight.sqliteDialect338)
            generateAsync.set(true)
        }
    }
}
