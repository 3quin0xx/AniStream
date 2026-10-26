package com.portolan.anistream.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Official AniList GraphQL endpoint (never goes down)
    const val ANILIST_GRAPHQL_URL = "https://graphql.anilist.co"

    // Fallback Consumet-compatible mirrors for episode stream extraction
    val CONSUMET_MIRRORS = listOf(
        "https://consumet-api-clone.vercel.app/",
        "https://api-consumet-org-six.vercel.app/",
        "https://consumet-api.vercel.app/",
        "https://api.consumet.org/"
    )

    private const val ANISKIP_URL = "https://api.aniskip.com/"

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    fun createConsumetApi(baseUrl: String): ConsumetApi = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ConsumetApi::class.java)

    val consumetApi: ConsumetApi = createConsumetApi(CONSUMET_MIRRORS.first())

    val aniSkipApi: AniSkipApi = Retrofit.Builder()
        .baseUrl(ANISKIP_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AniSkipApi::class.java)
}
