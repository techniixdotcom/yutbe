plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.hhst.youtubelite"
    compileSdk = 36

    installation {
        installOptions.add("-t")
    }

    lint {
        disable.add("MissingTranslation")
        disable.add("ExtraTranslation")
        abortOnError = false
        // lintVitalAnalyzeRelease needs several GB of heap; skip it on release builds
        checkReleaseBuilds = false
    }

    defaultConfig {
        // kept as com.vidlite.app so existing installs update in place
        applicationId = "com.vidlite.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "v1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    buildTypes {
        release {
            // Minify can be disabled with -PenableMinify=false for machines
            // with limited RAM (R8 needs several GB of heap).
            val minify = (project.findProperty("enableMinify") as String?) != "false"
            isMinifyEnabled = minify
            isShrinkResources = minify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
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
            // extractor libraries ship conflicting META-INF entries
            excludes += setOf(
                "META-INF/README.md",
                "META-INF/CHANGES",
                "META-INF/COPYRIGHT",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )
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
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    // NewPipeExtractor is vendored as source (patched WizeStream tree) under
    // org.schabi.newpipe.extractor; these are its external dependencies.
    implementation(libs.nanojson)
    implementation(libs.jsoup)
    implementation(libs.commons.lang3)
    implementation(libs.brotli.dec)
    implementation(libs.java.websocket)
    implementation(libs.rhino)
    implementation(libs.autolink)
    implementation(libs.protobuf.java)
    implementation(libs.hlsdownloader)
    compileOnly(libs.spotbugs.annotations)
    compileOnly(libs.org.json)
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
