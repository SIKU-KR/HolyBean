package eloom.holybean.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eloom.holybean.data.repository.MenuDao
import eloom.holybean.data.repository.MenuDatabase // Ensure this is correctly imported
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMenuDatabase(@ApplicationContext context: Context): MenuDatabase {
        return Room.databaseBuilder(
            context,
            MenuDatabase::class.java,
            "database.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideMenuDao(database: MenuDatabase): MenuDao {
        return database.menuDao()
    }
}
