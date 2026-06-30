import ArtifactsUtilities.BuildType.DEBUG
import ArtifactsUtilities.BuildType.RELEASE
import ArtifactsUtilities.buildName
import com.android.build.api.dsl.ApkSigningConfig
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

fun ApkSigningConfig.fromKeystoreProperties(keystorePropertiesPath: String) {
    val keystorePropertiesFile = rootProject.file(keystorePropertiesPath)
    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
        storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
        storePassword = keystoreProperties.getProperty("storePassword")
    }
}

android {
    namespace = AppConfig.namespace
    compileSdk = AppConfig.targetSdk

    defaultConfig {
        applicationId = AppConfig.namespace
        minSdk = AppConfig.minSdk
        versionCode = AppConfig.versionCode
        versionName = AppConfig.versionName
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        val javaVersion = AppConfig.javaVersion
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }
    signingConfigs {
        create("release") {
            fromKeystoreProperties("androidApp/keystore/keystore.properties")
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

fun CopyArtifacts.setup(buildType: ArtifactsUtilities.BuildType) = setup(
    appName = rootProject.name,
    buildType = buildType,
    versionProperties = CopyArtifacts.VersionProperties(
        versionCode = AppConfig.versionCode,
        versionName = AppConfig.versionName
    ),
    //for single flavored projects should omit the flavor
    intoFolder = "$rootDir/releases/android/"
)
//this defines the building of the debug apk for a flavor
tasks.register<CopyArtifacts>(buildName("prepare", "android", "debug")) {
    setup(buildType = DEBUG)
}
//this defines the building of the release apk and aabs for a flavor
tasks.register<CopyArtifacts>(buildName("prepare", "android", "release")) {
    setup(buildType = RELEASE)
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(project(":shared"))
    debugImplementation(libs.jetbrains.compose.ui.tooling)

    implementation(libs.jetbrains.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.napier)
}