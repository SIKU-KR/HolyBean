package eloom.holybean.config

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * 앱이 붙는 Firebase 백엔드 실행 모드. 모드별로 달라지는 동작을 각 모드 객체가 직접 구현한다.
 * 호출부(AppClass, FirebaseModule)는 분기 없이 현재 모드에 위임만 한다.
 */
sealed interface RunMode {

    /** 설치할 App Check 공급자. null 이면 App Check 를 설치하지 않는다. */
    val appCheckFactory: AppCheckProviderFactory?

    /** Firestore 인스턴스를 처음 쓰기 전(=settings 설정 전)에 호출된다. */
    fun configure(db: FirebaseFirestore) {}

    /** FirebaseAuth 인스턴스를 처음 쓰기 전에 호출된다. */
    fun configure(auth: FirebaseAuth) {}

    /** 로컬 개발: Android 에뮬레이터에서 PC의 Firebase 에뮬레이터로 붙는다. App Check 미사용. */
    data object LocalEmulator : RunMode {
        private const val HOST = "10.0.2.2"   // Android 에뮬레이터에서 본 PC의 localhost
        private const val FIRESTORE_PORT = 8080
        private const val AUTH_PORT = 9099

        override val appCheckFactory: AppCheckProviderFactory? = null
        override fun configure(db: FirebaseFirestore) = db.useEmulator(HOST, FIRESTORE_PORT)
        override fun configure(auth: FirebaseAuth) = auth.useEmulator(HOST, AUTH_PORT)
    }

    /** 실서버 + Play 미경유 설치(App Distribution 등). logcat 의 debug secret 을 콘솔 App Check 에 등록해야 한다. */
    data object RemoteDebug : RunMode {
        override val appCheckFactory: AppCheckProviderFactory
            get() = DebugAppCheckProviderFactory.getInstance()
    }

    /** 실서버 + Play 스토어 배포. */
    data object Production : RunMode {
        override val appCheckFactory: AppCheckProviderFactory
            get() = PlayIntegrityAppCheckProviderFactory.getInstance()
    }

    companion object {
        /** 빌드 플래그 조합으로 모드를 정한다. 에뮬레이터 플래그가 우선한다. */
        fun fromFlags(): RunMode = when {
            FeatureFlags.useFirebaseEmulator -> LocalEmulator
            FeatureFlags.useDebugAppCheck -> RemoteDebug
            else -> Production
        }
    }
}
