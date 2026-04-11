package com.fantasyidler.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Shared [Json] instance used by repositories to serialize/deserialize the
     * JSON columns stored in Room entities.
     *
     * - [ignoreUnknownKeys]: tolerates forward-compatible additions to the JSON schema.
     * - [coerceInputValues]: null JSON values are coerced to Kotlin defaults rather
     *   than throwing, which prevents crashes when old DB rows miss new fields.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }
}
