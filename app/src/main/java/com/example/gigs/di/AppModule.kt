package com.example.gigs.di

// Updated AppModule.kt

import android.content.Context
import com.example.gigs.data.remote.FirebaseAuthManager
import com.example.gigs.data.remote.SupabaseClient
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.ProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuthManager(): FirebaseAuthManager {
        return FirebaseAuthManager()
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(@ApplicationContext context: Context): SupabaseClient {
        return SupabaseClient(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseAuthManager: FirebaseAuthManager,
        supabaseClient: SupabaseClient
    ): AuthRepository {
        return AuthRepository(firebaseAuthManager, supabaseClient)
    }

    @Provides
    @Singleton
    fun provideProfileRepository(
        firebaseAuthManager: FirebaseAuthManager,
        supabaseClient: SupabaseClient
    ): ProfileRepository {
        return ProfileRepository(firebaseAuthManager, supabaseClient)
    }
}