plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.milkdrop"
    compileSdk = 35
    ndkVersion = "25.2.9519653"

    defaultConfig {
        applicationId = "com.example.milkdrop"
        minSdk = 21
        targetSdk = 35
        versionCode = 16
        versionName = "1.4.16"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-frtti", "-fexceptions", "-O3", "-ffast-math")
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-21",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    splits {
        abi {
            isEnable = false  // fat APK with both ABIs
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.leanback:leanback:1.2.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // NO Google Play Services dependencies

    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val licenseAssetsDir = layout.buildDirectory.dir("generated/license-assets")

android.sourceSets.getByName("main").assets.srcDir(licenseAssetsDir)

tasks.register("copyLicenseAssets") {
    val projectMDir = layout.projectDirectory.dir("src/main/cpp/projectm")
    val outputDir = licenseAssetsDir
    inputs.dir(projectMDir)
    outputs.dir(outputDir)
    doLast {
        val destination = outputDir.get().asFile
        destination.mkdirs()
        projectMDir.file("LICENSE.txt").asFile.copyTo(
            destination.resolve("projectM-LGPL-2.1.txt"),
            overwrite = true
        )
        projectMDir.file("vendor/projectm-eval/LICENSE.md").asFile.copyTo(
            destination.resolve("projectm-eval-MIT.txt"),
            overwrite = true
        )
        projectMDir.file("vendor/hlslparser/LICENSE").asFile.copyTo(
            destination.resolve("hlslparser-MIT.txt"),
            overwrite = true
        )
        val revisionCommand = ProcessBuilder(
            "git", "-C", projectMDir.asFile.absolutePath, "rev-parse", "HEAD"
        ).start()
        val revision = revisionCommand.inputStream.bufferedReader().readText().trim()
        val revisionResult = revisionCommand.waitFor()
        if (revisionResult != 0 || revision.isEmpty()) {
            throw GradleException("Cannot read the projectM source revision")
        }
        destination.resolve("projectM-source.txt").writeText(
            "projectM $revision\n" +
                "https://github.com/projectM-visualizer/projectm/tree/$revision\n"
        )
    }
}

tasks.named("preBuild").configure {
    dependsOn("copyLicenseAssets")
}
