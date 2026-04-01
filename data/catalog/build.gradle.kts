plugins {
    alias(libs.plugins.android.library)
}

val puzzleAssetsBaseUrl = providers.gradleProperty("puzzleAssetsBaseUrl")
    .orElse("https://storage.googleapis.com/puzzle_assets")

android {
    namespace = "com.puzzle.jigsaw.data.catalog"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        buildConfigField(
            "String",
            "PUZZLE_ASSETS_BASE_URL",
            "\"${puzzleAssetsBaseUrl.get().trimEnd('/')}\"",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit4)
}
