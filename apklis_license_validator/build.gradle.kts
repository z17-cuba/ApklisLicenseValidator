plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    id("maven-publish")
}

android {
    namespace = "cu.uci.android.apklis_license_validator"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
        singleVariant("debug") {
            withSourcesJar()
            withJavadocJar()
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "cu.uci.android"
                artifactId = "apklis_license_validator"
                version = "1.0.0"
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Additional dependencies from the library
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    // Because WebSocketLib is a custom implementation on top of OkHttp's WebSocket:
    implementation(libs.okhttp.urlconnection)

    // Optional for better JSON parsing
    implementation(libs.gson)

    // QR Code generation/view
    implementation(libs.zxing.android.embedded)
    implementation(libs.zxing.core)
    implementation(libs.compose.qr.code)

    // Cryptography library
    implementation(libs.security.crypto)
}