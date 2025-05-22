import org.jetbrains.kotlin.commonizer.OptimisticNumberCommonizationEnabledKey.alias
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.hilt)
    //id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

// Load properties file with proper error handling
val properties = Properties().apply {
    val propertiesFile = rootProject.file("keys.properties")
    if (propertiesFile.exists()) {
        load(FileInputStream(propertiesFile))
    } else {
        throw GradleException("keys.properties file not found. Please create it based on keys.properties.template")
    }
}

fun getConfigValue(key: String): String {
    return properties[key]?.toString() ?: throw GradleException("Missing required configuration property: $key")
}

android {
    namespace = "com.example.gigs"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gigs"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Fix resource conflicts - correct location in defaultConfig
        resourceConfigurations.clear()
        resourceConfigurations.add("en")

        testInstrumentationRunner = "com.example.gigs.HiltTestRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${getConfigValue("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${getConfigValue("SUPABASE_KEY")}\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "DEBUG", "true")
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "DEBUG", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    // Enhanced packaging options to fix resource conflicts
    packaging {
        resources {
            // Exclude all META-INF files that commonly cause conflicts
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/9/previous-compilation-data.bin",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/rxjava.properties",
                "META-INF/*.kotlin_module"
            )

            // Pick first for files that might be duplicated
            pickFirsts += listOf(
                "META-INF/MANIFEST.MF",
                "META-INF/ASL-2.0.txt",
                "META-INF/LGPL-3.0.txt"
            )
        }
    }

    // Add AAPT options to fix resource ID errors
    aaptOptions {
        noCompress("tflite")
        // Add ignoreAssetsPattern for problematic resources
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"

        // This is important: explicitly handle resources that might cause conflicts
        additionalParameters += listOf(
            "--allow-reserved-package-id",
            "--no-version-vectors"
        )
    }

    // Improved test options
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true

            // Add Robolectric configuration
            all {
                it.systemProperty("robolectric.logging", "SEVERE")
                it.systemProperty("robolectric.packageManager.default", "BINARY")
                it.systemProperty("javax.net.ssl.trustStoreType", "JKS")
                // Helps with Hilt test issues
                it.systemProperty("dagger.hilt.disableModulesHaveInstallInCheck", "true")
            }
        }

        // Explicitly set test execution options
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

// Use consistent versions for all dependencies
object Versions {
    const val core = "1.12.0"
    const val appcompat = "1.6.1"
    const val material = "1.11.0"
    const val room = "2.6.1"
    const val ktor = "3.0.0"
    const val paging = "3.3.6"
    const val hilt = "2.48"
    const val coroutines = "1.7.3"
    const val composeBom = "2024.02.00"
    const val nav = "2.8.0"
    const val accompanist = "0.34.0" // New version constant

}

dependencies {
    implementation(libs.androidx.glance)
    implementation(libs.play.services.fitness)
    implementation(libs.androidx.compose.material)
    // Force specific versions for libraries that might cause conflicts
    configurations.all {
        resolutionStrategy {
            force("androidx.core:core-ktx:${Versions.core}")
            force("androidx.appcompat:appcompat:${Versions.appcompat}")
            force("com.google.android.material:material:${Versions.material}")
            force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
        }
    }

    testImplementation(libs.androidx.runner)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Essential UI dependencies - with forced versions
    implementation("androidx.core:core-ktx:${Versions.core}")
    implementation("androidx.appcompat:appcompat:${Versions.appcompat}")
    implementation("com.google.android.material:material:${Versions.material}")

    // Navigation
    implementation("androidx.navigation:navigation-compose:${Versions.nav}")

    implementation("com.google.accompanist:accompanist-navigation-animation:${Versions.accompanist}")

    // Google Play Services
    implementation("com.google.android.gms:play-services-location:21.0.1")

    //implementation("app.softwork:kotlinx-uuid-core:0.0.19")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation("io.ktor:ktor-client-okhttp:3.0.0")

    //lottie
    implementation("com.airbnb.android:lottie-compose:6.0.0")



    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Security
    implementation("androidx.security:security-crypto:1.0.0")

    // Compose (using BOM)
    implementation(platform("androidx.compose:compose-bom:${Versions.composeBom}"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.runtime:runtime")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")

    // Firebase (using BOM)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Supabase (using BOM)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)

    // Required for serialization with Supabase
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Ensure consistent Kotlin versions
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")

    // Ktor
    implementation("io.ktor:ktor-client-android:${Versions.ktor}")
    implementation("io.ktor:ktor-client-core:${Versions.ktor}")
    implementation("io.ktor:ktor-client-json:${Versions.ktor}")
    implementation("io.ktor:ktor-utils:${Versions.ktor}")
    implementation(libs.ktor.client.cio)
    implementation(libs.jetbrains.annotations)

    // Room
    implementation("androidx.room:room-runtime:${Versions.room}")
    implementation("androidx.room:room-ktx:${Versions.room}")
    implementation("androidx.room:room-common:${Versions.room}")
    kapt("androidx.room:room-compiler:${Versions.room}")

    // Dagger Hilt
    implementation("com.google.dagger:hilt-android:${Versions.hilt}")
    kapt("com.google.dagger:hilt-android-compiler:${Versions.hilt}")

    // Core Dependencies
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Paging
    implementation("androidx.paging:paging-runtime:${Versions.paging}")
    implementation("androidx.paging:paging-compose:${Versions.paging}")

    // Testing Dependencies - Improved configuration
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")

    // Ensure consistent Hilt versions between main and test
    testImplementation("com.google.dagger:hilt-android-testing:${Versions.hilt}")
    kaptTest("com.google.dagger:hilt-android-compiler:${Versions.hilt}")

    // Test implementation to help with ConnectivityManager
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test:rules:1.5.0")

    // Add these for better Hilt testing
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.test.espresso:espresso-core:3.5.1")
    testImplementation("androidx.room:room-testing:${Versions.room}")

    // Android Testing
    androidTestImplementation(platform("androidx.compose:compose-bom:${Versions.composeBom}"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("com.google.dagger:hilt-android-testing:${Versions.hilt}")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
    androidTestImplementation("com.google.truth:truth:1.1.5")
    androidTestImplementation("androidx.paging:paging-testing:${Versions.paging}")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:${Versions.hilt}")

    // Add test orchestrator
    androidTestUtil("androidx.test:orchestrator:1.4.2")

    // Debug Implementation
    debugImplementation(platform("androidx.compose:compose-bom:${Versions.composeBom}"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

}

// Improve Kapt performance and error reporting
kapt {
    correctErrorTypes = true
    useBuildCache = true
    arguments {
        arg("dagger.fastInit", "enabled")
        arg("dagger.experimentalDaggerErrorMessages", "enabled")
        // Add these to help with kapt issues
        arg("kapt.verbose", "true")
        arg("kapt.incremental.apt", "false") // Disable incremental processing temporarily
        arg("kapt.use.worker.api", "false") // Disable worker API temporarily
        arg("dagger.hilt.disableModulesHaveInstallInCheck", "true") // Important for fixing Hilt test issues
    }
    javacOptions {
        // Increase error limit to see all errors
        option("-Xmaxerrs", "500")
    }
}