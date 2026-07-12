// Top-level build file where you can add configuration options common to all sub-projects/modules.
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.detekt) apply false
}

val detektFormatting = libs.detekt.formatting

// Every module gets the same static-analysis gate; adding a module adds its gate.
subprojects {
  apply(plugin = "io.gitlab.arturbosch.detekt")

  extensions.configure<DetektExtension> {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    source.setFrom(
      "src/main/java",
      "src/main/kotlin",
      "src/test/java",
      "src/test/kotlin",
      "src/androidTest/java",
      "src/androidTest/kotlin",
    )
    parallel = true
    autoCorrect = true
  }

  dependencies {
    add("detektPlugins", detektFormatting)
  }
}
