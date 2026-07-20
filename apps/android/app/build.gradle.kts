import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val apiBaseUrl = providers.environmentVariable("HHY_API_BASE_URL")
    .orElse("https://api.example.invalid")
val appChannel = providers.environmentVariable("HHY_APP_CHANNEL").orElse("official")
// The publicly reachable development API publishes its test artefacts in STAGING.
// Individual CI jobs can still override this with HHY_APP_ENVIRONMENT.
val appEnvironment = providers.environmentVariable("HHY_APP_ENVIRONMENT").orElse("STAGING")
val h5BaseUrl = providers.environmentVariable("HHY_H5_BASE_URL").orElse("https://h5.orbexa.cc")

val packagingTaskRequested = gradle.startParameter.taskNames.any { requested ->
    val name = requested.substringAfterLast(':')
    name == "verifyApiBaseUrl" || name == "build" ||
        name.startsWith("package") || name.startsWith("assemble") || name.startsWith("bundle")
}
if (packagingTaskRequested) {
    val value = apiBaseUrl.get()
    val uri = runCatching { URI(value) }.getOrNull()
    require(
        uri?.scheme == "https" &&
            !uri.host.isNullOrBlank() &&
            !uri.host.endsWith(".invalid") &&
            uri.userInfo == null &&
            uri.fragment == null
    ) {
        "APK packaging requires a safe HHY_API_BASE_URL; refusing placeholder or unsafe endpoint: $value"
    }
}

val verifyApiBaseUrl by tasks.registering {
    group = "verification"
    description = "Rejects APK packaging when HHY_API_BASE_URL is missing or unsafe."
}

tasks.configureEach {
    if ((name.startsWith("package") || name.startsWith("assemble") || name.startsWith("bundle")) &&
        (name.endsWith("Debug") || name.endsWith("Release"))
    ) {
        dependsOn(verifyApiBaseUrl)
    }
}

android {
    namespace = "cc.orbexa.hhy"
    compileSdk = 36

    defaultConfig {
        applicationId = "cc.orbexa.hhy"
        minSdk = 26
        targetSdk = 36
        versionCode = 10213
        versionName = "1.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.get()}\"")
        buildConfigField("String", "CONTRACT_VERSION", "\"1.2.2\"")
        buildConfigField("String", "APP_CHANNEL", "\"${appChannel.get()}\"")
        buildConfigField("String", "APP_ENVIRONMENT", "\"${appEnvironment.get()}\"")
        buildConfigField("String", "IDENTITY_RETURN_URL", "\"${h5BaseUrl.get().trimEnd('/')}/identity/callback\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "合伙云 Pro 测试")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":feature:shell"))
    implementation(project(":feature:startup"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:identity"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
