package com.ctech.eaty.di

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.cache.normalized.CacheKey
import com.apollographql.apollo.cache.normalized.CacheKeyResolver
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory
import com.ctech.eaty.BuildConfig
import com.ctech.eaty.network.AlgoliaApi
import com.ctech.eaty.network.ProductHuntApi
import com.ctech.eaty.network.SoundCloudApi
import com.ctech.eaty.network.adapter.ApolloTypedAdapterFactory
import com.ctech.eaty.network.cache.ApolloSql
import com.ctech.eaty.network.cache.SqlNormalizedCacheFactory
import com.ctech.eaty.repository.AccessTokenInterceptor
import com.ctech.eaty.repository.AppSettingsManager
import com.ctech.eaty.type.CustomType
import com.ctech.eaty.util.Constants
import com.ctech.eaty.util.Constants.GRAPHQL_URL
import com.ctech.eaty.util.DateTimeConverter
import com.ctech.eaty.util.glide.GlideImageLoader
import com.ctech.eaty.util.NetworkManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.joda.time.DateTime
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import javax.inject.Singleton


@Module
class NetworkModule {

    private val DATE_TIME_TYPE = object : TypeToken<DateTime>() {}.type

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
                .registerTypeAdapter(DATE_TIME_TYPE, DateTimeConverter())
                .create()
    }


    @Singleton
    @Provides
    fun provideOkHttpClient(appSettingsManager: AppSettingsManager): OkHttpClient {
        val builder = OkHttpClient.Builder()

        val accessTokenInterceptor = AccessTokenInterceptor(appSettingsManager)

        builder.addInterceptor(accessTokenInterceptor)


        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger { message ->
                Timber.tag("OkHttp").d(message)
            })

            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

            builder.addInterceptor(loggingInterceptor)
        }
        return builder
                .followRedirects(false)
                .build()
    }

    @Singleton
    @Provides
    fun provideProductHuntApi(okHttpClient: OkHttpClient, gson: Gson): ProductHuntApi {
        return createRestServices(gson, okHttpClient, Constants.API_URL, ProductHuntApi::class.java)
    }

    @Singleton
    @Provides
    fun provideSoundCloudApi(okHttpClient: OkHttpClient, gson: Gson): SoundCloudApi {
        return createRestServices(gson, okHttpClient, Constants.SOUND_CLOUD_API, SoundCloudApi::class.java)
    }

    @Singleton
    @Provides
    fun provideAngoliaApi(okHttpClient: OkHttpClient, gson: Gson): AlgoliaApi {
        return createRestServices(gson, okHttpClient, Constants.ALGOLIA_CLOUD_API, AlgoliaApi::class.java)
    }

    @Singleton
    @Provides
    fun provideImageLoader(context: Context): GlideImageLoader {
        return GlideImageLoader(context)
    }

    private fun <T> createRestServices(gson: Gson,
                                       okHttpClient: OkHttpClient,
                                       baseUrl: String,
                                       servicesClass: Class<T>): T {
        val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
        return retrofit.create(servicesClass)
    }

    @Singleton
    @Provides
    fun provideNetworkManager(context: Context): NetworkManager {
        return NetworkManager.IMPL(context)
    }

    @Provides
    fun provideApolloSql(context: Context): ApolloSql {
        return ApolloSql.create(context)
    }

    @Provides
    @Singleton
    fun provideApolloClient(okHttpClient: OkHttpClient, apolloSql: ApolloSql): ApolloClient {

        val sqlCacheFactory = SqlNormalizedCacheFactory(apolloSql)
        val memoryFirstThenDBCacheFactory = LruNormalizedCacheFactory(
                EvictionPolicy.builder().maxSizeBytes(16 * 1024).build()
        ).chain(sqlCacheFactory)

        val resolver = object : CacheKeyResolver() {
            override fun fromFieldRecordSet(field: ResponseField, recordSet: Map<String, Any>): CacheKey {
                return formatCacheKey(recordSet["id"] as? String)
            }

            override fun fromFieldArguments(field: ResponseField, variables: Operation.Variables): CacheKey {
                return formatCacheKey(field.resolveArgument("id", variables) as? String)
            }

            private fun formatCacheKey(id: String?): CacheKey {
                return if (id == null || id.isEmpty()) {
                    CacheKey.NO_KEY
                } else {
                    CacheKey.from(id)
                }
            }
        }
        return ApolloClient.builder()
                .serverUrl(GRAPHQL_URL)
                .normalizedCache(memoryFirstThenDBCacheFactory, resolver)
                .addCustomTypeAdapter(CustomType.JSON, ApolloTypedAdapterFactory.TYPED_JSON)
                .addCustomTypeAdapter(CustomType.DATETIME, ApolloTypedAdapterFactory.TYPED_DATE_TIME)
                .okHttpClient(okHttpClient)
                .build()
    }
}