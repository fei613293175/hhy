plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "cc.orbexa.hhy.prop"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
