plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

/**
 * Google Maps key, read from `local.properties` (`MAPS_API_KEY=...`) or the `MAPS_API_KEY`
 * environment variable so it never reaches version control. The app still builds and runs without
 * one; the map then renders empty tiles while everything else, including the AppFunctions demo,
 * keeps working.
 */
val mapsApiKey: String =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { contents ->
            contents
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.startsWith("MAPS_API_KEY=") }
                ?.substringAfter('=')
                ?.trim()
                .orEmpty()
        }
        .orElse(providers.environmentVariable("MAPS_API_KEY"))
        .orElse("")
        .get()

android {
    namespace = "com.example.spotatlas"

    // AppFunctions are part of the platform from Android 16 (API 36); the Jetpack library keeps the
    // app installable further back, where the function service simply never gets bound.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.spotatlas"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Required so the AppFunctions compiler emits one aggregated schema for the whole module.
    arg("appfunctions:aggregateAppFunctions", "true")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // AppFunctions: the on-device agent integration this demo is about.
  implementation(libs.androidx.appfunctions)
  ksp(libs.androidx.appfunctions.compiler)

  // Google Maps
  implementation(libs.maps.compose)
  implementation(libs.play.services.maps)
}
