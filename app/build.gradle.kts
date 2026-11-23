plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.scanapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.scanapp"
        minSdk = 24          // يشتغل على أندرويد 7 وأحدث
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // واجهة Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")

    // قارئ الباركود الجاهز من Google (ما يحتاج صلاحية كاميرا)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // سكنر المستندات من Google: قص تلقائي + عدة صفحات + PDF
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // لعرض صور الصفحات المصغرة
    implementation("io.coil-kt:coil-compose:2.7.0")
}
