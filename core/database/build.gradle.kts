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
    // api: UniAppDatabase extends RoomDatabase, so Room is part of this module's ABI.
    api(libs.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
