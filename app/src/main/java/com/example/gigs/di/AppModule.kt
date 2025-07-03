package com.example.gigs.di

import android.content.Context
import com.example.gigs.data.remote.FirebaseAuthManager
import com.example.gigs.data.remote.SupabaseClient
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.ProfileRepository
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.viewmodel.ProcessedJobsRepository
import com.example.gigs.data.repository.ReconsiderationStorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // === EXISTING DEPENDENCIES ===

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

    // === NEW/UPDATED DEPENDENCIES FOR RECONSIDERATION SYSTEM ===

    @Provides
    @Singleton
    fun provideJobRepository(
        supabaseClient: SupabaseClient,
        authRepository: AuthRepository,
        processedJobsRepository: ProcessedJobsRepository,
        applicationRepository: ApplicationRepository,
        reconsiderationStorage: ReconsiderationStorageManager
    ): JobRepository {
        return JobRepository(
            supabaseClient,
            authRepository,
            processedJobsRepository,
            applicationRepository,
            reconsiderationStorage
        )
    }

    @Provides
    @Singleton
    fun provideProcessedJobsRepository(
        reconsiderationStorageManager: ReconsiderationStorageManager
    ): ProcessedJobsRepository {
        return ProcessedJobsRepository(reconsiderationStorageManager)
    }

    @Provides
    @Singleton
    fun provideReconsiderationStorageManager(
        @ApplicationContext context: Context,
        authRepository: AuthRepository
    ): ReconsiderationStorageManager {
        return ReconsiderationStorageManager(context, authRepository)
    }
}