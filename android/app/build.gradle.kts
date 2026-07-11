import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("kotlin-kapt")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.appdistribution")
}

// Feature flags: local.properties → -P gradle property → default.
// 빌드타입과 독립적으로 백엔드 구성을 토글한다.
//   useFirebaseEmulator=true  → Firestore/Auth 를 10.0.2.2 에뮬레이터로
val featureFlagProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun featureFlag(name: String, default: Boolean): Boolean =
    (featureFlagProps.getProperty(name) ?: (project.findProperty(name) as String?))?.toBoolean() ?: default

android {
    namespace = "eloom.holybean"
    compileSdk = 36

    defaultConfig {
        applicationId = "eloom.holybean"
        minSdk = 31
        // 버전은 versionName(MAJOR.MINOR) 단일 소스에서 파생한다. 릴리스 스킬은 appVersionName 한 줄만 갱신.
        val appVersionName = "3.2"
        versionName = appVersionName
        versionCode = appVersionName.split(".").let { (maj, min) -> maj.toInt() * 100 + min.toInt() }  // 3.0 → 300

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("boolean", "USE_FIREBASE_EMULATOR", featureFlag("useFirebaseEmulator", false).toString())
        // App Check: 기본 true = Debug provider(등록된 debug 토큰 사용, Play 미경유 가능).
        // 실제 Play 스토어 프로덕션 배포 시에만 useDebugAppCheck=false 로 PlayIntegrity 사용.
        buildConfigField("boolean", "USE_DEBUG_APPCHECK", featureFlag("useDebugAppCheck", true).toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            // Play 미경유 내부 배포용: debug 키로 서명해 별도 키스토어 없이 설치 가능.
            // (실제 Play 프로덕션 출시 시 전용 release signingConfig로 교체할 것)
            signingConfig = signingConfigs.getByName("debug")

            // Firebase App Distribution: CI(distribute.yml)에서 GOOGLE_APPLICATION_CREDENTIALS(서비스 계정 키)로 인증.
            // release-notes.txt 는 워크플로가 GitHub Release 본문으로 생성한다.
            firebaseAppDistribution {
                artifactType = "APK"
                testers = "bumshik0126@gmail.com"
                releaseNotesFile = rootProject.file("release-notes.txt").absolutePath
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Align the atomic androidx.concurrent group on 1.2.0: androidx.test:core:1.7.0
    // (via androidx.test.ext:junit:1.3.0) pulls concurrent-futures-ktx:1.2.0, which
    // strictly forces concurrent-futures:1.2.0, while profileinstaller pulls 1.1.0 at
    // runtime. Consistent resolution requires compile & runtime to match, so pin both.
    constraints {
        implementation("androidx.concurrent:concurrent-futures:1.2.0")
        androidTestImplementation("androidx.concurrent:concurrent-futures:1.2.0")
    }

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.4")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("sh.calvin.reorderable:reorderable:2.4.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.google.dagger:hilt-android:2.57.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.14.5")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    kapt("com.google.dagger:hilt-android-compiler:2.57.1")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}

kapt {
    correctErrorTypes = true

    javacOptions {
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED")
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
    }
}