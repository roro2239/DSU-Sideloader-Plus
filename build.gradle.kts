plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false

    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false

    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

val gitCommitsCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

val versionCode by extra { gitCommitsCount }
val versionName by extra { "1.0.0" }
val packageName by extra { "yangfentuozi.dsusideloaderplus" }

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
