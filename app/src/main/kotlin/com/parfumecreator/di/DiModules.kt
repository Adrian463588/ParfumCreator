package com.parfumecreator.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.parfumecreator.data.local.dao.*
import com.parfumecreator.data.local.database.DatasetReadinessStore
import com.parfumecreator.data.local.database.ParfumeDatabase
import com.parfumecreator.data.repository.*
import com.parfumecreator.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher() = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher() = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideParfumeDatabase(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        readiness: DatasetReadinessStore
    ): ParfumeDatabase = ParfumeDatabase.buildDatabase(context, scope, readiness)

    @Provides
    fun provideIngredientDao(db: ParfumeDatabase): IngredientDao = db.ingredientDao()

    @Provides
    fun provideFormulaDao(db: ParfumeDatabase): FormulaDao = db.formulaDao()

    @Provides
    fun provideRagDao(db: ParfumeDatabase): RagDao = db.ragDao()

    @Provides
    fun provideChatDao(db: ParfumeDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideDownloadDao(db: ParfumeDatabase): DownloadDao = db.downloadDao()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "parfume_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore
}


@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindIngredientRepository(impl: IngredientRepositoryImpl): IngredientRepository

    @Binds
    @Singleton
    abstract fun bindFormulaRepository(impl: FormulaRepositoryImpl): FormulaRepository

    @Binds
    @Singleton
    abstract fun bindRagRepository(impl: RagRepositoryImpl): RagRepository

    @Binds
    @Singleton
    abstract fun bindInternetKnowledgeRepository(impl: InternetKnowledgeRepositoryImpl): InternetKnowledgeRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindLlmRepository(impl: LlmRepositoryImpl): LlmRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
