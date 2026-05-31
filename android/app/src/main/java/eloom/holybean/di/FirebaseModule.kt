package eloom.holybean.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eloom.holybean.config.FeatureFlags
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance()
        // useEmulator는 인스턴스를 처음 쓰기 전(=settings 설정 전)에 호출해야 한다.
        if (FeatureFlags.useFirebaseEmulator) {
            db.useEmulator("10.0.2.2", 8080)
        }
        db.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
        return db
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        val auth = FirebaseAuth.getInstance()
        if (FeatureFlags.useFirebaseEmulator) {
            auth.useEmulator("10.0.2.2", 9099)
        }
        return auth
    }
}
