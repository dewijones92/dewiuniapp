// Top-level build file where you can add configuration options common to all sub-projects/modules.
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
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

// One lint policy for every Android module, current and future.
val lintPolicy: Lint.() -> Unit = {
  warningsAsErrors = true
  abortOnError = true
  // Version-freshness nags break CI on every upstream release, not on code changes.
  // Dependency updates are handled deliberately, not by lint.
  disable += listOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
}

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

  plugins.withId("com.android.application") {
    extensions.configure<ApplicationExtension> { lint(lintPolicy) }
  }
  plugins.withId("com.android.library") {
    extensions.configure<LibraryExtension> { lint(lintPolicy) }
  }
}
