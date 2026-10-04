import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
}

val appVersionName = "v1.0.4"
val appVersionCode = 10004
val apkBaseName = "yutbe" + appVersionName.removePrefix("v")

base {
    archivesName.set(apkBaseName)
}

// Release signing is supplied by BUILD.sh through environment variables, so no secret is ever
// stored inside the repository.
val releaseKeystoreFile: String? = System.getenv("YUTBE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.yutbe.app"
    compileSdk = 37

    lint {
        disable.add("MissingTranslation")
        disable.add("ExtraTranslation")
        abortOnError = false
        checkReleaseBuilds = false
    }

    defaultConfig {
        applicationId = "com.yutbe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 32-bit x86 phones have not been made for years; dropping them keeps the APK smaller.
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = System.getenv("YUTBE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("YUTBE_KEY_ALIAS")
                keyPassword = System.getenv("YUTBE_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            if (releaseKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    androidResources {
        // Only the languages the app itself is translated into; libraries ship dozens more.
        localeFilters += listOf("en", "es", "fr", "ja", "ko", "ru", "tr", "zh", "zh-rTW")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "META-INF/services/javax.script.ScriptEngineFactory"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    implementation(libs.newpipeextractor)
    implementation(libs.isoparser)
    implementation(libs.gson)
    implementation(libs.commons.io)
    implementation(libs.picasso)
    implementation(libs.media)
    implementation(libs.photoview)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.mmkv)
    implementation(libs.activity)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.constraintlayout)
    implementation(libs.swiperefreshlayout)
    implementation(libs.webkit)
    implementation(libs.viewpager2)
    implementation(libs.recyclerview)
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

abstract class ExportApkTask : DefaultTask() {
    @get:InputFiles
    abstract val apkFolder: DirectoryProperty

    @get:Internal
    abstract val artifactsLoader: Property<BuiltArtifactsLoader>

    @get:OutputFile
    abstract val outputApk: RegularFileProperty

    @TaskAction
    fun export() {
        val builtArtifacts = artifactsLoader.get().load(apkFolder.get())
            ?: throw GradleException("No APK was produced in ${apkFolder.get().asFile}")
        val apk = builtArtifacts.elements.singleOrNull()
            ?: throw GradleException("Expected exactly one APK, found ${builtArtifacts.elements.size}")
        val target = outputApk.get().asFile
        target.parentFile.mkdirs()
        File(apk.outputFile).copyTo(target, overwrite = true)
        logger.lifecycle("APK: ${target.absolutePath}")
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val fileName = if (variant.buildType == "release") {
            "$apkBaseName.apk"
        } else {
            "$apkBaseName-${variant.name}.apk"
        }
        val exportTask = tasks.register<ExportApkTask>("export${variantName}Apk") {
            apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
            artifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
            outputApk.set(rootProject.layout.projectDirectory.file("dist/$fileName"))
        }
        tasks.matching { it.name == "assemble$variantName" }.configureEach {
            finalizedBy(exportTask)
        }
    }
}
