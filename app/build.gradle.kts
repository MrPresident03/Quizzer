plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

// Play Store signing: the upload key is NOT in the repo. The release workflow decodes it from
// GitHub Actions secrets and passes its path/password in these environment variables. Without
// them (e.g. normal CI), release builds are signed with the debug key so the minified app can
// still be built and tested - those builds can't be uploaded to Play.
val uploadKeystore = System.getenv("QUIZZER_UPLOAD_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
val uploadKeystorePassword = System.getenv("QUIZZER_UPLOAD_KEYSTORE_PASSWORD")

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // Permanent Play Store ID - never change this after the first upload.
    applicationId = "nz.durbs.quizzer"
    minSdk = 26
    targetSdk = 36
    // Builds are numbered by their GitHub Actions run so each upload/APK is a newer version.
    // Release builds take their version name from the git tag (v1.0.0 -> 1.0.0).
    val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    versionCode = ciBuildNumber ?: 1
    versionName = System.getenv("QUIZZER_VERSION_NAME") ?: if (ciBuildNumber != null) "1.1.$ciBuildNumber" else "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (uploadKeystore != null && uploadKeystorePassword != null) {
      create("upload") {
        storeFile = uploadKeystore
        storePassword = uploadKeystorePassword
        keyAlias = "upload"
        keyPassword = uploadKeystorePassword
      }
    }
    create("debugConfig") {
      // A fixed debug key is committed so every dev build (CI or local) is signed the same way and
      // installs over the previous one without losing progress. It's for testing only.
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // Shrink and obfuscate the Play Store build (smaller download); keep rules in proguard-rules.pro.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debugConfig")
    }
    debug {
      // Dev builds ("Quizzer Dev") install alongside the Play Store app instead of replacing it.
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-dev"
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// KSP (Room/Moshi code generation) is only needed for the app's own sources; tests don't use those
// annotations, so skip it for test source sets. Note: KSP2 may log a harmless
// "ApplicationManager.getApplication() is null" NullPointerException from a background thread.
tasks.configureEach {
  if (name.startsWith("ksp") && (name.endsWith("UnitTestKotlin") || name.endsWith("AndroidTestKotlin"))) {
    enabled = false
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
