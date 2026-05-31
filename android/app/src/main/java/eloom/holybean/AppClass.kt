package eloom.holybean

import android.app.Application
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import dagger.hilt.android.HiltAndroidApp
import eloom.holybean.config.FeatureFlags

@HiltAndroidApp
class AppClass : Application() {
    override fun onCreate() {
        super.onCreate()

        Firebase.appCheck.installAppCheckProviderFactory(
            if (FeatureFlags.useDebugAppCheck) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        if (Firebase.auth.currentUser == null) {
            Firebase.auth.signInAnonymously()
        }
    }
}
