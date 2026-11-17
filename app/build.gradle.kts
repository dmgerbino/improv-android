plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val rustDir = rootProject.file("rust")
val cargoTargetDir = System.getenv("CARGO_TARGET_DIR")
  ?: "${System.getProperty("user.home")}/.cache/projects/improv-android-rust/cargo"

android {
  namespace = "com.droid.improv"
  compileSdk = 37
  ndkVersion = "29.0.14206865"

  defaultConfig {
    applicationId = "com.droid.improv"
    minSdk = 31
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }

  sourceSets {
    getByName("debug").jniLibs.srcDir(layout.buildDirectory.dir("rustJniLibs/debug").get().asFile)
    getByName("release").jniLibs.srcDir(layout.buildDirectory.dir("rustJniLibs/release").get().asFile)
  }

  buildTypes {
    debug {
      enableUnitTestCoverage = true
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  lint {
    // UniFFI's generated JavaLangRefCleaner checks Class.forName("java.lang.ref.Cleaner")
    // at runtime and falls back to JnaCleaner on API 31-32.
    disable += "NewApi"
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

  testOptions {
    unitTests.isReturnDefaultValues = true
    unitTests.all {
      it.systemProperty("jna.library.path", "$cargoTargetDir/debug")
    }
  }
}

kotlin {
  jvmToolchain(17)
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
  implementation("androidx.compose.material3.adaptive:adaptive")
  implementation("androidx.compose.material3:material3-window-size-class")
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

  // JNA required by UniFFI Kotlin bindings
  implementation("net.java.dev.jna:jna:5.19.1@aar")
  testImplementation("net.java.dev.jna:jna:5.19.1")
}

// Compile Rust for both ARM64 (Pixel + ARM Googlebook) and x86_64 (Intel Googlebook)
val buildRustDebug by tasks.registering(Exec::class) {
  workingDir = rustDir
  environment("CARGO_TARGET_DIR", cargoTargetDir)
  commandLine(
    "cargo", "ndk",
    "-t", "arm64-v8a",
    "-t", "x86_64",
    "--platform", "31",
    "-o", layout.buildDirectory.dir("rustJniLibs/debug").get().asFile.absolutePath,
    "build", "-p", "improv_android_ffi"
  )
}

val buildRustRelease by tasks.registering(Exec::class) {
  workingDir = rustDir
  environment("CARGO_TARGET_DIR", cargoTargetDir)
  commandLine(
    "cargo", "ndk",
    "-t", "arm64-v8a",
    "-t", "x86_64",
    "--platform", "31",
    "-o", layout.buildDirectory.dir("rustJniLibs/release").get().asFile.absolutePath,
    "build", "--release", "-p", "improv_android_ffi"
  )
}

tasks.matching { it.name == "mergeDebugJniLibFolders" }.configureEach {
  dependsOn(buildRustDebug)
}
tasks.matching { it.name == "mergeReleaseJniLibFolders" }.configureEach {
  dependsOn(buildRustRelease)
}
