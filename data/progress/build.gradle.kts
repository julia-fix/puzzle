plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.puzzle.jigsaw.data.progress"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjspecify-annotations=strict")
    }
}

dependencies {
    api(project(":domain:jigsaw"))

    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit4)
}
