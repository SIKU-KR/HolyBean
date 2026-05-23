package eloom.holybean.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eloom.holybean.diag.AndroidNetworkStatusProvider
import eloom.holybean.diag.NetworkStatusProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds @Singleton
    abstract fun bindNetworkStatusProvider(impl: AndroidNetworkStatusProvider): NetworkStatusProvider
}
