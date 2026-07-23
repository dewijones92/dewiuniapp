plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dewijones92.uniapp.playback"
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    // Cast: CastPlayer + CastContext, so playback can hand off to a Chromecast.
    implementation(libs.media3.cast)
    implementation(libs.play.services.cast.framework)

    testImplementation(project(":core:data"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
