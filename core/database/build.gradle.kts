plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.dewijones92.uniapp.database"
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":core:data"))
    // Implements the playback module's PlaybackProgressStore port with Room.
    api(project(":core:playback"))
    // api: UniAppDatabase extends RoomDatabase, so Room is part of this module's ABI.
    api(libs.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    // Chapters are persisted as a compact JSON array in one episode column.
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
