package eloom.holybean.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eloom.holybean.config.RunMode
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideRunMode(): RunMode = RunMode.fromFlags()

    @Provides
    @Singleton
    fun provideFirestore(runMode: RunMode): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance()
        runMode.configure(db)   // settings 설정 전에 호출해야 한다(에뮬레이터 연결 등)
        db.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
        return db
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(runMode: RunMode): FirebaseAuth =
        FirebaseAuth.getInstance().also(runMode::configure)
}
