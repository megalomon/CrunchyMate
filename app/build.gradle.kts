import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.process.ExecOperations
import javax.inject.Inject

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

// Ausgabeorte des Rust-Builds; einmal benannt, weil Source-Sets und Tasks denselben Pfad brauchen
val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs")
val uniffiOutDir = layout.buildDirectory.dir("generated/uniffi")

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
            kotlin.srcDir(uniffiOutDir.get().asFile)
            jniLibs.srcDir(rustJniLibsDir.get().asFile)
        }
    }

    testOptions {
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
    // Ältere Espresso-Versionen finden InputManager.getInstance auf neuen Android-Versionen nicht mehr
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// Rust-Kern bauen und Kotlin-Bindings erzeugen

val repoRoot: File = rootProject.projectDir
val rustTarget = "aarch64-linux-android"
val coreLibrary = repoRoot.resolve("target/$rustTarget/release/libcrunchymate_core.so")

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

// Bricht früh mit einer verständlichen Meldung ab, statt mit einem kryptischen Exec-Fehler mitten im Build
val checkRustToolchain by tasks.registering {
    group = "rust"
    description = "Prüft, ob cargo, cargo-ndk und das NDK verfügbar sind."
    val cargo = cargoExecutable
    val ndkPath = ndkDirectory.map { it.asFile }
    doLast {
        fun succeeds(vararg command: String): Boolean =
            runCatching {
                ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()
                    .also { it.inputStream.readBytes() }
                    .waitFor() == 0
            }.getOrDefault(false)
        if (!succeeds(cargo, "--version")) {
            throw GradleException(
                "cargo wurde nicht gefunden ($cargo). Rust über rustup installieren (https://rustup.rs) " +
                    "oder die Umgebungsvariable CARGO auf die cargo-Datei setzen.",
            )
        }
        if (!succeeds(cargo, "ndk", "--version")) {
            throw GradleException("cargo-ndk fehlt. Installieren mit: cargo install cargo-ndk --locked")
        }
        if (!ndkPath.get().isDirectory) {
            throw GradleException(
                "Das Android-NDK fehlt (erwartet in ${ndkPath.get()}). " +
                    "Installieren mit: sdkmanager --install \"ndk;30.0.16248370\"",
            )
        }
    }
}

val cargoBuildArm64 =
    tasks.register<Exec>("cargoBuildArm64") {
        group = "rust"
        description = "Baut libcrunchymate_core.so für arm64-v8a mit cargo-ndk."
        dependsOn(checkRustToolchain)
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
            rustJniLibsDir.get().asFile.path,
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

// Die App ist offline-first: Bilder liegen lokal, Netzwerkclients gehören nur in den Rust-Kern.
// Ein versehentlich mitgezogenes Coil-Netzwerkmodul oder ein HTTP-Client würde sonst unbemerkt Netzbilder erlauben.
val checkNoNetworkClients by tasks.registering {
    val forbidden =
        listOf(
            Regex("io\\.coil-kt\\.coil3:coil-network-.*"),
            Regex("com\\.squareup\\.okhttp3:okhttp(-.*)?"),
            Regex("io\\.ktor:.*client.*"),
        )
    val roots =
        listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").associateWith { name ->
            configurations.named(name).flatMap { it.incoming.resolutionResult.rootComponent }
        }
    doLast {
        val violations = mutableListOf<String>()
        roots.forEach { (name, root) ->
            val seen = mutableSetOf<ComponentIdentifier>()
            val pending = ArrayDeque<ResolvedComponentResult>().apply { add(root.get()) }
            while (pending.isNotEmpty()) {
                val component = pending.removeFirst()
                if (!seen.add(component.id)) continue
                val module = component.moduleVersion
                if (module != null && forbidden.any { it.matches("${module.group}:${module.name}") }) {
                    violations += "$name: ${module.group}:${module.name}"
                }
                component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { pending.add(it.selected) }
            }
        }
        check(violations.isEmpty()) { "Netzwerkclients auf dem Klassenpfad der App: $violations" }
    }
}

tasks.named("preBuild") {
    dependsOn(cargoBuildArm64, generateUniffiBindings, checkNoNetworkClients)
}

// ktlint schließt die erzeugten Bindings aus, sieht das Verzeichnis aber im Source-Set;
// mustRunAfter genügt für die Task-Validierung, ohne ktlint vom Rust-Build abhängig zu machen.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    mustRunAfter(generateUniffiBindings)
}

// Fixture-Datenbank für die Geräte-Tests: kommt als Asset in die Test-APK

@CacheableTask
abstract class GenerateFixtureDb
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val rustSources: ConfigurableFileCollection

        // Der cargo-Pfad ist je Rechner verschieden und darf den Cache-Schlüssel nicht bestimmen
        @get:Internal
        abstract val cargo: Property<String>

        // "stable" in rust-toolchain.toml ist beweglich; die Datei selbst gehört trotzdem zum Schlüssel
        @get:Input
        abstract val toolchainSpec: Property<String>

        @get:Internal
        abstract val workDir: DirectoryProperty

        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @TaskAction
        fun generate() {
            val target = outputDir.get().asFile.resolve("fixture.db")
            target.parentFile.mkdirs()
            execOperations.exec {
                workingDir = workDir.get().asFile
                commandLine(cargo.get(), "run", "-p", "crunchymate-core", "--example", "make_fixture_db", "--", target.path)
            }
        }
    }

val generateFixtureDb =
    tasks.register<GenerateFixtureDb>("generateFixtureDb") {
        group = "rust"
        description = "Erzeugt die Fixture-Datenbank für die Geräte-Tests."
        rustSources.from(rustInputs, repoRoot.resolve("core/examples/make_fixture_db.rs"))
        dependsOn(checkRustToolchain)
        cargo.set(cargoExecutable)
        toolchainSpec.set(providers.fileContents(rootProject.layout.projectDirectory.file("rust-toolchain.toml")).asText)
        workDir.set(rootProject.layout.projectDirectory)
        outputDir.set(layout.buildDirectory.dir("fixture"))
    }

androidComponents.onVariants { variant ->
    variant.androidTest
        ?.sources
        ?.assets
        ?.addGeneratedSourceDirectory(generateFixtureDb, GenerateFixtureDb::outputDir)
}
