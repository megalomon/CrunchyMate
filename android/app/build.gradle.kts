plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.crunchymate"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.crunchymate"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val releaseKeystore = file("release.keystore")
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = "crunchymate"
                keyAlias = "crunchymate"
                keyPassword = "crunchymate"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.webkit:webkit:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.json:json:20231013")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

val copyWebAssets = tasks.register<Copy>("copyWebAssets") {
    description = "Copies web frontend assets from web/static into assets/web"
    group = "build"
    from(rootProject.file("../web/static"))
    into(file("src/main/assets/web"))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(copyWebAssets)
}
