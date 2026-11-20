plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinAndroid)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.whetstone)
  alias(libs.plugins.kotlinKapt)
}

android {
  namespace = "arun.com.chromer"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.kabukabu.hiraku"
    // Glasmio 0.1.3 (the real Liquid Glass renderer) declares
    // minSdk 33: its lens/refraction pipeline is built on AGSL
    // RuntimeShader, which only exists on API 33+.
    minSdk = 33
    targetSdk = 34
    versionCode = 233
    versionName = "2.3.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  signingConfigs {
    create("release") {
      val ksPath = System.getenv("HIRAKU_KEYSTORE_FILE")
      if (!ksPath.isNullOrBlank()) {
        storeFile = file(ksPath)
        storePassword = System.getenv("HIRAKU_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("HIRAKU_KEY_ALIAS") ?: "hiraku"
        keyPassword = System.getenv("HIRAKU_KEY_PASSWORD")
          ?: System.getenv("HIRAKU_KEYSTORE_PASSWORD")
      }
    }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".dev"
    }
    release {
      isMinifyEnabled = false
      val ksPath = System.getenv("HIRAKU_KEYSTORE_FILE")
      if (!ksPath.isNullOrBlank() && file(ksPath).exists() && file(ksPath).length() > 0) {
        signingConfig = signingConfigs.getByName("release")
      }
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

// Anvil (pulled in by Whetstone) announces "Anvil is currently
// incompatible with the K2 compiler and the language version will
// be overridden to 1.9" and pins the language version to 1.9,
// which the Kotlin 2.4 compiler rejects outright
// (UNSUPPORTED_LANGUAGE_VERSION). Force 2.4 on every Kotlin
// compile task. KotlinCompileTask is the abstract base of both
// the regular compile tasks and the kapt tasks, so the kapt
// worker's stub compilation is covered as well.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompileTask>().configureEach {
  compilerOptions {
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4)
  }
}

whetstone {
  addOns.compose.set(true)
  addOns.workManager.set(false)
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation("androidx.compose.material:material-icons-core")
  // v2.3.0: Apps/History/Schedule etc. live in the extended set
  // (release builds tree-shake the unused icons).
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.browser:browser:1.8.0")

  implementation(libs.molecule.runtime)

  // Dagger
  implementation(libs.dagger)
  kapt(libs.dagger.compiler)

  testImplementation(libs.junit)

  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}
