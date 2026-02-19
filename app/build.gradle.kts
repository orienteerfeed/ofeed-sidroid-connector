import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    kotlin("android")
}

// Set build directory outside Android Studio development environment.
val externalBuildDir = project.findProperty("EXTERNAL_BUILD_DIR") as String
layout.buildDirectory.set(File("${externalBuildDir}OFeed-SIDroid-Connector/${project.name}"))

extensions.configure<ApplicationExtension> {
    namespace = "com.orienteerfeed.ofeed_sidroid_connector"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orienteerfeed.ofeed_sidroid_connector"
        minSdk = 23
        targetSdk = 36
        versionCode = 19
        versionName = "beta-19"  // 19=Help scan/paste. 18=Settings layout. 17=Credentials help. 16=Settings updates. 15=Clear OFeed error message. 14=App link/share/paste. 13=Check for nothing to upload. 12=Remove incomplete persons. 11=Czech. 10=OResults. Upload of local file.
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
