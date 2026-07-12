// compileSdk/minSdk, Java level, and lint policy come from the root build's androidDefaults.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dewijones92.uniapp.ytdlp"
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":lib:common"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
