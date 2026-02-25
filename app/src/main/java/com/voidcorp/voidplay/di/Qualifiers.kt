package com.voidcorp.voidplay.di

import javax.inject.Qualifier

/**
 * Qualifier for Deezer Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeezerRetrofit

/**
 * Qualifier for Fast OkHttpClient (Short timeouts).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FastOkHttpClient

/**
 * Qualifier for Gson instance configured for backup serialization.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackupGson

/**
 * Qualifier for Netease Cloud Music Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NeteaseRetrofit

