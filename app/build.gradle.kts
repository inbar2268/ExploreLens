plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.explorelens"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.explorelens"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Base dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // ARCore (explicit version)
    implementation("com.google.ar:core:1.37.0")

    // Sceneform dependencies with proper exclusions
    implementation("com.google.ar.sceneform.ux:sceneform-ux:1.17.1") {
        exclude("com.android.support")
    }
    implementation("com.google.ar.sceneform:core:1.17.1") {
        exclude("com.android.support")
    }
    implementation("com.google.ar.sceneform:assets:1.17.1") {
        exclude("com.android.support")
    }

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}