package com.portolan.anistream.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Public Consumet instance — change to your own if needed
    private const val CONSUMET_URL = "https://api.consumet.org/"
    private const val ANISKIP_URL  = "https://api.aniskip.com/"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val consumetApi: ConsumetApi = Retrofit.Builder()
        .baseUrl(CONSUMET_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ConsumetApi::class.java)

    val aniSkipApi: AniSkipApi = Retrofit.Builder()
        .baseUrl(ANISKIP_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AniSkipApi::class.java)
}
