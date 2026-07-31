plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    // Busy exposes its in-flight work as a StateFlow, which is what lets one indicator in
    // the UI observe work reported from the HTTP and extraction layers. :lib:ytdlp already
    // takes the same dependency, so this does not widen what a published bundle needs.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
