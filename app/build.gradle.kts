// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val keystore = rootProject.file("keystore.jks")

android {
    namespace = "com.example.medtap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.medtap"
        minSdk = 26
        targetSdk = 35

        // Le numéro de version vient du compteur de builds de CI, avec un repli à 1 pour
        // les compilations locales. Il était figé à 1 : Android refuse d'installer par
        // dessus une version dont le numéro a baissé, et surtout un APK qui prétend être
        // le même que celui déjà installé n'a aucune raison d'être proposé comme mise à
        // jour. Le nom des Releases suivait déjà le compteur ; l'APK, lui, mentait.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        if (keystore.exists()) create("release") {
            storeFile = keystore
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    splits { abi { isEnable = false } }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Les tests tournent sur la JVM, sans émulateur : tout ce qui compte les jours est
    // écrit contre l'interface `MedDao`, pas contre Room, donc un faux DAO en mémoire
    // suffit. Une suite qui demande un appareil est une suite qu'on finit par ne plus
    // lancer.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
