plugins {
    alias(libs.plugins.android.application)
}

// Set build directory outside Android Studio development environment.
var externalBuildDir = project.findProperty("EXTERNAL_BUILD_DIR") as String
layout.buildDirectory.set(File("${externalBuildDir}${project.name}"))

android {
    namespace = "com.orienteerfeed.ofeed_sidroid_connector"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orienteerfeed.ofeed_sidroid_connector"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("KEYSTORE_FILE") as String)
            storePassword = project.findProperty("KEYSTORE_PASSWORD") as String
            keyAlias = project.findProperty("CONNECTOR_KEY_ALIAS") as String
            keyPassword = project.findProperty("CONNECTOR_KEY_PASSWORD") as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.okhttp)
    implementation(libs.okhttplog)
    implementation(libs.preference)
    implementation(libs.scanner)
    implementation(libs.appupdate)
    coreLibraryDesugaring(libs.desugarjdklibs)
}
