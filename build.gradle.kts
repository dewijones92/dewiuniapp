// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.detekt)
}

dependencies {
  detektPlugins(libs.detekt.formatting)
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom("$rootDir/config/detekt/detekt.yml")
  source.setFrom(
    "app/src/main/java",
    "app/src/test/java",
    "app/src/androidTest/java",
  )
  parallel = true
  autoCorrect = true
}
