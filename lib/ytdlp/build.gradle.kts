plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dewijones92.uniapp.ytdlp"
    compileSdk = 37
    defaultConfig {
        minSdk = 34
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Lint policy: keep identical in every Android module (see app).
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Version-freshness nags break CI on every upstream release, not on code changes.
        // Dependency updates are handled deliberately, not by lint.
        disable += listOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
