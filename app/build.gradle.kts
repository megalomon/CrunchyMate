plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

val baseVersion = providers.gradleProperty("crunchymate.baseVersion").get()
val appVersionCode =
    providers
        .gradleProperty("crunchymate.versionCode")
        .map(String::toInt)
        .getOrElse(1)
val appVersionName =
    providers
        .gradleProperty("crunchymate.versionName")
        .getOrElse("$baseVersion-dev")

android {
    namespace = "de.crunchymate"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 2
            }
    }
    // AGP 9.4 nutzt sonst NDK r28; der Rust-Kern ist gegen r30 geprüft
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "de.crunchymate"
        minSdk = 34
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "de.crunchymate.CrunchyMateTestRunner"
    }

    // Signierung nur, wenn SIGNING_KEYSTORE_PATH gesetzt ist (CI, lokaler Test); sonst unsignierte APK
    val signingKeystorePath = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
    if (signingKeystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(signingKeystorePath)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("build/generated/uniffi")
            jniLibs.srcDir("build/rustJniLibs")
        }
    }

    testOptions {
        // android.util.Log liefert in JVM-Tests Standardwerte statt Fehler
        unitTests.isReturnDefaultValues = true
        // Robolectric braucht die Ressourcen (strings, drawables) für Compose-Tests
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        error += listOf("HardcodedText", "MissingTranslation", "UnusedResources")
    }
}

kotlin {
    jvmToolchain(17)
}

ktlint {
    filter {
        // Von UniFFI erzeugter Code folgt nicht dem Projektstil
        exclude { it.file.path.contains("/build/") }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)
    // Android braucht das AAR mit den nativen JNA-Bibliotheken statt des JVM-JARs
    implementation(libs.jna) {
        artifact { type = "aar" }
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// Rust-Kern bauen und Kotlin-Bindings erzeugen

val repoRoot: File = rootProject.projectDir
val rustTarget = "aarch64-linux-android"
val coreLibrary = repoRoot.resolve("target/$rustTarget/release/libcrunchymate_core.so")
val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs")
val uniffiOutDir = layout.buildDirectory.dir("generated/uniffi")

// Reihenfolge: CARGO (CI, Overrides), rustup-Installation im Home, sonst cargo aus dem PATH.
// Das Distributions-cargo im PATH ignoriert rust-toolchain.toml und ist zu alt.
val cargoExecutable: String =
    providers.environmentVariable("CARGO").orNull
        ?: File(System.getProperty("user.home"), ".cargo/bin/cargo")
            .takeIf { it.canExecute() }
            ?.path
        ?: "cargo"

val rustInputs =
    files(
        repoRoot.resolve("core/src"),
        repoRoot.resolve("core/Cargo.toml"),
        repoRoot.resolve("Cargo.toml"),
        repoRoot.resolve("Cargo.lock"),
        repoRoot.resolve("rust-toolchain.toml"),
    )

val ndkDirectory = androidComponents.sdkComponents.ndkDirectory

val cargoBuildArm64 =
    tasks.register<Exec>("cargoBuildArm64") {
        group = "rust"
        description = "Baut libcrunchymate_core.so für arm64-v8a mit cargo-ndk."
        inputs.files(rustInputs).withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.file(coreLibrary)
        outputs.dir(rustJniLibsDir)
        workingDir = repoRoot
        commandLine(
            cargoExecutable,
            "ndk",
            "-t",
            "arm64-v8a",
            "-o",
            "app/build/rustJniLibs",
            "build",
            "--release",
            "-p",
            "crunchymate-core",
        )
        val ndkHome = ndkDirectory.map { it.asFile.path }
        doFirst {
            if (System.getenv("ANDROID_NDK_HOME").isNullOrEmpty()) {
                environment("ANDROID_NDK_HOME", ndkHome.get())
            }
        }
    }

val generateUniffiBindings =
    tasks.register<Exec>("generateUniffiBindings") {
        group = "rust"
        description = "Erzeugt die Kotlin-Bindings aus der gebauten Kern-Bibliothek."
        dependsOn(cargoBuildArm64)
        inputs.file(coreLibrary).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(repoRoot.resolve("core/uniffi.toml")).withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.dir(uniffiOutDir)
        workingDir = repoRoot
        val outDir = uniffiOutDir.get().asFile
        commandLine(
            cargoExecutable,
            "run",
            "-p",
            "crunchymate-core",
            "--features",
            "cli",
            "--bin",
            "uniffi-bindgen",
            "--",
            "generate",
            "--library",
            coreLibrary.path,
            "--language",
            "kotlin",
            // Ohne Formatierung bleibt die Ausgabe unabhängig von einem installierten ktlint
            "--no-format",
            "--out-dir",
            outDir.path,
        )
        // Veraltete Dateien aus früheren Läufen dürfen nicht mitkompiliert werden
        doFirst { outDir.deleteRecursively() }
    }

tasks.named("preBuild") {
    dependsOn(cargoBuildArm64, generateUniffiBindings)
}

// ktlint schließt die erzeugten Bindings aus, sieht das Verzeichnis aber im Source-Set;
// mustRunAfter genügt für die Task-Validierung, ohne ktlint vom Rust-Build abhängig zu machen.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    mustRunAfter(generateUniffiBindings)
}

// Fixture-Datenbank für die Geräte-Tests: kommt als Asset in die Test-APK

val fixtureDbFile = layout.buildDirectory.file("fixture/fixture.db")

val generateFixtureDb =
    tasks.register<Exec>("generateFixtureDb") {
        group = "rust"
        description = "Erzeugt die Fixture-Datenbank für die Geräte-Tests."
        inputs.files(rustInputs).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(repoRoot.resolve("core/examples/make_fixture_db.rs")).withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.dir(fixtureDbFile.map { it.asFile.parentFile })
        workingDir = repoRoot
        val target = fixtureDbFile.get().asFile
        commandLine(cargoExecutable, "run", "-p", "crunchymate-core", "--example", "make_fixture_db", "--", target.path)
        doFirst { target.parentFile.mkdirs() }
    }

androidComponents.onVariants { variant ->
    variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(generateFixtureDb) {
        objects.directoryProperty().fileValue(fixtureDbFile.get().asFile.parentFile)
    }
}
