plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinAndroid)
  alias(libs.plugins.whetstone)
  alias(libs.plugins.kotlinKapt)
}

android {
  namespace = "arun.com.chromer"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.kabukabu.hiraku"
    minSdk = 28
    targetSdk = 34
    versionCode = 220
    versionName = "2.2.0"

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
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.1"
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

whetstone {
  addOns.compose.set(true)
  addOns.workManager.set(false)
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation("androidx.compose.material:material-icons-core")
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
