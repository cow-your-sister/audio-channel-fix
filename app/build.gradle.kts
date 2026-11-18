import com.android.build.gradle.tasks.PackageAndroidArtifact

plugins {
    alias(libs.plugins.android.application)
}

val manualVersionName = providers.gradleProperty("versionName").orNull
val manualVersionCode = providers.gradleProperty("versionCode").orNull?.let {
    it.toIntOrNull()
        ?: throw GradleException("Gradle property 'versionCode' must be a positive integer.")
}
val gitCommitCount = if (manualVersionName == null || manualVersionCode == null) {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toIntOrNull()
        ?: throw GradleException("Unable to determine the Git commit count. Build from a Git checkout or set both -PversionName and -PversionCode.")
} else {
    null
}
val resolvedVersionName = manualVersionName ?: "1.0.${requireNotNull(gitCommitCount)}"
val resolvedVersionCode = manualVersionCode ?: requireNotNull(gitCommitCount)

if (resolvedVersionName.isBlank()) {
    throw GradleException("Gradle property 'versionName' must not be empty.")
}
if (resolvedVersionCode !in 1..2_100_000_000) {
    throw GradleException("versionCode must be between 1 and 2100000000.")
}

android {
    namespace = "com.example.audiochannelfix"

    buildFeatures { buildConfig = true }

    defaultConfig {
        compileSdk = 37
        minSdk = 27
        targetSdk = 37
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            vcsInfo.include = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                "${rootProject.name}-${variant.name}-${resolvedVersionName}.apk"
            )
        }
    }
}

// https://stackoverflow.com/a/77745844
tasks.withType<PackageAndroidArtifact> {
    doFirst { appMetadata.asFile.orNull?.writeText("") }
}

dependencies {
    compileOnly(libs.libxposed)
}