package eloom.holybean

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.HiltAndroidApp
import eloom.holybean.config.RunMode
import javax.inject.Inject

@HiltAndroidApp
class AppClass : Application() {

    /** 로컬 에뮬레이터 / 실서버 디버그 / 프로덕션. 모드별 차이는 [RunMode] 구현에 있다. */
    @Inject lateinit var runMode: RunMode

    // FirebaseModule 이 runMode 설정(에뮬레이터 연결 등)을 적용해 제공하는 인스턴스.
    // Firebase.auth 를 직접 쓰면 설정 이전에 실서버로 요청이 나간다.
    @Inject lateinit var auth: FirebaseAuth

    override fun onCreate() {
        super.onCreate()   // Hilt 필드 주입이 여기서 일어난다
        Log.i(TAG, "실행 모드: $runMode")

        runMode.appCheckFactory?.let(Firebase.appCheck::installAppCheckProviderFactory)

        // 모든 모드 공통: Firestore 규칙이 로그인(request.auth)을 요구한다.
        signInAnonymouslyIfNeeded()
    }

    private fun signInAnonymouslyIfNeeded() {
        if (auth.currentUser != null) return
        auth.signInAnonymously()
            .addOnFailureListener { Log.w(TAG, "익명 로그인 실패", it) }
    }

    private companion object {
        const val TAG = "AppClass"
    }
}
