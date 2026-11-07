import mihon.gradle.tasks.GenerateLocalesConfigTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.moko.resources)
}

kotlin {
    android {
        namespace = "tachiyomi.i18n"

        // TODO(antsy): Remove when https://youtrack.jetbrains.com/issue/KT-83319 is resolved
        withHostTest { }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    dependencies {
        api(libs.moko.resources)
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

androidComponents {
    onVariants { variant ->
        val resSource = variant.sources.res ?: return@onVariants

        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<GenerateLocalesConfigTask>("generate${variantName}LocalesConfig") {
            localeFiles.from(
                layout.projectDirectory.dir("src/commonMain/moko-resources").asFileTree.matching {
                    include("**/strings.xml", "**/plurals.xml")
                },
            )
        }
        resSource.addGeneratedSourceDirectory(task) { it.outputDir }
    }
}

multiplatformResources {
    resourcesPackage.set("tachiyomi.i18n")
}

object MokoXmlSanitizer {
    fun sanitize(mokoDir: java.io.File) {
        if (!mokoDir.exists()) return
        mokoDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("multiplatform_") && it.name.endsWith(".xml") }
            .forEach { file ->
                val content = file.readText(Charsets.UTF_8)
                val closeTag = "</resources>"
                val idx = content.indexOf(closeTag)
                if (idx != -1 && idx < content.length - 20) {
                    val elemStart = content.lastIndexOf("\n\t<", idx).takeIf { it != -1 } ?: idx
                    val nextStringEnd = content.indexOf("</string>", idx).takeIf { it != -1 }?.plus("</string>".length)
                    val nextPluralsEnd = content.indexOf("</plurals>", idx).takeIf { it != -1 }?.plus("</plurals>".length)
                    val elemEnd = listOfNotNull(nextStringEnd, nextPluralsEnd).minOrNull() ?: (idx + closeTag.length)
                    val repaired = (content.substring(0, elemStart) + content.substring(elemEnd).replace(closeTag, "")).trimEnd() + "\n</resources>\n"
                    file.writeBytes(repaired.toByteArray(Charsets.UTF_8))
                }
            }
    }
}

val mokoGeneratedDir = layout.buildDirectory.dir("generated/moko-resources").map { it.asFile }

tasks.matching { it.name.startsWith("generateMR") || it.name == "packageAndroidMainResources" }.configureEach {
    val dirProvider = mokoGeneratedDir
    doFirst { MokoXmlSanitizer.sanitize(dirProvider.get()) }
    doLast { MokoXmlSanitizer.sanitize(dirProvider.get()) }
}
