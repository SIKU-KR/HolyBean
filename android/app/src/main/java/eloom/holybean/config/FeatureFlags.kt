package eloom.holybean.config

import eloom.holybean.BuildConfig

/**
 * 빌드타입과 독립적인 기능 토글. 값은 build.gradle.kts가 local.properties/-P에서
 * 읽어 BuildConfig로 주입한다(기본 false = real 백엔드/real 프린터).
 */
object FeatureFlags {
    /** Firestore/Auth를 로컬 Firebase 에뮬레이터(10.0.2.2)로 붙인다. */
    val useFirebaseEmulator: Boolean get() = BuildConfig.USE_FIREBASE_EMULATOR

    /** 실제 Pi 인쇄서버 대신 FakePrintServerApi(no-op)를 사용한다. */
    val useFakePrinter: Boolean get() = BuildConfig.USE_FAKE_PRINTER
}
