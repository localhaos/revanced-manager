import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import io.github.z4kn4fein.semver.toVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.devtools)
    alias(libs.plugins.about.libraries)
    alias(libs.plugins.about.libraries.android)
}

val managerVersionName = providers.gradleProperty("managerVersion")
    .orElse(providers.provider { if (version == "unspecified") "1.0.0" else version.toString() })
val managerBuildLabel = providers.gradleProperty("managerBuildLabel").orElse(managerVersionName)
val outputApkFileName = "${rootProject.name}-${managerBuildLabel.get()}.apk"

dependencies {
    implementation(libs.androidx.ktx)
    implementation(libs.runtime.ktx)
    implementation(libs.runtime.compose)
    implementation(libs.splash.screen)
    implementation(libs.activity.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.preferences.datastore)
    implementation(libs.appcompat)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.preview)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.livedata)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.paging3)

    implementation(libs.accompanist.drawablepainter)
    implementation(libs.placeholder.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.appiconloader)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collection.immutable)
    implementation(libs.kotlinx.datetime)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.foundation.layout)
    annotationProcessor(libs.room.compiler)
    ksp(libs.room.compiler)

    implementation(libs.revanced.patcher)
    implementation(libs.revanced.library)
    implementation(libs.morphe.patches.library)
    implementation(libs.morphe.extensions.library)
    implementation(libs.ample.patcher)
    implementation(libs.ample.library)

    implementation(project(":api"))
    implementation(libs.kotlin.process)

    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.nio)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.navigation)
    implementation(libs.koin.workmanager)

    implementation(libs.about.libraries.core)
    implementation(libs.about.libraries.m3)

    implementation(libs.ktor.core)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization)

    implementation(libs.markdown.renderer)
    implementation(libs.fading.edges)
    implementation(libs.scrollbars)
    implementation(libs.enumutil)
    ksp(libs.enumutil.ksp)
    implementation(libs.reorderable)
    implementation(libs.compose.icons.fontawesome)
    implementation(libs.ackpine.core)
    implementation(libs.ackpine.ktx)
}

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath(libs.semver.parser) }
}

android {
    namespace = "app.revanced.manager"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "app.revanced.manager.flutter"
        minSdk { version = release(26) }
        targetSdk { version = release(36) }

        val versionStr = managerVersionName.get()
        versionName = versionStr
        versionCode = with(versionStr.toVersion()) {
            major * 100_000_000 + minor * 100_000 + patch * 100 +
                    (preRelease?.substringAfterLast('.')?.toIntOrNull() ?: 99)
        }
        vectorDrawables.useSupportLibrary = true

        val resDir = file("src/main/res")
        val locales = resDir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("values-[a-z]{2}(-r[A-Z]{2})?")) }
            .map { it.name.removePrefix("values-").replace("-r", "-") }
            .sorted()
            .joinToString(prefix = "{", separator = ",", postfix = "}") { "\"$it\"" }

        buildConfigField("String[]", "SUPPORTED_LOCALES", locales)
        val deepLinkScheme = "revanced-manager"
        manifestPlaceholders["deepLinkScheme"] = deepLinkScheme
        buildConfigField("String", "DEEP_LINK_SCHEME", "\"$deepLinkScheme\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    applicationVariants.all {
        outputs.all {
            this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            outputFileName = outputApkFileName
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    ksp { arg("room.schemaLocation", "$projectDir/schemas") }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    androidResources { generateLocaleConfig = true }
    lint { disable.add("ExtraTranslation") }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/XPP3_*_VERSION"
            excludes += "/font-awesome-license.txt"
            excludes += "/smali.properties"
            excludes += "/baksmali.properties"
            excludes += "/properties/apktool.properties"
            excludes += "/org/antlr/**"
            excludes += "/org/mockito/**"
            excludes += "/org/bouncycastle/pqc/**.properties"
            excludes += "/org/bouncycastle/x509/**.properties"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/**/*.txt"
            excludes += "/META-INF/**/*.properties"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/prebuilt/**/*"
        }
        jniLibs {
            excludes += "/lib/x86/*.so"
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) {
        it.packaging.resources.excludes.apply {
            add("/META-INF/*.version")
            add("/META-INF/*.kotlin_module")
            add("/kotlin-tooling-metadata.json")
            add("/DebugProbesKt.bin")
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll("-Xexplicit-backing-fields", "-Xcontext-parameters")
    }
}

configurations {
    all {
        exclude(group = "com.google.api.grpc", module = "proto-google-common-protos")
    }
}

aboutLibraries {
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.EXACT
    }
}
