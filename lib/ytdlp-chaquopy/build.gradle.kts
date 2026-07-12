plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.chaquopy)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dewijones92.uniapp.ytdlp.chaquopy"
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
}

chaquopy {
    defaultConfig {
        // Must match the build machine's python3 minor version (Chaquopy requirement).
        version = "3.12"
        pip {
            install("yt-dlp")
        }
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":lib:ytdlp"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
